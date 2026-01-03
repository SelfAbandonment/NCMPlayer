package org.selfabandonment.ncmplayer.client.audio;

import javazoom.jl.decoder.*;
import org.lwjgl.openal.AL10;

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

        decodeWorker = new Thread(() -> decodeLoop(mp3Url), "ncm-mp3-decode");
        decodeWorker.setDaemon(true);
        decodeWorker.start();
    }

    /**
     * 暂停播放
     */
    public synchronized void pause() {
        if (state.get() == State.PLAYING) {
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
     * 停止播放
     */
    public synchronized void stop() {
        stopRequested.set(true);
        if (state.get() != State.ERROR) state.set(State.STOPPING);

        if (decodeWorker != null) {
            try {
                decodeWorker.join(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            decodeWorker = null;
        }
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

        if (decodeDead && nothingQueued && nothingIncoming) {
            cleanupAl();
            playbackStarted = false;
            prebuffered = 0;
            if (state.get() != State.ERROR) state.set(State.STOPPED);
        } else {
            if (!playbackStarted && state.get() != State.PAUSED) {
                state.set(State.BUFFERING);
            }
        }
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
        try {
            HttpRequest req = HttpRequest.newBuilder(mp3Url)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Minecraft NeoForge Mod)")
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " for " + mp3Url);
            }

            try (InputStream raw = resp.body();
                 BufferedInputStream in = new BufferedInputStream(raw, 64 * 1024)) {
                decodeMp3ToQueue(in);
            }
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            state.set(State.ERROR);
            stopRequested.set(true);
        }
    }

    private void decodeMp3ToQueue(InputStream mp3Stream) throws Exception {
        Bitstream bitstream = new Bitstream(mp3Stream);
        Decoder decoder = new Decoder();

        while (!stopRequested.get()) {
            PcmChunk chunk = readPcmChunk(bitstream, decoder, TARGET_CHUNK_MS);
            if (chunk == null) break;

            while (!stopRequested.get() && !pcmQueue.offer(chunk)) {
                Thread.sleep(10);
            }
        }

        try { bitstream.close(); } catch (Throwable ignored) {}
    }

    private PcmChunk readPcmChunk(Bitstream bitstream, Decoder decoder, int targetMs) throws Exception {
        ByteBuffer out = null;
        int sampleRate = -1;
        int channels = -1;
        int totalSamplesPerChannel = 0;

        while (!stopRequested.get()) {
            Header header = bitstream.readFrame();
            if (header == null) return null;

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
        return new PcmChunk(out, sampleRate, channels);
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

        if (queued > 0 && state.get() != State.PAUSED) {
            if (alState != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source);
            }
        }
    }

    private void cleanupAl() {
        try {
            if (source != 0) {
                AL10.alSourceStop(source);
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                while (queued-- > 0) {
                    AL10.alSourceUnqueueBuffers(source);
                }
                AL10.alDeleteSources(source);
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

        PcmChunk(ByteBuffer pcm, int sampleRate, int channels) {
            this.pcm = pcm;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }
}

