package dev.xy177.waterlogged;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import top.outlands.foundation.TransformerDelegate;
import dev.xy177.waterlogged.core.WaterloggedWorldEditTransformer;

@IFMLLoadingPlugin.Name("Waterlogged Coremod")
@IFMLLoadingPlugin.MCVersion("1.12.2")
/** Entry point for Waterlogged's mixin bootstrap and optional ASM transformers. */
public final class WaterloggedLoadingPlugin implements IFMLLoadingPlugin {
    static {
        TransformerDelegate.registerExplicitTransformer(
                new WaterloggedWorldEditTransformer(),
                "com.sk89q.worldedit.function.block.ExtentBlockCopy",
                "com/sk89q/worldedit/function/block/ExtentBlockCopy",
                "com.sk89q.worldedit.forge.ForgeWorld",
                "com/sk89q/worldedit/forge/ForgeWorld");
        System.out.println("[Waterlogged] Registered optional WorldEdit waterlogged transformer");
    }

    @Override public String[] getASMTransformerClass() {
        return new String[0];
    }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }
}
