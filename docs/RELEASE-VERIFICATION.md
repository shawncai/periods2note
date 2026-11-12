# v1.1.0 发布验收

日期：2026-11-12。首次公开发布只提交源码、构建文件、说明和虚构数据截图，不包含个人 JSON、数据库、旧 APK、旧截图或本地签名文件。

| 检查 | 结果 |
| --- | --- |
| 算法单元测试 | 8 项通过 |
| Android 真机测试 | 17 项通过，Samsung SM-N9760 / Android 12 |
| Debug / Release Lint | 0 错误；保留 SDK 和依赖版本更新提示 |
| Debug / QA / Release 构建 | 通过 |
| 仅 Git 待提交文件的独立构建 | 无 JSON、无 local.properties 的临时源码目录，单元测试与签名 Release 构建通过 |
| APK 签名 | APK Signature Scheme v2 校验通过，RSA 3072 |
| 公开数据边界 | 不跟踪 JSON/数据库/私钥；APK 无种子记录和测试数据 |
| 公开版初始化 | 独立数据库验证首次启动为空、导入后重启保留 |
| 界面截图 | Android 真机测试使用 SyntheticData 生成，已目视检查 |
| 专项审查 | 安全与结构审查完成，无剩余发布阻断项 |

测试包含样本不足、短长周期、权重变化、修改重算、P80、持续时间、跨年闰年、滚动回测的历史边界，以及幂等初始化、导入冲突、过期编辑、失败回滚、并发快捷操作、导出恢复和页面流程。写入均在独立 QA 数据库完成。

发布签名 SHA-256：

```text
b9c0139641fbf765c94969821defbb3a858f396e14ae9ef155b8b9697241449a
```

验收通过 `testDebugUnitTest`、`lintDebug`、`lintRelease`、`assembleDebug`、`assembleQa`、`assembleQaAndroidTest` 和 `assembleRelease`。真机执行 AndroidJUnitRunner，返回 `OK (17 tests)`。QA 使用同一业务源码和独立包名；签名 Release 包完成文件内容、Manifest 和签名检查，没有覆盖手机原有 Debug 应用，也没有移除其记录。

公开包最低 Android 8.0。已验证的真机仅上述 Android 12 设备，不能将这些结果视为全部机型覆盖。算法为个人日程参考，经验范围不代表临床准确率。

依赖审查仍有未修复的构建/测试工具链公告，具体范围见 [DEPENDENCIES.md](DEPENDENCIES.md)。

复核公开文件和 APK 边界：

```sh
python3 scripts/verify-public.py /path/to/periods2note-1.1.0-release.apk
```

脚本检查路径和 APK 数据边界；截图、示例和源代码内容仍需人工审查。

## 发布后核对

源码已推送到 `main`，发布代码标签为 `v1.1.0`。远端 README 和三张截图与本地文件一致，源码树不含 JSON 或本地交付目录。已从 [GitHub Release](https://github.com/shawncai/periods2note/releases/tag/v1.1.0) 重新下载 APK 与校验文件，SHA-256 一致：

```text
7f2a6840d03b53b857bfaa85cf646368483f70669806133005252597f7035479
```

任务测试包、ADB 服务和临时源码/下载目录均已清理；原有手机应用保留。发布签名私钥只保存在维护者本机，不随仓库或 Release 分发。
