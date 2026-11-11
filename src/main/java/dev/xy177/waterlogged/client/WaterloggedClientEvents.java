package dev.xy177.waterlogged.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.client.model.WaterloggedPlantBakedModel;
import dev.xy177.waterlogged.common.block.BlockWaterloggedStateCarrier;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.IStateMapper;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class WaterloggedClientEvents {
    private WaterloggedClientEvents() {}

    @SubscribeEvent
    public static void registerCarrierStateMappers(ModelRegistryEvent event) {
        registerStateMappers();
    }

    /** Register before model discovery as well as during ModelRegistryEvent. */
    public static void registerStateMappers() {
        IStateMapper mapper = block -> java.util.Collections.emptyMap();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (block instanceof BlockWaterloggedStateCarrier) {
                ModelLoader.setCustomStateMapper(block, mapper);
            } else if (WaterloggedBlockApi.hasUniversalWaterloggedProperty(
                    block.getDefaultState())) {
                WaterloggedBlockApi.registerRenderedBlock(block);
                WaterloggedBlockApi.registerWaterColorBlock(block);
            }
        }
    }

    @SubscribeEvent
    public static void bakeModels(ModelBakeEvent event) {
        net.minecraft.client.renderer.block.statemap.BlockStateMapper mapper =
                event.getModelManager().getBlockModelShapes().getBlockStateMapper();
        Set<ModelResourceLocation> models = new HashSet<>();
        for (Block block : WaterloggedBlockApi.renderedBlocks())
            models.addAll(mapper.getVariants(block).values());
        for (ModelResourceLocation location : new ArrayList<>(event.getModelRegistry().getKeys())) {
            if ("inventory".equals(location.getVariant())
                    || !WaterloggedBlockApi.isRenderedModel(location)
                        && !models.contains(location)) continue;
            IBakedModel original = event.getModelRegistry().getObject(location);
            if (original != null && !(original instanceof WaterloggedPlantBakedModel))
                event.getModelRegistry().putObject(location,
                        new WaterloggedPlantBakedModel(original));
        }
    }
}
