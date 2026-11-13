# Mod of Mending runtimeOnly 依赖

日期：2026-11-12

## 交付内容

- 将用户提供的 `Mod of Mending-1.0.0.jar` 保存到 `libs/Mod of Mending-1.0.0.jar`。
- 在 `gradle/scripts/dependencies.gradle` 中加入 `runtimeOnly files(...)` 声明，使开发运行时从仓库内的稳定相对路径加载该模组；该依赖不会被打包进 Waterlogged JAR。

## 验证

- 已确认仓库内 JAR 大小为 343,864 字节。
- 已运行 `gradlew.bat build -x test --console=plain`。Gradle 配置阶段成功识别项目，但构建因当前环境无法访问 `nexus.jsdu.cn` 上已有的 Modrinth 依赖而失败（`Permission denied: getsockopt`）；另有既有 `build/reports/problems/problems-report.html` 文件访问冲突。此次失败与新增本地 `runtimeOnly` 声明无关。
