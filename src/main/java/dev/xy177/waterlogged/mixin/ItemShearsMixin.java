package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve contained fluid when vanilla shears remove a host block. */
@Mixin(ItemShears.class)
public abstract class ItemShearsMixin {
    @Redirect(method = "onBlockStartBreak", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlockState(" +
                    "Lnet/minecraft/util/math/BlockPos;" +
                    "Lnet/minecraft/block/state/IBlockState;I)Z"))
    private boolean waterlogged$preserveFluidAfterShearing(World world, BlockPos pos,
            net.minecraft.block.state.IBlockState replacement, int flags) {
        return WaterloggedBlockApi.setBlockStateAfterShearing(world, pos, replacement, flags);
    }
}
