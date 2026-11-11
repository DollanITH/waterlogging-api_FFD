package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wet states share the host's dry model, including its destruction sprite. */
@Mixin(BlockModelShapes.class)
public abstract class BlockModelShapesMixin {
    @Inject(method = "getModelForState", at = @At("HEAD"), cancellable = true)
    private void waterlogged$hostModel(IBlockState state,
            CallbackInfoReturnable<IBakedModel> result) {
        if (!WaterloggedBlockApi.isWaterlogged(state)) return;
        IBlockState dry = WaterloggedBlockApi.withWaterlogged(state, false);
        if (dry != null && dry != state) {
            BlockModelShapes shapes = (BlockModelShapes) (Object) this;
            IBakedModel model = shapes.getModelForState(dry);
            if (model == shapes.getModelManager().getMissingModel()) {
                ModelResourceLocation location = shapes.getBlockStateMapper()
                        .getVariants(dry.getBlock()).get(dry);
                if (location != null) {
                    model = shapes.getModelManager().getModel(location);
                }
            }
            result.setReturnValue(model);
        }
    }
}
