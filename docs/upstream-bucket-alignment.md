# 桶交互与上游对齐记录

2026-11-11 对照 `xy177/Farmers-Future-Delight` 提交 `bd0b45045edbdbe07a2e98e93c564c47796381c9` 中的 `FFDGameplayEvents.java`。

- `WaterloggedBucketHandler` 的空桶收水走上游 `FillBucketEvent` 流程；移除了独立添加的 `RightClickBlock` 空桶处理及收水网络包。
- 放水沿用上游的 `onWaterBucketUse`、`tryExtraFluidBucket`、容器排水与消耗逻辑，并恢复上游的 `withWaterlogged` 状态过滤。
- 收水写入干态时保留本移植工程的 `setBlockStateWithoutWaterloggingHook`，避免本工程的 `WorldMixin` 重入；这是独立模组所需的适配。
- 指定 `GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle` 运行 `gradlew.bat compileJava --console=plain`，结果为 `BUILD SUCCESSFUL`。

编译仅证明代码可以构建；尚未在移植客户端验证空桶、水桶与水流的完整交互。
