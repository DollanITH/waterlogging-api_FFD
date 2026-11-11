# 含水方块上游对照修复记录

本轮以 `Farmers-Future-Delight` 的 `FFDGameplayHooks`、`FFDHeightTransformer` 和 `FFDGameplayEvents` 为对照，核查了放置、流体深度、Forge 流体 quanta、粒子状态解码及空桶事件。

已确认当前实现包含：

- `BlockDynamicLiquid` 的含水宿主挡流与相邻含水源识别。
- `BlockLiquid` 对含水源的流向采样及共享水面隐藏处理。
- Forge `BlockFluidBase`/`BlockFluidClassic` 的含水源、quanta 与置换保护。
- `RenderGlobal.playEvent` 破坏粒子使用解码后的原方块干态，避免 `missingno`。
- `ItemBlock` 放置时仅在目标位置已有允许的流体源时写入含水状态。
- 空桶事件按上游 `FFDGameplayEvents.onFillBucket` 流程处理；本轮提高到最高事件优先级，减少与其他桶处理器的竞争。

验证：

- 使用 `GRADLE_USER_HOME=E:/mdk/waterlogged/.gradle` 执行 `gradlew.bat build --console=plain`。
- Gradle 构建成功，生成、重映射与资源处理均完成；项目无自动化测试源。
- 当前客户端未关闭，尚未把新 JAR 热加载到已运行实例，因此游戏内行为仍需在下次启动后复测。

## 崩溃修复

`crash-2026-11-11_21.38.00-server.txt` 显示 `WaterloggedFluidQuantaAccess` 位于 Mixin 包中，被 Mixin 禁止作为普通运行时类加载，最终表现为 `BlockFluidBase` 的 `NoClassDefFoundError`。已将该接口移到 `common.fluid` 普通包，并让 `BlockFluidBaseMixin` 与 `BlockFluidClassicMixin` 引用新位置。重新执行完整 `build` 已成功。
