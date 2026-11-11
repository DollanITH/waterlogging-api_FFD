package dev.xy177.waterlogged.mixin;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Random;

/** Makes vanilla flowing liquid treat a matching waterlogged neighbour as a source. */
@Mixin(BlockDynamicLiquid.class)
public abstract class BlockDynamicLiquidMixin {
    @Shadow
    protected abstract int checkAdjacentBlock(World world, BlockPos pos, int currentMinLevel);

    @Shadow
    int adjacentSourceBlocks;

    @Unique
    private static final ThreadLocal<BlockPos> waterlogged$flowOrigin = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<World> waterlogged$flowWorld = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<AdjacentContext> waterlogged$adjacentContext = new ThreadLocal<>();

    @Inject(method = "isBlocked", at = @At("RETURN"), cancellable = true)
    private void waterlogged$protectHost(World world, BlockPos pos, IBlockState state,
            CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) return;
        if (WaterloggedBlockApi.protectContainedHost(world, pos)) {
            result.setReturnValue(true);
            return;
        }
        if (((BlockDynamicLiquid) (Object) this).getDefaultState().getMaterial() == Material.WATER
                && state != null && !WaterloggedBlockApi.isWaterlogged(state)
                && WaterloggedBlockApi.canBeWaterlogged(state)
                && !WaterloggedConfig.isAdditionalWaterloggingBlock(world, state.getBlock())) {
            result.setReturnValue(true);
        }
    }

    @Inject(method = "tryFlowInto", at = @At("HEAD"), cancellable = true)
    private void waterlogged$keepRail(World world, BlockPos pos, IBlockState state,
            int level, CallbackInfo callback) {
        if (world.isRemote || !(state.getBlock() instanceof BlockRailBase)
                || ((BlockDynamicLiquid) (Object) this).getDefaultState().getMaterial() != Material.WATER
                || WaterloggedBlockApi.isWaterlogged(world, pos)) return;
        IBlockState fluid = ((BlockDynamicLiquid) (Object) this).getDefaultState()
                .withProperty(BlockLiquid.LEVEL, level);
        FFDStoredFluidStates.set(world, pos, fluid);
        world.scheduleUpdate(pos, state.getBlock(), 1);
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        callback.cancel();
    }

    @Inject(method = "updateTick", at = @At("HEAD"))
    private void waterlogged$captureFlowOrigin(World world, BlockPos pos, IBlockState state,
            Random random, CallbackInfo callback) {
        waterlogged$flowOrigin.set(pos);
        waterlogged$flowWorld.set(world);
    }

    @Inject(method = "updateTick", at = @At("RETURN"))
    private void waterlogged$clearFlowOrigin(World world, BlockPos pos, IBlockState state,
            Random random, CallbackInfo callback) {
        waterlogged$flowOrigin.remove();
        waterlogged$flowWorld.remove();
    }

    @Inject(method = "checkAdjacentBlock", at = @At("HEAD"))
    private void waterlogged$captureAdjacentContext(World world, BlockPos pos, int current,
            CallbackInfoReturnable<Integer> callback) {
        waterlogged$adjacentContext.set(new AdjacentContext(world, pos));
    }

    @Inject(method = "checkAdjacentBlock", at = @At("RETURN"))
    private void waterlogged$clearAdjacentContext(World world, BlockPos pos, int current,
            CallbackInfoReturnable<Integer> callback) {
        waterlogged$adjacentContext.remove();
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "updateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockDynamicLiquid;checkAdjacentBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;I)I"))
    private int waterlogged$checkAdjacent(BlockDynamicLiquid liquid, World world,
            BlockPos neighbor, int current) {
        BlockPos origin = waterlogged$flowOrigin.get();
        if (origin != null && WaterloggedBlockApi.isWaterloggedFlowOccluded(liquid, world,
                origin, neighbor)) {
            return current;
        }
            return ((BlockDynamicLiquidMixin) (Object) liquid)
                    .checkAdjacentBlock(world, neighbor, current);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "checkAdjacentBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockDynamicLiquid;getDepth(Lnet/minecraft/block/state/IBlockState;)I"))
    private int waterlogged$containedAdjacentDepth(BlockDynamicLiquid liquid, IBlockState state) {
        AdjacentContext context = waterlogged$adjacentContext.get();
        if (context == null) {
            return WaterloggedBlockApi.vanillaLiquidDepth(liquid, state);
        }
        return WaterloggedBlockApi.waterloggedLiquidDepth(
                WaterloggedBlockApi.vanillaLiquidDepth(liquid, state), liquid,
                context.world, context.pos);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "updateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/BlockDynamicLiquid;getDepth(Lnet/minecraft/block/state/IBlockState;)I"))
    private int waterlogged$containedAboveDepth(BlockDynamicLiquid liquid, IBlockState state) {
        World world = waterlogged$flowWorld.get();
        BlockPos origin = waterlogged$flowOrigin.get();
        int depth = WaterloggedBlockApi.vanillaLiquidDepth(liquid, state);
        return world == null || origin == null ? depth
                : WaterloggedBlockApi.waterloggedDepthAbove(depth, liquid, world, origin);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = {"updateTick", "getPossibleFlowDirections", "getSlopeDistance"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/state/IBlockState;getValue(Lnet/minecraft/block/properties/IProperty;)Ljava/lang/Comparable;"),
            require = 0)
    private Comparable<?> waterlogged$liquidLevel(IBlockState state, IProperty<?> property) {
        return WaterloggedBlockApi.getLiquidLevelValue(state, property);
    }

    @Unique
    private static final class AdjacentContext {
        private final World world;
        private final BlockPos pos;

        private AdjacentContext(World world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }
    }

}
