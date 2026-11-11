# 含水树叶破坏后的液体更新

日期：2026-11-12

## 问题与原因

含水树叶被破坏后，原方块位置恢复为原版静止水源。静止水源没有由这条移除路径安排的流体更新，邻接水流和画面可能等到其他方块变化后才刷新。上游也存在相同的移除路径问题。

## 修改

- 在 `WaterloggedBlockApi.setBlockStateAfterRemoval` 中，把含水宿主释放出的原版静止水或熔岩转换为对应的流动液体源状态，保留液体等级。
- 服务端在移除宿主后安排下一刻流体更新并通知相邻方块；客户端刷新周围一个方块范围的渲染。
- 此处理覆盖玩家破坏及同一方法处理的其他移除路径。

## 验证

- 使用 `GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle` 执行 `gradlew.bat build -x test --console=plain`：`BUILD SUCCESSFUL`。
- `git diff --check` 通过。
- 尚未在重新加载该构建的客户端中验证树叶破坏后的水流与视觉更新。
