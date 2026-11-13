package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.block.WaterloggedPlantFluid;
import net.minecraft.block.BlockSponge;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends vanilla sponge absorption to water stored inside waterlogged hosts. */
@Mixin(BlockSponge.class)
public abstract class BlockSpongeMixin {
    @Inject(method = "tryAbsorb", at = @At("HEAD"))
    private void waterlogged$absorbContained(World world, BlockPos origin, IBlockState state,
                                              CallbackInfo callback) {
        if (world.isRemote) return;
        // Vanilla tryAbsorb may have changed this sponge to wet while it was
        // removing ordinary water.  Re-read the live state before extending
        // the same operation to contained water.
        WaterloggedPlantFluid.absorbWaterloggedAtSponge(world, origin,
                world.getBlockState(origin));
    }
}
