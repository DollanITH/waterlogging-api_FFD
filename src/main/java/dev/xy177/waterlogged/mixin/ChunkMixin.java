package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.common.block.WaterloggedPlantFluid;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chunk.class)
public abstract class ChunkMixin {
    @Inject(method = "setBlockState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;onBlockAdded(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
                    shift = At.Shift.AFTER))
    private void waterlogged$onBlockAdded(BlockPos pos, IBlockState next,
            CallbackInfoReturnable<IBlockState> result) {
        Chunk chunk = (Chunk) (Object) this;
        WaterloggedPlantFluid.onBlockAdded(chunk.getWorld(), pos, next.getBlock());
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void waterlogged$stateChanged(BlockPos pos, IBlockState next,
            CallbackInfoReturnable<IBlockState> result) {
        FFDStoredFluidStates.changed(result.getReturnValue(), (Chunk) (Object) this, pos, next);
    }
}
