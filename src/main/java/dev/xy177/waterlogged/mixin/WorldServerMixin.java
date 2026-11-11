package dev.xy177.waterlogged.mixin;

import java.util.Random;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldServer.class)
public abstract class WorldServerMixin {
    @Redirect(method = "tickUpdates",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V"))
    private void waterlogged$scheduledBlockTick(Block block, World world, BlockPos pos,
            IBlockState state, Random random) {
        WaterloggedBlockApi.runBlockTick(block, world, pos, state, random);
    }
}
