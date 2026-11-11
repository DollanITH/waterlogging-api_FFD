package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Break particles must use the original host model and its texture. */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {
    @Unique
    private static final ThreadLocal<Integer> waterlogged$destroyData = new ThreadLocal<>();

    @Inject(method = "playEvent", at = @At("HEAD"))
    private void waterlogged$captureDestroyData(EntityPlayer player, int type, BlockPos pos,
            int data, CallbackInfo callback) {
        waterlogged$destroyData.set(data);
    }

    @Inject(method = "playEvent", at = @At("RETURN"))
    private void waterlogged$clearDestroyData(EntityPlayer player, int type, BlockPos pos,
            int data, CallbackInfo callback) {
        waterlogged$destroyData.remove();
    }

    @Redirect(method = "playEvent",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/block/Block;getStateFromMeta(I)Lnet/minecraft/block/state/IBlockState;"))
    private IBlockState waterlogged$breakState(Block block, int meta) {
        Integer data = waterlogged$destroyData.get();
        return WaterloggedBlockApi.waterloggedDestroyParticleState(block, meta,
                data == null ? meta : data);
    }

    @Redirect(method = "playEvent",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/client/particle/ParticleManager;addBlockDestroyEffects("
                            + "Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/block/state/IBlockState;)V"))
    private void waterlogged$breakParticleState(net.minecraft.client.particle.ParticleManager manager,
            BlockPos pos, IBlockState state) {
        Integer data = waterlogged$destroyData.get();
        manager.addBlockDestroyEffects(pos,
                WaterloggedBlockApi.waterloggedDestroyParticleState(state,
                        data == null ? 0 : data));
    }
}
