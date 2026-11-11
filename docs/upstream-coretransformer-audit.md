# 上游含水 CoreTransformer 对照审计

审计基准：`Farmers-Future-Delight` 提交 `bd0b45045edbdbe07a2e98e93c564c47796381c9`。本次按上游 `FFDHeightTransformer` 的变换目标与本工程 `waterlogged.mixin.json` 注册的 Mixin 逐项对照。这里的“缺失”指没有发现对应的注入入口，不能靠同名 API 方法存在来判定已实现。

| 上游变换目标 | 上游作用 | 本工程状态 |
| --- | --- | --- |
| `BlockStateContainer`、`StateMapperBase` | 加入通用含水属性、模型键忽略该属性 | 有对应 Mixin |
| `Block` | 状态 ID、可替换性、渲染层、玩家破坏后留水、爆炸后留水、挖掘进度 | 只有状态 ID 与渲染层；其余没有对应 `Block` 注入 |
| `ItemBlock` | 放进水源时设含水状态、处理物品 `BlockStateTag` | 没有对应 Mixin |
| `World` | `mayPlace`、含水亮度、实体材质判定、水流加速度、邻居更新 | 有部分世界状态与实体材质注入；`mayPlace`、含水亮度没有对应注入 |
| `BlockDynamicLiquid`、`BlockLiquid`、`BlockStaticLiquid` | 原版水深、挡流、相邻水源与静止水更新 | 有前两者的部分 Mixin；没有 `BlockStaticLiquid` 对应 Mixin |
| Forge `BlockFluidBase`、`BlockFluidClassic` | 模组流体置换、流量、水源查询 | 没有对应 Mixin |
| `WorldServer` | 计划与即时方块 tick | 有计划 tick 对应 Mixin；仍需核对上游两个调用点的覆盖情况 |
| `RenderGlobal` | 破坏状态解码及粒子状态 | 有对应 Mixin，但实现方式不同，尚无运行时通过证据 |
| `BlockRendererDispatcher`、`RenderChunk` | 特殊含水渲染、扩展状态及渲染层 | 没有对应 Mixin；本工程以模型包装和 `Block` 注入替代，未证明行为等价 |
| `BlockFluidRenderer` | 含水格水面、流体材质、液位、颜色 | 只有水面高度的部分 Mixin |
| `Entity`、`Chunk` | 水中判定、区块状态 | 有对应 Mixin，细节仍需行为测试 |

上游还含可选的 Fluidlogged API、OptiFine、HWYLA 兼容变换。本独立模组目前没有这些兼容入口，不能声称与上游在这些组合下等价。

结论：**确实存在遗漏，当前移植并非上游 coretransformer 的完整等价实现。** 对用户已报告的问题，最高优先级是 `Block` 破坏/爆炸后留水、`ItemBlock` 放置、原版和 Forge 流体挡流、`BlockFluidRenderer`/`BlockRendererDispatcher` 渲染，以及 `RenderGlobal` 粒子解码。后续修复须逐点实现并在新客户端实测；仅编译通过不能验收。
