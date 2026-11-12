# 依赖来源与安全检查

2026-11-12 检查。应用依赖版本固定在 Gradle 文件及 `app/gradle.lockfile` 中，没有为本次公开发布新增或自动升级应用依赖。

来源限制：Google Maven、Maven Central、Gradle Plugin Portal。Gradle Wrapper 8.11.1 使用官方分发地址及 SHA-256 校验。

初次构建时使用 OSV 对 251 个组件版本进行了公告查询。104 个 Debug 运行依赖版本当时未命中查询结果；这不是“没有漏洞”的保证。本次又单独查询了 98 个 Release 运行依赖版本，未命中 OSV 返回的公告。

11 个构建和测试工具链组件版本命中了公告，分别涉及 Guava（两个版本）、Protobuf、Commons IO、Netty（六个模块）和 Kotlin Gradle 插件。相关组件位于 KSP、Android Unified Test Platform 或 Gradle 插件环境，不属于 App 的运行依赖。版本保持固定，**这些工具链公告未统一修复**。

Kotlin 插件的 GHSA-r937-wjx7-w2jp 对应 KAPT 缓存处理；本项目使用 KSP，没有应用或运行 KAPT。判断依据：[上游修复](https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6)。其他组件不因未打入 APK 就被视为已修复；构建环境仍应只使用可信源码和缓存。

Lint 的版本更新提示保留，不使用自动升级来消除。若更新依赖，需要重新进行来源和公告检查、构建、存储与算法回归以及真机验证。
