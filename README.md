# Hentai1

一个 `hentai-one` 网站的第三方 Android 客户端，基于现代 Android 开发实践构建。

## ✨ 功能特性

*   **数据来源**: 解析 `hentai-one` 网站内容。
*   **流畅的阅读体验**: 顺滑的翻页与缩放。
*   **高级搜索**: 快速找到您钟爱的作品。
*   **本地收藏**: 保存您最喜欢的作品以便离线访问。
*   **纯净界面**: 无广告的沉浸式体验。

## 📝 TODO

*   [ ] 完善本地收藏功能。
*   [ ] 实现离线下载功能。
*   [ ] 优化搜索功能，增加筛选器。
*   [ ] 增加阅读器设置（如阅读方向切换）。

## 🏛️ 应用架构

本项目遵循 **整洁架构 (Clean Architecture)** 原则，将代码库分离为三个主要层次：

*   `domain`: 包含核心业务逻辑、用例和实体。它是应用的内核，独立于任何框架。
*   `data`: 管理数据来源，例如远程 API (使用 OkHttp 和 Jsoup) 和本地数据库 (使用 Room)。它实现了在 Domain 层定义的仓库接口。
*   `ui`: 表示层，完全由 **Jetpack Compose** 构建。它与用户进行交互，并遵循 MVVM 模式展示由 Domain 层提供的数据。

## 🛠️ 技术栈

*   **开发语言**: 100% [Kotlin](https://kotlinlang.org/)
*   **UI 框架**: [Jetpack Compose](https://developer.android.com/jetpack/compose) 用于构建声明式现代 UI。
*   **异步处理**: [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://developer.android.com/kotlin/flow) 用于管理后台线程。
*   **软件架构**: 整洁架构 (Clean Architecture) + MVVM
*   **导航**: [Navigation-Compose](https://developer.android.com/jetpack/compose/navigation)
*   **网络请求**: [OkHttp](https://square.github.io/okhttp/) 用于 HTTP 请求, [Jsoup](https://jsoup.org/) 用于 HTML 解析。
*   **数据库**: [Room](https://developer.android.com/training/data-storage/room) 用于本地数据持久化。
*   **图片加载**: [Coil](https://coil-kt.github.io/coil/) 用于在 Compose 中优化图片加载。
*   **JSON 解析**: [Gson](https://github.com/google/gson)

## 🚀 构建与安装

1.  **克隆项目**:
    ```bash
    git clone https://github.com/Hfugghg/Hentai1.git
    ```
2.  **在 Android Studio 中打开**:
    使用 `Android Studio` 打开项目 (推荐使用最新稳定版)。
3.  **构建项目**:
    等待 Gradle 同步完成后，构建项目 (`Build` -> `Make Project`)。
4.  **安装应用**:
    在模拟器或物理设备上运行应用。

## 🤝 参与贡献

欢迎任何形式的贡献！请随时提交 `Pull Request` 或开启 `Issue` 来讨论您想要进行的更改。

## 📧 联系方式

如果您有任何问题或建议，请在仓库中开启一个 `Issue`。

## 📄 许可证

本项目基于 GNU 许可证。详情请参阅 `LICENSE` 文件。
