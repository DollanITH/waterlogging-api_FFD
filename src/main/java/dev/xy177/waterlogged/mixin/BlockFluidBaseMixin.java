package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forge fluid source and displacement hooks from the upstream core transformer. */
@Mixin(value = BlockFluidBase.class, remap = false)
public abstract class BlockFluidBaseMixin implements dev.xy177.waterlogged.common.fluid.WaterloggedFluidQuantaAccess {
    @Shadow protected int quantaPerBlock;
    @Shadow protected int densityDir;

    @Override
    public int waterlogged$getQuantaPerBlock() {
        return quantaPerBlock;
    }

    @Inject(method = "canDisplace", at = @At("HEAD"), cancellable = true)
    private void waterlogged$protectHost(IBlockAccess world, BlockPos pos,
            CallbackInfoReturnable<Boolean> result) {
        if (WaterloggedBlockApi.isWaterlogged(world, pos)
                || WaterloggedConfig.isAdditionalWaterloggingBlock(world,
                        world.getBlockState(pos).getBlock())) {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "getEffectiveQuanta", at = @At("HEAD"), cancellable = true)
    private void waterlogged$containedQuanta(IBlockAccess world, BlockPos pos,
            CallbackInfoReturnable<Integer> result) {
        Block self = (Block) (Object) this;
        Fluid fluid = WaterloggedBlockApi.getFluidForBlock(self);
        if (fluid != null && WaterloggedBlockApi.isWaterlogged(world, pos)
                && WaterloggedBlockApi.getSourceFluid(world, pos) == fluid)
            result.setReturnValue(quantaPerBlock);
    }

    @Inject(method = "hasVerticalFlow", at = @At("RETURN"), cancellable = true)
    private void waterlogged$verticalSource(IBlockAccess world, BlockPos pos,
            CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) return;
        BlockPos neighbor = pos.down(densityDir);
        Fluid fluid = WaterloggedBlockApi.getFluidForBlock((Block) (Object) this);
        if (fluid != null && WaterloggedBlockApi.isWaterlogged(world, neighbor)
                && WaterloggedBlockApi.getContainedFluid(world, neighbor) == fluid)
            result.setReturnValue(true);
    }
}
