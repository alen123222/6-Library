# 📚 6的Library
百分之九十五的代码来自ai，此处鸣谢clauld gpt gemini（手动狗头）。本人只做了简单修改和调试以及鞭打ai等工作。此README的百分之八十同样出自ai之手。
注意，本app是一个纯本地资源的阅读工具，不具备任何联网功能，目前也没有书源/漫画源/音源接口。也许以后会做也许不会。附几张运行截图：
![pintu-fulicat com-1769565267961](https://github.com/user-attachments/assets/be5bc410-fa68-4013-b98d-c4a71f3a3e88)
也做了非常简单的横屏布局优化：
![pintu-fulicat com-1769565671956](https://github.com/user-attachments/assets/65f2f933-a65c-4e6b-8f57-1c9ab0c99a92)
另外，名字不是说这个ap很6，而是我的猫叫66.
推荐的文件存放逻辑：
📂 资源文件夹 
 │
 ├─ 📂 漫画 (Comics)
 │   ├─ 📂/📦 [漫画1]        <-- 结构A: 多章节 (内含分卷文件夹)
 │   │   ├─ 📂  第1卷
 │   │   └─ 📂  第2卷
 │   │
 │   └─ 📂/📦 [漫画2]      <-- 结构B: 单章节 (内含图片)
 │       ├─ 📄 01.jpg
 │       └─ 📄 02.png
 │
 ├─ 📂 小说 (Novels)
 │   ├─ 📄 小说1.txt       (需手动设置封面)
 │   └─ 📄 小说2.epub      (自动读取封面)
 │
 └─ 📂 歌曲 (Music)
     ├─ 🎵 歌曲1.mp3     (单曲直接存放)
     └─ 📂 [周杰伦专辑]    (专辑文件夹)
         ├─ 🎵 歌曲文件.mp3
         ├─ 🖼️ cover.jpg
         └─ 📄 歌词.lrc
选定漫画/小说/歌曲为扫描的目标文件夹。

> **一个现代化、高颜值的 Android 漫画/小说/音频阅读器，支持安卓5-15，不过过低版本比如7以下可能有不可预测的报错**
> 基于 Jetpack Compose 构建，打造极致流畅的沉浸式阅读体验

## ✨ 核心特性 | Features

### 📖 极致阅读体验 (Immersive Reader)
专为长时间阅读打造，提供高度可定制的阅读环境：
- **多模式护眼**：内置「羊皮纸」、「夜间模式」、「纯白」、「灰度」及等多种主题。
- **排版定制**：自由调节字号、行高 (1.6x)、段间距及水平边距。
- **自定义背景**：支持设置个性化背景图片，并调节遮罩透明度，确保文字清晰可读。
- **智能排版**：支持自定义字体（宋体、黑体、等宽），满足不同阅读偏好。

### 🎵 沉浸式音频播放 (Smart Audio Provider)
不仅仅是阅读，更是一种享受：
- **同步歌词**：支持滚动歌词显示，不错过每一句精彩。
- **悬浮歌词**：全局悬浮窗歌词，多任务处理时也能随心听。
- **炫彩视觉**：
    - **MeteorSlider**: 独创的流星进度条，带给你色彩流动的视觉盛宴。
    - **动态配色**：歌词颜色支持自定义（天蓝、荧光绿、淡紫等），随心而变。

### 🛠️ 强大的技术栈 (Tech Stack)
本项目完全采用现代 Android 开发技术构建：

| 库/工具 | 用途 |
| :--- | :--- |
| **Jetpack Compose** | 100% 声明式 UI，采用 Material 3 设计规范 |
| **Coil** | 高性能图片加载库 |
| **Media3 / ExoPlayer** | 稳定强大的音频播放核心 |
| **Biometric** | 生物识别支持，保护你的隐私 |
| **Junrar** | 原生支持 RAR 压缩包直读 |
| **JUniversalChardet** | 智能文本编码检测，告别乱码 |

## 📸 预览 | Screenshots

| 阅读器 (Reader) | 播放器 (Player) |
| :---: | :---: |
| *(在此处添加阅读器截图)* | *(在此处添加播放器截图)* |
| **设置 (Settings)** | **其他 (Others)** |
| *(在此处添加设置页截图)* | *(在此处添加其他截图)* |

## 🚀 快速开始 | Getting Started

### 环境要求
- Android Studio Koala 或更高版本
- JDK 17
- Android SDK API 35 (Compile SDK)

### 编译运行
1. 克隆仓库：
   ```bash
   git clone https://github.com/alen123222/6-Library.git
   ```
2. 打开 Android Studio 并导入项目。
3. 等待 Gradle Sync 完成。
4. 连接设备或模拟器，点击 Run (`Shift + F10`)。

## 🤝 贡献 | Contribution

欢迎提交 Issue 和 Pull Request！如果你有好的想法，请随时分享。

## 📄 许可证 | License


