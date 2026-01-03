# 🎵 NCM Player

[![Build & Auto Release](https://github.com/SelfAbandonment/NCMPlayer/actions/workflows/build.yml/badge.svg)](https://github.com/SelfAbandonment/NCMPlayer/actions/workflows/build.yml)

**在 Minecraft 中畅听网易云音乐！**

NCM Player 是一个 NeoForge 模组，让你可以在 Minecraft 游戏中搜索和播放网易云音乐。

## ✨ 功能

- 🔐 **扫码登录** - 使用网易云音乐 App 扫码登录
- 🔍 **歌曲搜索** - 搜索你喜欢的歌曲、歌手或专辑
- 🎵 **流式播放** - 高品质音乐流式播放
- ⏯ **播放控制** - 暂停、继续、停止
- 🔊 **音量调节** - 可配置的默认音量
- 🌐 **中英双语** - 支持中文和英文界面

## 📦 安装

### 要求
- Minecraft 1.21.1
- NeoForge 21.1.x

### 步骤
1. 从 [Releases](../../releases) 下载最新的 `ncmplayer-x.x.x.jar`
2. 将 JAR 文件放入 `.minecraft/mods/` 文件夹
3. 启动游戏

## 🎮 使用方法

1. **打开播放器**: 按 **M 键** 打开音乐播放器界面
2. **登录**: 点击左上角「扫码登录」，用网易云音乐 App 扫码
3. **搜索**: 在搜索框输入歌曲名，点击搜索
4. **播放**: 点击歌曲列表中的歌曲即可播放

## ⚙️ 配置

配置文件位于 `config/ncmplayer-common.toml`:

```toml
[music]
# 网易云 API 服务器地址
apiUrl = "http://your-api-server:3000"
# 默认音量 (0.0 ~ 1.0)
defaultVolume = 0.8
# 搜索结果数量限制
searchLimit = 30
```

## 🔧 自建 API 服务器

本模组需要网易云音乐 API 服务器。推荐使用：
- [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi)

## 📝 更新日志

### v1.0.0
- 🎉 首次发布
- 扫码登录
- 歌曲搜索
- 音乐播放

## 📄 许可证

MIT License

## 👨‍💻 作者

**SelfAbandonment**

---

⭐ 如果觉得好用，请给个 Star！

