package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import dev.xy177.waterlogged.common.fluid.WaterloggedFluidQuantaAccess;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockFluidClassic.class, remap = false)
public abstract class BlockFluidClassicMixin {
    @Inject(method = "getQuantaValue", at = @At("RETURN"), cancellable = true)
    private void waterlogged$containedQuanta(IBlockAccess world, BlockPos pos,
            CallbackInfoReturnable<Integer> result) {
        if (!WaterloggedBlockApi.isWaterlogged(world, pos)) return;
        Fluid fluid = WaterloggedBlockApi.getFluidForBlock((Block) (Object) this);
        if (fluid != null && WaterloggedBlockApi.getContainedFluid(world, pos) == fluid) {
            int quanta = ((WaterloggedFluidQuantaAccess) this).waterlogged$getQuantaPerBlock();
            result.setReturnValue(Math.max(0, quanta - FFDStoredFluidStates.level(world, pos)));
        }
    }

    @Inject(method = "isSourceBlock", at = @At("HEAD"), cancellable = true)
    private void waterlogged$containedSource(IBlockAccess world, BlockPos pos,
            CallbackInfoReturnable<Boolean> result) {
        Fluid fluid = WaterloggedBlockApi.getFluidForBlock((Block) (Object) this);
        if (fluid != null && WaterloggedBlockApi.isWaterlogged(world, pos)
                && WaterloggedBlockApi.getSourceFluid(world, pos) == fluid)
            result.setReturnValue(true);
    }
}
