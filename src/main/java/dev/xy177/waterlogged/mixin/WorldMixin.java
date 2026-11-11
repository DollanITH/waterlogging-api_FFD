package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import dev.xy177.waterlogged.common.block.WaterloggedPlantFluid;
import dev.xy177.waterlogged.common.fluid.FFDFluidloggedData;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves a fluid when a waterloggable host is placed or filled. */
@Mixin(World.class)
public abstract class WorldMixin {
    @Redirect(method = "handleMaterialAcceleration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/Block;isEntityInsideMaterial(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/Entity;DLnet/minecraft/block/material/Material;Z)Ljava/lang/Boolean;"))
    private Boolean waterlogged$insideContainedMaterial(Block block, IBlockAccess access,
            BlockPos pos, IBlockState state, Entity entity, double y,
            Material material, boolean testing) {
        Boolean original = block.isEntityInsideMaterial(access, pos, state, entity, y,
                material, testing);
        if (original != null) return original;
        Fluid fluid = WaterloggedBlockApi.getContainedFluid(access, pos);
        return WaterloggedBlockApi.isWaterlogged(access, pos) && fluid != null
                && WaterloggedBlockApi.sourceState(fluid).getMaterial() == material
                ? Boolean.TRUE : null;
    }

    @Redirect(method = "handleMaterialAcceleration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/Block;modifyAcceleration(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d waterlogged$containedAcceleration(Block block, World world,
            BlockPos pos, Entity entity, Vec3d motion) {
        if (WaterloggedBlockApi.isWaterlogged(world, pos)
                && WaterloggedBlockApi.getContainedFluid(world, pos) == FluidRegistry.WATER) {
            return WaterloggedBlockApi.modifyAcceleration(world, pos, entity, motion);
        }
        return block.modifyAcceleration(world, pos, entity, motion);
    }

    @Redirect(method = "isMaterialInBB", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/Block;isAABBInsideMaterial(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Lnet/minecraft/block/material/Material;)Ljava/lang/Boolean;"))
    private Boolean waterlogged$insideContainedMaterialBox(Block block, World world,
            BlockPos pos, AxisAlignedBB box, Material material) {
        Boolean original = block.isAABBInsideMaterial(world, pos, box, material);
        if (original != null) return original;
        Fluid fluid = WaterloggedBlockApi.getContainedFluid(world, pos);
        return WaterloggedBlockApi.isWaterlogged(world, pos) && fluid != null
                && WaterloggedBlockApi.sourceState(fluid).getMaterial() == material
                ? Boolean.TRUE : null;
    }

    @Inject(method = "handleMaterialAcceleration", at = @At("RETURN"), cancellable = true)
    private void waterlogged$containedWaterMovement(AxisAlignedBB box, Material material,
            Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (material != Material.WATER || result.getReturnValue()) return;
        World world = (World) (Object) this;
        for (BlockPos pos : BlockPos.getAllInBoxMutable(
                net.minecraft.util.math.MathHelper.floor(box.minX),
                net.minecraft.util.math.MathHelper.floor(box.minY),
                net.minecraft.util.math.MathHelper.floor(box.minZ),
                net.minecraft.util.math.MathHelper.ceil(box.maxX) - 1,
                net.minecraft.util.math.MathHelper.ceil(box.maxY) - 1,
                net.minecraft.util.math.MathHelper.ceil(box.maxZ) - 1)) {
            if (box.minY < pos.getY() + 8.0D / 9.0D
                    && box.maxY > pos.getY()
                    && WaterloggedBlockApi.getContainedFluid(world, pos) == FluidRegistry.WATER
                    && WaterloggedBlockApi.isWaterlogged(world, pos)) {
                result.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void waterlogged$preserveFluid(BlockPos pos, IBlockState next, int flags,
            CallbackInfoReturnable<Boolean> result) {
        if (WaterloggedBlockApi.isInternalStateWrite()) return;
        World world = (World) (Object) this;
        IBlockState old = world.getBlockState(pos);
        if (old == next) return;

        boolean oldWaterlogged = WaterloggedBlockApi.isWaterlogged(world, pos);
        Fluid existing = WaterloggedBlockApi.getContainedFluid(world, pos);
        Fluid incoming = WaterloggedBlockApi.getFluidForBlock(next.getBlock());
        if (incoming != null && existing != null) {
            if (oldWaterlogged) {
                result.setReturnValue(incoming == existing);
                return;
            }
        }

        IBlockState wet;
        IBlockState fluidState;
        if (next.getBlock() == Blocks.AIR && oldWaterlogged) {
            fluidState = WaterloggedBlockApi.getFluidState(world, pos);
            if (fluidState == null || fluidState.getBlock() == Blocks.AIR) return;
            next = fluidState;
            wet = null;
        } else {
            if (incoming != null && !oldWaterlogged
                    && (WaterloggedBlockApi.canBeWaterlogged(old)
                            || WaterloggedConfig.isAdditionalWaterloggingBlock(world, old.getBlock()))) {
                if (!WaterloggedConfig.isFluidAllowed(world, incoming)
                        || !WaterloggedPlantFluid.isSourceFluid(next)) {
                    result.setReturnValue(false);
                    return;
                }
                wet = WaterloggedBlockApi.withWaterlogged(old, true);
                if (wet == null) {
                    wet = old;
                }
                fluidState = WaterloggedBlockApi.sourceState(incoming);
            } else if (existing != null && WaterloggedConfig.isFluidAllowed(world, existing)
                    && WaterloggedPlantFluid.isSourceFluid(
                            WaterloggedBlockApi.getFluidState(world, pos))) {
                wet = WaterloggedBlockApi.withWaterlogged(next, true);
                if (wet == null && WaterloggedConfig.isAdditionalWaterloggingBlock(world, next.getBlock())) {
                    wet = next;
                }
                fluidState = WaterloggedBlockApi.getFluidState(world, pos);
            } else {
                return;
            }
            if (wet == null || fluidState == null) return;
            next = wet;
        }

        if (WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(world, pos, next, flags)) {
            if (wet == null) {
                FFDStoredFluidStates.remove(world, pos);
                FFDFluidloggedData.set(world, pos, null);
                Fluid restored = WaterloggedBlockApi.getFluidForBlock(next.getBlock());
                if (restored != null) {
                    world.scheduleUpdate(pos, next.getBlock(),
                            WaterloggedBlockApi.fluidTickRate(world, restored));
                    world.notifyNeighborsOfStateChange(pos, next.getBlock(), false);
                }
            } else if (WaterloggedBlockApi.withWaterlogged(next, false) != null) {
                Fluid fluid = WaterloggedBlockApi.getFluidForBlock(fluidState.getBlock());
                WaterloggedBlockApi.recordContainedFluid(world, pos, fluid);
                world.scheduleUpdate(pos, next.getBlock(),
                        WaterloggedBlockApi.fluidTickRate(world, fluid));
            } else {
                FFDStoredFluidStates.set(world, pos, fluidState);
                world.scheduleUpdate(pos, next.getBlock(),
                        WaterloggedBlockApi.fluidTickRate(world,
                                WaterloggedBlockApi.getFluidForBlock(fluidState.getBlock())));
            }
            result.setReturnValue(true);
        } else {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "neighborChanged", at = @At("TAIL"))
    private void waterlogged$afterNeighborChanged(BlockPos pos, Block changedBlock,
            BlockPos changedPos, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        World world = (World) (Object) this;
        WaterloggedBlockApi.onNeighborChanged(world, pos, world.getBlockState(pos).getBlock());
    }

    @Redirect(method = "immediateBlockTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;updateTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V"))
    private void waterlogged$immediateBlockTick(Block block, World world, BlockPos pos,
            IBlockState state, Random random) {
        WaterloggedBlockApi.runBlockTick(block, world, pos, state, random);
    }
}
