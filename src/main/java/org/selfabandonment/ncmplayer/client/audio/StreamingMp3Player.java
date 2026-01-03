package org.selfabandonment.ncmplayer.client.audio;

import javazoom.jl.decoder.*;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式 MP3 播放器
 *
 * 使用 JLayer 解码，OpenAL 播放
 * - 解码线程: HTTP 流 -> JLayer -> PCM -> pcmQueue
 * - 客户端 tick 线程: OpenAL 源/缓冲区队列管理
 *
 * @author SelfAbandonment
 */
public final class StreamingMp3Player implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("ncmplayer");

    public enum State { IDLE, BUFFERING, PLAYING, PAUSED, STOPPING, STOPPED, ERROR }

    private static final int NUM_AL_BUFFERS = 6;
    private static final int TARGET_CHUNK_MS = 150;
    private static final int PREBUFFER_COUNT = 3;
    private static final int PCM_QUEUE_CAPACITY = 24;

    private final HttpClient http;
    private final BlockingQueue<PcmChunk> pcmQueue = new ArrayBlockingQueue<>(PCM_QUEUE_CAPACITY);
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private volatile float volume = 1.0f;
    private Thread decodeWorker;
    private volatile URI currentUrl;
    private volatile String lastError = "";

    // 进度追踪
    private volatile long totalDecodedMs = 0;      // 已解码的总时长（毫秒）
    private volatile long playedMs = 0;            // 已播放的时长（毫秒）
    private volatile long estimatedDurationMs = 0; // 预估总时长（毫秒）
    private volatile long knownDurationMs = 0;     // 已知总时长（从 API 获取，毫秒）
    private long lastTickTime = 0;

    // Seek 支持
    private volatile long contentLength = 0;       // 文件总大小（字节）
    private volatile int bitRate = 0;              // 比特率（bps）
    private volatile boolean seekRequested = false;
    private volatile long seekTargetMs = 0;

    // OpenAL (tick 线程)
    private int source = 0;
    private int[] buffers = null;
    private final Deque<Integer> freeBuffers = new ArrayDeque<>();
    private final Deque<Integer> queuedBuffers = new ArrayDeque<>();
    private boolean playbackStarted = false;
    private int prebuffered = 0;

    public StreamingMp3Player() {
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public State getState() { return state.get(); }
    public String getLastError() { return lastError; }
    public float getVolume() { return volume; }

    /**
     * 获取当前播放位置（毫秒）
     */
    public long getPlayedMs() { return playedMs; }

    /**
     * 设置已知的总时长（从 API 获取）
     * 调用此方法后，进度条将使用精确时长
     */
    public void setKnownDuration(long durationMs) {
        this.knownDurationMs = durationMs;
        if (durationMs > 0) {
            this.estimatedDurationMs = durationMs;
        }
    }

    /**
     * 获取总时长（毫秒）
     * 优先返回已知时长，否则返回预估时长
     */
    public long getDurationMs() {
        if (knownDurationMs > 0) {
            return knownDurationMs;
        }
        return estimatedDurationMs;
    }

    /**
     * 获取预估总时长（毫秒）
     * 注意：流式播放时总时长是预估的，只有解码完成后才准确
     */
    public long getEstimatedDurationMs() { return estimatedDurationMs; }

    /**
     * 是否有已知的精确时长
     */
    public boolean hasKnownDuration() {
        return knownDurationMs > 0;
    }

    /**
     * 检查解码是否已完成
     */
    public boolean isDecodingComplete() {
        return (decodeWorker == null || !decodeWorker.isAlive()) && pcmQueue.isEmpty();
    }

    /**
     * 获取播放进度（0.0 ~ 1.0）
     */
    public float getProgress() {
        long duration = getDurationMs();
        if (duration <= 0) return 0f;
        return clamp((float) playedMs / duration, 0f, 1f);
    }

    /**
     * 检查是否正在播放
     */
    public boolean isPlaying() {
        State s = state.get();
        return s == State.PLAYING || s == State.BUFFERING;
    }

    public void setVolume(float v) {
        this.volume = clamp(v, 0f, 1f);
    }

    /**
     * 开始播放
     */
    public synchronized void play(URI mp3Url) {
        Objects.requireNonNull(mp3Url, "mp3Url");
        stop();

        currentUrl = mp3Url;
        lastError = "";
        state.set(State.BUFFERING);
        stopRequested.set(false);
        pcmQueue.clear();

        // 重置进度
        totalDecodedMs = 0;
        playedMs = 0;
        estimatedDurationMs = 0;
        knownDurationMs = 0;
        lastTickTime = System.currentTimeMillis();

        // 重置 seek 相关
        contentLength = 0;
        bitRate = 0;
        seekRequested = false;
        seekTargetMs = 0;

        decodeWorker = new Thread(() -> decodeLoop(mp3Url), "ncm-mp3-decode");
        decodeWorker.setDaemon(true);
        decodeWorker.start();
    }

    /**
     * 暂停播放
     */
    public synchronized void pause() {
        State s = state.get();
        if (s == State.PLAYING || s == State.BUFFERING) {
            state.set(State.PAUSED);
        }
    }

    /**
     * 继续播放
     */
    public synchronized void resume() {
        if (state.get() == State.PAUSED) {
            state.set(State.PLAYING);
        }
    }

    /**
     * 跳转到指定位置
     * @param targetMs 目标位置（毫秒）
     */
    public synchronized void seek(long targetMs) {
        if (currentUrl == null) return;

        long duration = getDurationMs();
        if (duration <= 0) return;

        // 限制范围
        targetMs = Math.max(0, Math.min(targetMs, duration));

        // 如果没有足够信息进行跳转，只更新显示时间
        if (contentLength <= 0 || bitRate <= 0) {
            // 无法精确跳转，但可以尝试基于已知时长估算
            if (knownDurationMs > 0 && contentLength > 0) {
                // 使用时长和文件大小估算比特率
                bitRate = (int) ((contentLength * 8 * 1000) / knownDurationMs);
            } else {
                // 无法跳转
                return;
            }
        }

        seekTargetMs = targetMs;
        seekRequested = true;

        // 停止当前播放
        stopRequested.set(true);

        // 清空队列
        pcmQueue.clear();

        // 等待解码线程停止
        if (decodeWorker != null && decodeWorker.isAlive()) {
            try {
                decodeWorker.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // 重新开始播放，从目标位置
        stopRequested.set(false);
        state.set(State.BUFFERING);
        playedMs = targetMs;
        lastTickTime = System.currentTimeMillis();

        // 计算字节偏移
        long byteOffset = (targetMs * bitRate) / (8 * 1000);
        byteOffset = Math.max(0, Math.min(byteOffset, contentLength - 1));

        final long finalByteOffset = byteOffset;
        decodeWorker = new Thread(() -> decodeLoopWithOffset(currentUrl, finalByteOffset), "ncm-mp3-decode-seek");
        decodeWorker.setDaemon(true);
        decodeWorker.start();

        seekRequested = false;
    }

    /**
     * 跳转到指定进度
     * @param progress 进度 (0.0 ~ 1.0)
     */
    public void seekToProgress(float progress) {
        long duration = getDurationMs();
        if (duration <= 0) return;

        progress = Math.max(0f, Math.min(1f, progress));
        long targetMs = (long) (duration * progress);
        seek(targetMs);
    }

    /**
     * 是否支持跳转
     */
    public boolean canSeek() {
        return currentUrl != null && getDurationMs() > 0 && (contentLength > 0 || knownDurationMs > 0);
    }

    /**
     * 停止播放
     */
    public synchronized void stop() {
        stopRequested.set(true);
        if (state.get() != State.ERROR) state.set(State.STOPPING);

        // 先清理 OpenAL 资源，防止残留音频
        if (source != 0) {
            cleanupAl();
        }

        // 清空 PCM 队列
        pcmQueue.clear();

        if (decodeWorker != null) {
            try {
                decodeWorker.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            decodeWorker = null;
        }

        // 重置状态
        playbackStarted = false;
        prebuffered = 0;
    }

    /**
     * 每帧调用（客户端 tick）
     */
    public void tick() {
        if ((state.get() == State.BUFFERING || state.get() == State.PLAYING ||
             state.get() == State.PAUSED || state.get() == State.STOPPING) && source == 0) {
            tryInitAl();
        }

        if (source == 0) {
            return;
        }

        AL10.alSourcef(source, AL10.AL_GAIN, volume);

        if (stopRequested.get() || state.get() == State.STOPPING) {
            cleanupAl();
            pcmQueue.clear();
            playbackStarted = false;
            prebuffered = 0;
            if (state.get() != State.ERROR) state.set(State.STOPPED);
            return;
        }

        if (state.get() == State.PAUSED) {
            AL10.alSourcePause(source);
            reclaimProcessedBuffers();
            return;
        } else {
            int alState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (playbackStarted && alState != AL10.AL_PLAYING &&
                AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) > 0) {
                AL10.alSourcePlay(source);
            }

            // 更新播放进度
            if (playbackStarted && alState == AL10.AL_PLAYING) {
                long now = System.currentTimeMillis();
                long delta = now - lastTickTime;
                if (delta > 0 && delta < 1000) {
                    playedMs += delta;
                    // 限制不超过已知时长
                    long duration = getDurationMs();
                    if (duration > 0 && playedMs > duration) {
                        playedMs = duration;
                    }
                }
                lastTickTime = now;
            } else {
                lastTickTime = System.currentTimeMillis();
            }
        }

        reclaimProcessedBuffers();

        int safety = NUM_AL_BUFFERS;
        while (safety-- > 0 && !freeBuffers.isEmpty()) {
            PcmChunk chunk = pcmQueue.poll();
            if (chunk == null) break;

            int buf = freeBuffers.removeFirst();
            int alFormat = toAlFormat(chunk.channels);

            AL10.alBufferData(buf, alFormat, chunk.pcm, chunk.sampleRate);
            AL10.alSourceQueueBuffers(source, buf);

            queuedBuffers.addLast(buf);
            prebuffered++;

            if (!playbackStarted && prebuffered >= PREBUFFER_COUNT) {
                AL10.alSourcePlay(source);
                playbackStarted = true;
                state.set(State.PLAYING);
            }
        }

        boolean decodeDead = decodeWorker == null || !decodeWorker.isAlive();
        boolean nothingQueued = (AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) == 0);
        boolean nothingIncoming = pcmQueue.isEmpty();

        // 检查播放结束条件
        if (decodeDead && playbackStarted) {
            int alState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            int buffersQueued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            int buffersProcessed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);

            // OpenAL 已停止（播放完所有缓冲区）
            if (alState == AL10.AL_STOPPED) {
                LOGGER.info("Playback finished: OpenAL stopped");
                finishPlayback();
                return;
            }

            // 解码完成，没有更多数据，且所有缓冲区都已处理
            if (nothingIncoming && buffersQueued == buffersProcessed) {
                LOGGER.info("Playback finished: all buffers processed");
                finishPlayback();
                return;
            }

            // 基于时间检测：播放时间已达到或超过已知时长
            long duration = getDurationMs();
            if (duration > 0 && playedMs >= duration && nothingIncoming) {
                LOGGER.info("Playback finished: reached duration {}ms", duration);
                finishPlayback();
                return;
            }
        }

        if (decodeDead && nothingQueued && nothingIncoming) {
            LOGGER.info("Playback finished: decode dead, nothing queued, nothing incoming");
            finishPlayback();
        } else {
            if (!playbackStarted && state.get() != State.PAUSED) {
                state.set(State.BUFFERING);
            }
        }
    }

    private void finishPlayback() {
        playedMs = getDurationMs();
        cleanupAl();
        playbackStarted = false;
        prebuffered = 0;
        if (state.get() != State.ERROR) state.set(State.STOPPED);
    }

    private String alStateToString(int alState) {
        return switch (alState) {
            case AL10.AL_INITIAL -> "INITIAL";
            case AL10.AL_PLAYING -> "PLAYING";
            case AL10.AL_PAUSED -> "PAUSED";
            case AL10.AL_STOPPED -> "STOPPED";
            default -> "UNKNOWN(" + alState + ")";
        };
    }

    @Override
    public void close() {
        stopRequested.set(true);
        stop();
        if (source != 0) {
            try { cleanupAl(); } catch (Throwable ignored) {}
        }
    }

    private void decodeLoop(URI mp3Url) {
        decodeLoopWithOffset(mp3Url, 0);
    }

    private void decodeLoopWithOffset(URI mp3Url, long byteOffset) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(mp3Url)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Minecraft NeoForge Mod)")
                    .GET();

            // 如果有偏移，添加 Range 头
            if (byteOffset > 0) {
                reqBuilder.header("Range", "bytes=" + byteOffset + "-");
            }

            HttpResponse<InputStream> resp = http.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();

            // 200 OK 或 206 Partial Content 都是成功
            if (code != 200 && code != 206) {
                throw new IOException("HTTP " + code + " for " + mp3Url);
            }

            // 获取文件大小
            resp.headers().firstValueAsLong("Content-Length").ifPresent(len -> {
                if (byteOffset == 0) {
                    contentLength = len;
                } else {
                    // 206 响应的 Content-Length 是剩余部分的大小
                    contentLength = byteOffset + len;
                }
            });

            // 尝试从 Content-Range 获取总大小
            resp.headers().firstValue("Content-Range").ifPresent(range -> {
                // 格式: bytes 0-1234/5678 或 bytes 1000-5677/5678
                int slashIdx = range.lastIndexOf('/');
                if (slashIdx > 0) {
                    try {
                        long total = Long.parseLong(range.substring(slashIdx + 1));
                        if (total > 0) {
                            contentLength = total;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            });

            try (InputStream raw = resp.body();
                 BufferedInputStream in = new BufferedInputStream(raw, 64 * 1024)) {
                decodeMp3ToQueue(in, byteOffset > 0);
            }
        } catch (Exception e) {
            if (!stopRequested.get()) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                state.set(State.ERROR);
            }
            stopRequested.set(true);
        }
    }

    private void decodeMp3ToQueue(InputStream mp3Stream) throws Exception {
        decodeMp3ToQueue(mp3Stream, false);
    }

    private void decodeMp3ToQueue(InputStream mp3Stream, boolean isSeeking) throws Exception {
        Bitstream bitstream = new Bitstream(mp3Stream);
        Decoder decoder = new Decoder();
        boolean firstFrame = true;

        while (!stopRequested.get()) {
            PcmChunk chunk = readPcmChunk(bitstream, decoder, TARGET_CHUNK_MS);
            if (chunk == null) break;

            // 从第一帧获取比特率
            if (firstFrame && chunk.bitRate > 0) {
                if (bitRate == 0) {
                    bitRate = chunk.bitRate;
                }
                firstFrame = false;
            }

            // 如果不是 seek 操作，累加已解码时长
            if (!isSeeking) {
                totalDecodedMs += chunk.durationMs;

                // 更新预估总时长
                if (totalDecodedMs > estimatedDurationMs) {
                    estimatedDurationMs = totalDecodedMs;
                }
            }

            while (!stopRequested.get() && !pcmQueue.offer(chunk)) {
                Thread.sleep(10);
            }
        }

        // 解码完成
        if (!isSeeking) {
            estimatedDurationMs = totalDecodedMs;
        }

        try { bitstream.close(); } catch (Throwable ignored) {}
    }

    private PcmChunk readPcmChunk(Bitstream bitstream, Decoder decoder, int targetMs) throws Exception {
        ByteBuffer out = null;
        int sampleRate = -1;
        int channels = -1;
        int totalSamplesPerChannel = 0;
        int frameBitRate = 0;

        while (!stopRequested.get()) {
            Header header = bitstream.readFrame();
            if (header == null) return null;

            // 获取比特率（从第一帧）
            if (frameBitRate == 0) {
                frameBitRate = header.bitrate();
            }

            SampleBuffer sb;
            try {
                sb = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            } finally {
                bitstream.closeFrame();
            }

            if (sampleRate < 0) {
                sampleRate = sb.getSampleFrequency();
                channels = sb.getChannelCount();
                out = ByteBuffer.allocateDirect(1024 * 64).order(ByteOrder.LITTLE_ENDIAN);
            }

            short[] pcm = sb.getBuffer();
            int len = sb.getBufferLength();
            int bytesNeeded = len * 2;

            if (out.remaining() < bytesNeeded) {
                int newCap = Math.max(out.capacity() * 2, out.capacity() + bytesNeeded);
                ByteBuffer grown = ByteBuffer.allocateDirect(newCap).order(ByteOrder.LITTLE_ENDIAN);
                out.flip();
                grown.put(out);
                out = grown;
            }

            for (int i = 0; i < len; i++) {
                out.putShort(pcm[i]);
            }

            totalSamplesPerChannel += (len / channels);
            double ms = (totalSamplesPerChannel * 1000.0) / sampleRate;
            if (ms >= targetMs) break;
        }

        if (out == null) return null;
        out.flip();

        long chunkDurationMs = (long) ((totalSamplesPerChannel * 1000.0) / sampleRate);
        return new PcmChunk(out, sampleRate, channels, chunkDurationMs, frameBitRate);
    }

    private void tryInitAl() {
        try {
            cleanupAl();
            source = AL10.alGenSources();

            buffers = new int[NUM_AL_BUFFERS];
            freeBuffers.clear();
            queuedBuffers.clear();

            for (int i = 0; i < NUM_AL_BUFFERS; i++) {
                buffers[i] = AL10.alGenBuffers();
                freeBuffers.addLast(buffers[i]);
            }

            AL10.alSourcef(source, AL10.AL_GAIN, volume);
            playbackStarted = false;
            prebuffered = 0;
        } catch (Throwable t) {
            lastError = "OpenAL init failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            state.set(State.ERROR);
            stopRequested.set(true);
            cleanupAl();
        }
    }

    private void reclaimProcessedBuffers() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int unqueued = AL10.alSourceUnqueueBuffers(source);
            queuedBuffers.pollFirst();
            freeBuffers.addLast(unqueued);
        }

        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        int alState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);

        // 只有在解码线程还活着并且有新数据要播放时才重新播放
        // 如果解码已结束，不要重新播放，让播放器自然停止
        boolean decodingActive = decodeWorker != null && decodeWorker.isAlive();
        boolean hasMoreData = !pcmQueue.isEmpty();

        if (queued > 0 && state.get() != State.PAUSED && (decodingActive || hasMoreData)) {
            if (alState != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source);
            }
        }
    }

    private void cleanupAl() {
        try {
            if (source != 0) {
                // 先停止源
                AL10.alSourceStop(source);

                // 等待一小段时间让 OpenAL 处理
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}

                // 先回收所有已处理的缓冲区
                int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                for (int i = 0; i < processed; i++) {
                    try {
                        AL10.alSourceUnqueueBuffers(source);
                    } catch (Throwable ignored) {}
                }

                // 再尝试回收剩余的队列缓冲区
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                for (int i = 0; i < queued; i++) {
                    try {
                        AL10.alSourceUnqueueBuffers(source);
                    } catch (Throwable ignored) {}
                }

                // 删除源
                try {
                    AL10.alDeleteSources(source);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        } finally {
            source = 0;
        }

        try {
            if (buffers != null) {
                for (int b : buffers) {
                    try { AL10.alDeleteBuffers(b); } catch (Throwable ignored) {}
                }
            }
        } finally {
            buffers = null;
            freeBuffers.clear();
            queuedBuffers.clear();
        }
    }

    private int toAlFormat(int channels) {
        return switch (channels) {
            case 1 -> AL10.AL_FORMAT_MONO16;
            case 2 -> AL10.AL_FORMAT_STEREO16;
            default -> throw new IllegalArgumentException("Unsupported channels: " + channels);
        };
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static final class PcmChunk {
        final ByteBuffer pcm;
        final int sampleRate;
        final int channels;
        final long durationMs;  // 此 chunk 的时长
        final int bitRate;      // 比特率 (bps)

        PcmChunk(ByteBuffer pcm, int sampleRate, int channels, long durationMs, int bitRate) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.durationMs = durationMs;
            this.bitRate = bitRate;
        }
    }
}

