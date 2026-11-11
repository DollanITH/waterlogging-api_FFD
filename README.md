# Waterlogged

独立的 Minecraft 1.12.2 水浸状态实验模组。构建基础是 Project Outlands 的
`mdk-java-mixin.zip`，版本 `mdk/2026.11.03-2`，使用 Unimined 和 CleanMix，
未使用 Farmer's Future Delight 原仓库的 Forge 构建模板。

## 来源与范围

水浸 API、流体状态存储、同步、渲染模型和流体更新逻辑改编自
`xy177/Farmers-Future-Delight` 的提交
`bd0b45045edbdbe07a2e98e93c564c47796381c9`。原项目的 MIT 许可见
`LICENSE_FFD`。本工程代码使用 MIT 许可，见 `LICENSE`。

当前包含：

- `IWaterloggableBlock` 和 `WaterloggedBlockApi`；
- 通用 `waterlogged` 状态属性与状态 ID 映射；
- 区块 NBT 中的包含流体状态、客户端区块同步和模型叠加；
- 方块替换时的流体保留、状态清理和更新调度。

原模组的农作物、世界生成、高度扩展和其他游戏内容未复制。原模组的
Fluidlogged API 旧世界迁移命令与迁移界面也未复制，旧世界数据不能依赖本模组自动迁移。

## 构建

需要 JDK 25。Windows 的 `gradlew.bat` 默认把 `GRADLE_USER_HOME` 设为
`E:/mdk/waterlogged/.gradle`，已设置的同名环境变量优先。Gradle wrapper 从腾讯镜像
下载 9.8.2；MCP 映射从 JSDU 镜像获取。

```powershell
.\gradlew.bat build --console=plain
```

输出是 `build/libs/waterlogged-0.1.0.jar`。

## 验证状态

`compileJava` 和 `build` 已通过。开发任务 `runServer` 在当前 MDK 配置下实际进入
客户端入口，并因缺少 `accessToken` 参数终止，因此尚未完成游戏内启动与方块行为验证。
请先在测试世界验证放置、破坏、桶交互、流动、光照、渲染、区块重新加载与多人同步，
再用于现有存档。
