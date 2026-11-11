package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLiquid.class)
public abstract class BlockLiquidMixin {
    /**
     * Vanilla's liquid code asks BlockLiquid for the depth of a state in a
     * number of places outside BlockDynamicLiquid itself.  A waterlogged host
     * has no LEVEL property, so the vanilla method otherwise reads an invalid
     * property and treats the host as a non-liquid block.  The upstream core
     * transformer inserts this same early return into getDepth.
     */
    @Inject(method = "getDepth", at = @At("HEAD"), cancellable = true)
    private void waterlogged$containedDepth(IBlockState state,
            CallbackInfoReturnable<Integer> result) {
        if (WaterloggedBlockApi.isWaterloggedStateForLiquid(state)) {
            result.setReturnValue(WaterloggedBlockApi.waterloggedLiquidDepth(
                    (BlockLiquid) (Object) this));
        }
    }

    @Inject(method = "shouldSideBeRendered", at = @At("RETURN"), cancellable = true)
    private void waterlogged$hideSharedWaterFace(IBlockState state, IBlockAccess world,
            BlockPos pos, EnumFacing side, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(WaterloggedBlockApi.fluidSideVisible(result.getReturnValue(),
                state, world, pos, side));
    }
}
