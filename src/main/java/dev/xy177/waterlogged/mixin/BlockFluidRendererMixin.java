package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.block.WaterloggedPlantFluid;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use the same corner heights on both sides of contained and vanilla water. */
@Mixin(BlockFluidRenderer.class)
public abstract class BlockFluidRendererMixin {
    @Redirect(method = {"renderFluid", "getFluidHeight"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/IBlockAccess;getBlockState("
                            + "Lnet/minecraft/util/math/BlockPos;)"
                            + "Lnet/minecraft/block/state/IBlockState;"))
    private IBlockState waterlogged$renderState(IBlockAccess world, BlockPos pos) {
        return WaterloggedBlockApi.fluidRenderState(world, pos);
    }

    @Redirect(method = {"renderFluid", "getFluidHeight"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/block/state/IBlockState;getMaterial()"
                            + "Lnet/minecraft/block/material/Material;"))
    private Material waterlogged$renderMaterial(IBlockState state) {
        return WaterloggedBlockApi.renderedFluidMaterial(state);
    }

    @Redirect(method = {"renderFluid", "getFluidHeight"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/block/state/IBlockState;getValue("
                            + "Lnet/minecraft/block/properties/IProperty;)"
                            + "Ljava/lang/Comparable;"))
    private Comparable<?> waterlogged$renderValue(IBlockState state, IProperty<?> property) {
        return WaterloggedBlockApi.getLiquidLevelValue(state, property);
    }

    @Inject(method = "getFluidHeight", at = @At("HEAD"), cancellable = true)
    private void waterlogged$fluidHeight(IBlockAccess world, BlockPos pos, Material material,
            CallbackInfoReturnable<Float> result) {
        Fluid fluid = material == Material.WATER ? FluidRegistry.WATER : null;
        if (fluid == null) return;
        for (int index = 0; index < 4; index++) {
            BlockPos sample = pos.add(-(index & 1), 0, -((index >> 1) & 1));
            if (WaterloggedBlockApi.isWaterlogged(world, sample)
                    || WaterloggedBlockApi.isWaterlogged(world, sample.up())) {
                result.setReturnValue(WaterloggedPlantFluid.getFluidHeight(world, pos, fluid));
                return;
            }
        }
    }
}
