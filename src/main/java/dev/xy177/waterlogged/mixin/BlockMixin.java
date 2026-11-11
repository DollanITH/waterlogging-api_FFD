package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Redirect(method = "isReplaceable", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/block/state/IBlockState;getMaterial()"
                    + "Lnet/minecraft/block/material/Material;"))
    private Material waterlogged$replacementMaterial(IBlockState state) {
        return WaterloggedBlockApi.blockReplacementMaterial(state);
    }

    @Inject(method = "removedByPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/Block;onBlockHarvested("
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/state/IBlockState;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void waterlogged$removeByPlayer(IBlockState state, World world, BlockPos pos,
            EntityPlayer player, boolean willHarvest, CallbackInfoReturnable<Boolean> result) {
        if (WaterloggedBlockApi.isWaterlogged(world, pos)) {
            result.setReturnValue(WaterloggedBlockApi.removeWaterloggedBlockByPlayer(
                    world, pos, state));
        }
    }

    @Redirect(method = "onBlockExploded", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlockToAir(Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean waterlogged$explode(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return WaterloggedBlockApi.setBlockStateAfterRemoval(world, pos,
                net.minecraft.init.Blocks.AIR.getDefaultState(), 3, state);
    }
    @Inject(method = "getStateId", at = @At("RETURN"), cancellable = true)
    private static void waterlogged$encode(IBlockState state, CallbackInfoReturnable<Integer> result) {
        result.setReturnValue(WaterloggedBlockApi.encodeUniversalStateId(result.getReturnValue(), state));
    }

    @Inject(method = "getStateById", at = @At("RETURN"), cancellable = true)
    private static void waterlogged$decode(int id, CallbackInfoReturnable<IBlockState> result) {
        result.setReturnValue(WaterloggedBlockApi.decodeUniversalStateId(result.getReturnValue(), id));
    }

    @Inject(method = "getExtendedState", at = @At("RETURN"), cancellable = true)
    private void waterlogged$renderState(IBlockState state, IBlockAccess world, BlockPos pos,
                                          CallbackInfoReturnable<IBlockState> result) {
        if (WaterloggedBlockApi.isWaterlogged(world, pos))
            result.setReturnValue(WaterloggedBlockApi.getExtendedState(result.getReturnValue(), world, pos));
    }

    @Inject(method = "canRenderInLayer", at = @At("RETURN"), cancellable = true)
    private void waterlogged$renderLayer(IBlockState state, BlockRenderLayer layer,
                                         CallbackInfoReturnable<Boolean> result) {
        if (layer == BlockRenderLayer.TRANSLUCENT && WaterloggedBlockApi.isWaterlogged(state))
            result.setReturnValue(true);
    }
}
