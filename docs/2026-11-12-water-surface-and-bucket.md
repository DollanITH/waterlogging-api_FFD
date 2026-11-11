# 含水方块水面与空桶修复记录

日期：2026-11-12

## 原因

- `WaterloggedPlantBakedModel.createWaterQuads` 原先把含水方块顶面的正反两面固定绑定到 `stillFluid`。普通水流的 `BlockFluidRenderer` 则按水平流向选用 `flowingFluid` 并旋转 UV，因此含水源头与相邻水流出现纹理断开。上游同一模型方法也使用固定的静止水顶面。
- 空桶的方块点击和 `ItemBucket` 自己的液体射线可能命中不同方块。此前客户端取消交互后等待服务器方块更新，没有立即把含水宿主显示成干态；液体射线还可能越过宿主的空隙，收走后面的普通水源。

## 本次改动

- 扩展渲染状态增加水平流向角度，含水水面的顶面按流向选用流动水纹理并使用与 `BlockFluidRenderer` 相同的旋转 UV；无水平流向时继续使用静止水纹理。模型缓存键加入流向角度。
- 空桶右键含水宿主时，客户端先预测写入干态，服务器继续执行同样的状态写入并同步结果。清除储存流体后通知相邻方块更新。
- `FillBucketEvent` 的液体射线若经过含水宿主再命中后方水源，优先收取经过的含水宿主中的水。

## 验证

- `GRADLE_USER_HOME=E:\mdk\waterlogged\.gradle` 下运行 `gradlew.bat build -x test --console=plain`，构建成功，生成 `build/libs/waterlogged-0.1.0.jar`。
- 本轮没有关闭或重启用户正在运行的客户端。该客户端尚未载入本次改动；顶面纹理、空桶收水和相邻水流的游戏内结果仍待新构建加载后验证。
