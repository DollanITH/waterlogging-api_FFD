# runClient 游戏资源目录修复

日期：2026-11-14

## 原因

- IDEA 的 Gradle 用户主目录配置正确，指向 `E:\mdk\waterlogged\.gradle`。Unimined 已在该目录的 `caches\unimined\assets` 下载 Minecraft 1.12 索引和对象。
- `runClient` 原先启动参数没有 `--assetsDir` 和 `--assetIndex`。Minecraft 1.12.2 因此按默认值从工作目录 `run\client\assets` 查找资源；该目录不存在。
- 资源包界面出现 `Default — Missing pack.mcmeta`，并且简体中文和声音资源无法正常使用，与 assets 根目录未传入启动参数吻合。

## 改动

- `build.gradle` 的开发运行参数现在显式传入 `unimined.globalCache.resolve('assets')` 和资源索引 `1.12`。

## 验证

- 使用 IDEA 配置的 Gradle 用户目录运行 `runClient --info --console=plain`，任务成功完成，游戏进入主菜单。
- 检查实际 Java 命令行，确认包含 `--assetsDir E:\mdk\waterlogged\.gradle\caches\unimined\assets` 与 `--assetIndex 1.12`。
- Unimined 解析 1,305 个资源对象；缓存中的简体中文语言、声音索引及声音样本均存在且大小匹配索引。
- 游戏日志记录资源管理器重载和 `OpenAL initialized`；未发现声音文件加载失败日志。日志不能证明扬声器实际播放，故实际听感尚未确认。
- 当时记录的 `waterlogged_state/...` 模型错误来自误加的 carrier 方块注册；该注册和对应 palette 编码已在后续修正中移除，不属于资源目录问题。
- `git diff --check` 通过。
