package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Match the upstream ItemBlock placement hook: only a source fluid is preserved. */
@Mixin(ItemBlock.class)
public abstract class ItemBlockMixin {
    @Redirect(method = "onItemUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/Block;getStateForPlacement(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;FFFILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/block/state/IBlockState;"))
    private IBlockState waterlogged$placeIntoSource(Block block, World world, BlockPos pos,
            EnumFacing side, float hitX, float hitY, float hitZ, int meta,
            EntityLivingBase placer, EnumHand hand) {
        IBlockState placed = block.getStateForPlacement(world, pos, side,
                hitX, hitY, hitZ, meta, placer, hand);
        Fluid fluid = WaterloggedBlockApi.getSourceFluid(world, pos);
        if (fluid == null || !WaterloggedConfig.isFluidAllowed(world, fluid)) return placed;
        IBlockState wet = WaterloggedBlockApi.withWaterlogged(placed, true);
        if (wet == null) return placed;
        if (!world.isRemote)
            world.scheduleUpdate(pos, wet.getBlock(), WaterloggedBlockApi.fluidTickRate(world, fluid));
        return wet;
    }
}
