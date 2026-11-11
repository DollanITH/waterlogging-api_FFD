package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import net.minecraft.block.BlockFence;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep fence connections based on the dry host material of a wet state. */
@Mixin(BlockFence.class)
public abstract class BlockFenceMixin {
    @Redirect(method = "canConnectTo", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/block/state/IBlockState;getMaterial()"
                    + "Lnet/minecraft/block/material/Material;"))
    private Material waterlogged$replacementMaterial(IBlockState state) {
        return WaterloggedBlockApi.blockReplacementMaterial(state);
    }
}
