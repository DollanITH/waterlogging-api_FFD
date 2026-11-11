# 含水方块代码对照表

对照来源：`Farmers-Future-Delight` 上游 `FFDGameplayHooks`、`FFDGameplayEvents`、`FFDHeightTransformer`、`FFDClientHeightHooks`。本表只记录代码覆盖关系，不把编译成功当作游戏行为验证。

| 功能 | 上游实现 | 当前实现 | 覆盖判断 | 当前风险/待验证 |
|---|---|---|---|---|
| 水桶放置含水状态 | `waterlogPlacedBlockState`，由 `ItemBlock` 放置流程调用 | `ItemBlockMixin` 重定向 `getStateForPlacement` | 基本覆盖 | 需验证目标格必须是流体源，普通水流不能误含水 |
| 空桶回收含水水 | `FFDGameplayEvents.onFillBucket` | `WaterloggedBucketHandler.onFillBucket` | 基本对齐 | 需验证客户端/服务端事件顺序、一次右键是否完成回收 |
| 额外含水方块 | 上游 `FFDStoredFluidStates` 与 `tryExtraFluidBucket` | `FFDStoredFluidStates` + `tryExtraFluidBucket` | 已覆盖 | 需验证轨道、树叶等方块的存储状态清理 |
| 流动水挡流 | `blocksFlowingWater` 注入 `BlockDynamicLiquid.isBlocked` | `BlockDynamicLiquidMixin.isBlocked` | 部分覆盖 | 当前实现没有完全复刻上游的 `targetState` 条件分支 |
| 相邻含水深度 | 替换 `BlockLiquid.getDepth` 为 `waterloggedLiquidDepth` | `BlockDynamicLiquidMixin.checkAdjacentBlock` 局部处理 | 不完整 | 可能导致水流忽强忽弱、错误高度和不连续水面 |
| 上方含水深度 | `waterloggedDepthAbove`，在 `updateTick` 中单独处理 | 当前没有等价的 updateTick 调用点替换 | 缺失 | 含水方块上方/下方的流动和源判定可能错误 |
| 碰撞箱遮挡 | `isWaterloggedFlowOccluded` + `canPassThrough` | `WaterloggedPlantFluid.canPassThrough`，仅从部分路径调用 | 部分覆盖 | 需要验证所有水平、垂直流动路径都使用共享面遮挡判断 |
| 原版水流向量 | 上游通过原版 `BlockLiquid` 深度采样 | `BlockLiquidMixin.getFlow` 自行计算 | 部分覆盖 | 自行计算与原版 level、下方水面和源水连接规则可能不完全一致 |
| 共享水面渲染 | `fluidSideVisible`、`BlockFluidRenderer` 字节码改写 | `BlockLiquidMixin.shouldSideBeRendered` + `BlockFluidRendererMixin` | 部分覆盖 | 含水方块与普通水流接壤处可能出现断层或静止水纹理 |
| 流体渲染状态 | `fluidRenderState`、`extendedWaterloggedRenderState` | `BlockMixin`、`BlockModelShapesMixin`、`StateMapperMixin` | 基本覆盖 | 需运行时截图确认材质和高度采样 |
| 破坏粒子 | `waterloggedDestroyParticleState` 在 `RenderGlobal.playEvent` 两处改写 | `RenderGlobalMixin` Redirect 粒子调用并恢复干态 | 部分覆盖 | 当前未确认所有 `playEvent` 映射下都命中，仍可能出现 missingno |
| 方块破坏后保留流体 | `removeWaterloggedBlockByPlayer`、`setBlockStateAfterRemoval` | `WorldMixin` 通用 `setBlockState` 拦截 | 不等价 | 玩家破坏、剪切、爆炸的替换状态路径没有逐一对应上游调用 |
| 爆炸破坏 | `setBlockToAirAfterExplosion` | 未发现独立等价 Mixin 注入 | 缺失/待核实 | 含水方块爆炸后可能错误留下或丢失水 |
| 邻居更新 | `handleUniversalWaterloggedNeighborChanged` | `WorldMixin.neighborChanged`、Chunk/WorldServer 相关处理 | 部分覆盖 | 需验证破坏、放置、流体更新后的邻居通知顺序 |
| Forge `BlockFluidBase` | `canDisplace`、`getEffectiveQuanta`、`hasVerticalFlow` 注入 | `BlockFluidBaseMixin` | 基本覆盖 | 依赖 Mixin 运行时成功应用；接口已移出 mixin 包以避免类加载崩溃 |
| Forge `BlockFluidClassic` | `getQuantaValue`、`isSourceBlock` 注入 | `BlockFluidClassicMixin` | 基本覆盖 | 需验证不同 Forge 流体与水的 quanta 计算 |
| 实体水中移动 | 上游流体材质/加速度钩子 | `EntityMixin`、`WorldMixin` | 部分覆盖 | 需实测实体移动、眼部水材质和含水格边界 |
| 存档/区块状态 | 上游存储流体状态与区块读写配套 | `ChunkMixin`、`FFDFluidloggedData`、`FFDStoredFluidStates` | 部分覆盖 | 需验证重进游戏后含水状态、额外存储状态是否一致 |

## 当前结论

当前最明显的差异集中在 `BlockDynamicLiquid.updateTick`：上游不是只增加一个相邻含水源判断，而是同时改写相邻深度、上方深度和遮挡判断。破坏/爆炸路径也仍未做到与上游的独立调用点一一对应。这两组差异应优先处理。

## 验证状态

- `gradlew.bat build --console=plain` 已成功。
- 尚未完成运行时 Mixin 应用日志、真实水流、空桶、破坏粒子和重进存档的逐项验收。

## 本轮临时对齐

已在 `BlockMixin` 中补入上游对应的 `removedByPlayer` 和 `onBlockExploded` 路径：含水方块被玩家破坏或爆炸时，先恢复替换状态并清理存储流体，避免只依赖 `World.setBlockState` 的通用拦截。

`compileJava` 已成功；水流 updateTick 的完整字节码级等价改写仍需单独处理和运行时验证。

本轮又加入了 `BlockDynamicLiquid.updateTick` 对 `BlockLiquid.getDepth` 的重定向：含水状态按上游规则作为水深 0 参与更新。完整构建已成功。

重新完整检索上游后发现，上游并未自定义重写 `BlockLiquid.getFlow`；流向仍由原版算法根据被替换的深度读取计算。当前已移除本项目自定义 `getFlow` 计算，避免绕过原版水流方向、下方水面和源连接规则。完整构建成功。
