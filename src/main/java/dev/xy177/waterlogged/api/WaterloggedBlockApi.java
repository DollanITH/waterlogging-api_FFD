package dev.xy177.waterlogged.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import dev.xy177.waterlogged.common.block.WaterloggedPlantFluid;
import dev.xy177.waterlogged.common.fluid.FFDFluidloggedData;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;

public final class WaterloggedBlockApi {
    public static final PropertyBool UNIVERSAL_WATERLOGGED =
            new UniversalWaterloggedProperty();
    private static final Set<Block> RENDERED_BLOCKS =
            Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
    private static final Set<Block> WATER_COLOR_BLOCKS =
            Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
    private static final Set<ResourceLocation> RENDERED_MODELS =
            new java.util.LinkedHashSet<>();
    private static final ThreadLocal<Boolean> INTERNAL_STATE_WRITE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<World> ACTIVE_FLOW_WORLD = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> ACTIVE_FLOW_ORIGIN = new ThreadLocal<>();

    private WaterloggedBlockApi() {
    }

    public static void setActiveFlowOrigin(World world, BlockPos pos) {
        ACTIVE_FLOW_WORLD.set(world);
        ACTIVE_FLOW_ORIGIN.set(pos);
    }

    public static void clearActiveFlowOrigin(World world) {
        if (ACTIVE_FLOW_WORLD.get() == world) {
            ACTIVE_FLOW_WORLD.remove();
            ACTIVE_FLOW_ORIGIN.remove();
        }
    }

    @Nullable
    public static BlockPos getActiveFlowOrigin(World world) {
        return ACTIVE_FLOW_WORLD.get() == world ? ACTIVE_FLOW_ORIGIN.get() : null;
    }

    public interface Adapter extends WaterloggedApi.Adapter {
    }

    public interface FluidMixRule extends WaterloggedApi.FluidMixRule {
    }

    public static void registerAdapter(Adapter adapter) {
        WaterloggedPlantFluid.registerAdapter(adapter);
    }

    public static void unregisterAdapter(Adapter adapter) {
        WaterloggedPlantFluid.unregisterAdapter(adapter);
    }

    public static void registerFluidMixRule(FluidMixRule rule) {
        WaterloggedPlantFluid.registerFluidMixRule(rule);
    }

    public static void unregisterFluidMixRule(FluidMixRule rule) {
        WaterloggedPlantFluid.unregisterFluidMixRule(rule);
    }

    public static synchronized void registerRenderedBlock(Block block) {
        if (block != null && (block instanceof IWaterloggableBlock
                || WaterloggedConfig.isWaterloggingEnabled())) {
            RENDERED_BLOCKS.add(block);
        }
    }

    public static synchronized void unregisterRenderedBlock(Block block) {
        RENDERED_BLOCKS.remove(block);
    }

    public static synchronized void registerWaterColorBlock(Block block) {
        if (block != null && (block instanceof IWaterloggableBlock
                || WaterloggedConfig.isWaterloggingEnabled())) {
            WATER_COLOR_BLOCKS.add(block);
        }
    }

    public static synchronized void unregisterWaterColorBlock(Block block) {
        WATER_COLOR_BLOCKS.remove(block);
    }

    public static synchronized void registerRenderedModel(ResourceLocation model) {
        if (model != null) {
            RENDERED_MODELS.add(model);
        }
    }

    public static synchronized void unregisterRenderedModel(ResourceLocation model) {
        RENDERED_MODELS.remove(model);
    }

    public static synchronized List<Block> renderedBlocks() {
        return new ArrayList<>(RENDERED_BLOCKS);
    }

    public static synchronized List<Block> waterColorBlocks() {
        return new ArrayList<>(WATER_COLOR_BLOCKS);
    }

    public static synchronized boolean isRenderedModel(ResourceLocation model) {
        if (model == null) {
            return false;
        }
        if (RENDERED_MODELS.contains(new ResourceLocation(
                model.getNamespace(), model.getPath()))) {
            return true;
        }
        for (Block block : RENDERED_BLOCKS) {
            ResourceLocation registryName = block.getRegistryName();
            if (registryName != null
                    && registryName.getNamespace().equals(model.getNamespace())
                    && registryName.getPath().equals(model.getPath())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWaterlogged(IBlockState state) {
        return WaterloggedPlantFluid.isWaterloggedBlock(state);
    }

    public static boolean hasUniversalWaterloggedProperty(IBlockState state) {
        if (state == null) {
            return false;
        }
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property == UNIVERSAL_WATERLOGGED) {
                return true;
            }
        }
        return false;
    }

    public static IProperty<?>[] appendUniversalWaterloggedProperty(
            Block block, IProperty<?>[] properties) {
        if (!WaterloggedConfig.isWaterloggingEnabled()
                || !supportsUniversalWaterlogging(block) || properties == null) {
            return properties;
        }
        for (IProperty<?> property : properties) {
            if ("waterlogged".equals(property.getName())) {
                return properties;
            }
        }
        IProperty<?>[] expanded = java.util.Arrays.copyOf(properties, properties.length + 1);
        expanded[properties.length] = UNIVERSAL_WATERLOGGED;
        return expanded;
    }

    public static synchronized void initializeUniversalStateMappings() {
        // Wet state persistence is handled by the per-chunk host/fluid records.
    }

    /** Resolve the original host state used by vanilla block-destroy particles. */
    public static IBlockState waterloggedDestroyParticleState(Block block, int meta, int data) {
        if (block == null) {
            return Blocks.AIR.getDefaultState();
        }
        IBlockState state;
        try {
            state = block.getStateFromMeta(meta);
        } catch (RuntimeException ignored) {
            state = block.getDefaultState();
        }
        return particleHostState(state);
    }

    /** Resolve the state passed to ParticleDigging for a destroyed block. */
    public static IBlockState waterloggedDestroyParticleState(IBlockState original, int data) {
        return particleHostState(original == null ? Blocks.AIR.getDefaultState() : original);
    }

    private static IBlockState particleHostState(IBlockState state) {
        if (isWaterlogged(state)) {
            IBlockState dry = withWaterlogged(state, false);
            if (dry != null) {
                return dry;
            }
        }
        return state;
    }

    public static Map<IProperty<?>, Comparable<?>> withoutUniversalWaterloggedProperty(
            Map<IProperty<?>, Comparable<?>> properties) {
        if (properties == null) {
            return properties;
        }
        boolean found = false;
        for (IProperty<?> property : properties.keySet()) {
            if (property == UNIVERSAL_WATERLOGGED) {
                found = true;
                break;
            }
        }
        if (!found) {
            return properties;
        }
        Map<IProperty<?>, Comparable<?>> filtered = new LinkedHashMap<>();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : properties.entrySet()) {
            if (entry.getKey() != UNIVERSAL_WATERLOGGED) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static boolean supportsUniversalWaterlogging(Block block) {
        if (block instanceof BlockStairs || block instanceof BlockFence
                || block instanceof BlockWall || block instanceof BlockPane
                || block instanceof BlockTrapDoor || block instanceof BlockLadder
                || block instanceof BlockSign || block instanceof BlockChest
                || block instanceof BlockLeaves
                || block instanceof net.minecraft.block.BlockRailBase
                || block instanceof net.minecraft.block.BlockEnderChest
) {
            return true;
        }
        return block instanceof BlockSlab && !((BlockSlab) block).isDouble();
    }

    public static boolean canBeWaterlogged(IBlockState state) {
        return WaterloggedPlantFluid.withWaterlogged(state, true) != null;
    }

    public static boolean trapDoorCollisionStateChanged(IBlockState oldState,
            IBlockState newState) {
        if (oldState == null || newState == null
                || oldState.getBlock() != newState.getBlock()
                || !(oldState.getBlock() instanceof BlockTrapDoor)) return false;
        return !oldState.getValue(BlockTrapDoor.OPEN).equals(newState.getValue(BlockTrapDoor.OPEN))
                || !oldState.getValue(BlockTrapDoor.FACING).equals(newState.getValue(BlockTrapDoor.FACING))
                || !oldState.getValue(BlockTrapDoor.HALF).equals(newState.getValue(BlockTrapDoor.HALF));
    }

    /** Detect state changes that alter the solid collision geometry seen by fluid. */
    public static boolean fluidCollisionStateChanged(World world, BlockPos pos,
            IBlockState oldState, IBlockState newState) {
        if (world == null || pos == null || oldState == null || newState == null
                || oldState.getBlock() != newState.getBlock()) return false;
        List<AxisAlignedBB> before = new ArrayList<>();
        List<AxisAlignedBB> after = new ArrayList<>();
        AxisAlignedBB probe = new AxisAlignedBB(pos).grow(0.001D);
        oldState.addCollisionBoxToList(world, pos, probe, before, null, false);
        newState.addCollisionBoxToList(world, pos, probe, after, null, false);
        if (before.size() != after.size()) return true;
        for (int i = 0; i < before.size(); i++) {
            AxisAlignedBB a = before.get(i), b = after.get(i);
            if (Math.abs(a.minX - b.minX) > 1.0E-6D
                    || Math.abs(a.minY - b.minY) > 1.0E-6D
                    || Math.abs(a.minZ - b.minZ) > 1.0E-6D
                    || Math.abs(a.maxX - b.maxX) > 1.0E-6D
                    || Math.abs(a.maxY - b.maxY) > 1.0E-6D
                    || Math.abs(a.maxZ - b.maxZ) > 1.0E-6D) return true;
        }
        return false;
    }

    /** Match vanilla's liquid-level reads while allowing a host state without LEVEL. */
    public static Comparable<?> getLiquidLevelValue(IBlockState state, IProperty<?> property) {
        if (state == null || property == null) {
            return Integer.valueOf(0);
        }
        if (property == BlockLiquid.LEVEL
                && !state.getPropertyKeys().contains(BlockLiquid.LEVEL)) {
            return Integer.valueOf(0);
        }
        return state.getValue(property);
    }

    /** Same calculation as BlockLiquid#getDepth, usable from a mixin targeting a subclass. */
    public static int vanillaLiquidDepth(Block liquid, IBlockState state) {
        if (liquid == null || state == null
                || state.getMaterial() != liquid.getDefaultState().getMaterial()) {
            return -1;
        }
        return state.getPropertyKeys().contains(BlockLiquid.LEVEL)
                ? state.getValue(BlockLiquid.LEVEL) : -1;
    }

    public static boolean isWaterloggedStateForLiquid(IBlockState state) {
        return WaterloggedPlantFluid.isWaterlogged(state);
    }

    /** State view used by BlockFluidRenderer when sampling a waterlogged cell. */
    public static IBlockState fluidRenderState(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return isWaterlogged(world, pos) ? getExtendedState(state, world, pos) : state;
    }

    /** Material view used by BlockFluidRenderer for contained fluid states. */
    public static Material renderedFluidMaterial(IBlockState state) {
        if (!isWaterlogged(state)) {
            return state.getMaterial();
        }
        Fluid fluid = WaterloggedBlockApi.getContainedFluidFromState(state);
        IBlockState fluidState = sourceState(fluid == null ? FluidRegistry.WATER : fluid);
        return fluidState.getMaterial();
    }

    /**
     * Match the upstream replacement-material hook used by Block and fences.
     * Custom waterloggable states may expose a different material while wet;
     * replacement and connection checks must inspect the dry host state.
     */
    public static Material blockReplacementMaterial(IBlockState state) {
        if (state == null) {
            return Material.AIR;
        }
        IBlockState dry = isWaterlogged(state) ? withWaterlogged(state, false) : null;
        return dry == null ? state.getMaterial() : dry.getMaterial();
    }

    private static Fluid getContainedFluidFromState(IBlockState state) {
        if (state instanceof net.minecraftforge.common.property.IExtendedBlockState) {
            String name = ((net.minecraftforge.common.property.IExtendedBlockState) state)
                    .getValue(WaterloggedPlantFluid.CONTAINED_FLUID);
            if (name != null && !name.isEmpty()) {
                return FluidRegistry.getFluid(name);
            }
        }
        return FluidRegistry.WATER;
    }

    /** Make ordinary liquid faces share the face-visibility rule with contained fluid. */
    public static boolean fluidSideVisible(boolean original, IBlockState state,
            IBlockAccess world, BlockPos pos, EnumFacing side) {
        Fluid fluid = getFluidForBlock(state.getBlock());
        return original && !(fluid != null && containsFluid(world, pos.offset(side), fluid));
    }

    /** Return the vanilla depth represented by a waterlogged state for this liquid. */
    public static int waterloggedLiquidDepth(Block liquid) {
        return liquid != null && liquid.getDefaultState().getMaterial() == Material.WATER ? 0 : -1;
    }

    /** Resolve a neighbouring waterlogged cell's stored flow depth for a liquid update. */
    public static int waterloggedLiquidDepth(int depth, Block liquid, IBlockAccess world,
            BlockPos pos) {
        if (liquid == null || world == null || pos == null
                || !isWaterlogged(world, pos)) {
            return depth;
        }
        Fluid liquidFluid = getFluidForBlock(liquid);
        return liquidFluid != null && getContainedFluid(world, pos) == liquidFluid
                ? FFDStoredFluidStates.level(world, pos) : -1;
    }

    public static boolean isContainedFluidSource(Block liquid, IBlockAccess world, BlockPos pos) {
        if (liquid == null || world == null || pos == null
                || !isWaterlogged(world, pos)) {
            return false;
        }
        Fluid liquidFluid = getFluidForBlock(liquid);
        return liquidFluid != null && getSourceFluid(world, pos) == liquidFluid;
    }

    public static boolean hasContainedFluidAlongDensity(Block liquid, IBlockAccess world,
            BlockPos pos, int densityDirection) {
        if (liquid == null || world == null || pos == null) {
            return false;
        }
        BlockPos neighbor = pos.down(densityDirection);
        return isWaterlogged(world, neighbor)
                && getContainedFluid(world, neighbor) == getFluidForBlock(liquid);
    }

    public static boolean protectContainedHost(IBlockAccess world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return isWaterlogged(world, pos)
                || WaterloggedConfig.isAdditionalWaterloggingBlock(world,
                        world.getBlockState(pos).getBlock());
    }

    /** Return whether a dry waterloggable block presents a full horizontal collision barrier. */
    public static boolean isFluidCollisionBlocked(World world, BlockPos pos, IBlockState state) {
        if (world == null || pos == null || state == null) {
            return false;
        }
        if (state.getMaterial() == Material.PORTAL
                || state.getMaterial() == Material.STRUCTURE_VOID) {
            return true;
        }
        List<AxisAlignedBB> boxes = new ArrayList<>();
        state.addCollisionBoxToList(world, pos, new AxisAlignedBB(pos).grow(0.001D),
                boxes, null, false);
        for (AxisAlignedBB box : boxes) {
            if (box.minX <= pos.getX() + 0.001D
                    && box.maxX >= pos.getX() + 0.999D
                    && box.minZ <= pos.getZ() + 0.001D
                    && box.maxZ >= pos.getZ() + 0.999D) {
                return true;
            }
        }
        return false;
    }

    /** Checks the actual contact point on the face through which fluid moves. */
    public static boolean isFluidCollisionBlocked(World world, BlockPos pos, IBlockState state,
            EnumFacing direction) {
        if (world == null || pos == null || state == null || direction == null) return false;
        if (state.getMaterial() == Material.PORTAL || state.getMaterial() == Material.STRUCTURE_VOID) {
            return true;
        }
        List<AxisAlignedBB> boxes = new ArrayList<>();
        state.addCollisionBoxToList(world, pos, new AxisAlignedBB(pos).grow(0.001D), boxes, null, false);
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        if (direction == EnumFacing.WEST) x = pos.getX() + 0.001D;
        else if (direction == EnumFacing.EAST) x = pos.getX() + 0.999D;
        else if (direction == EnumFacing.DOWN) y = pos.getY() + 0.001D;
        else if (direction == EnumFacing.UP) y = pos.getY() + 0.999D;
        else if (direction == EnumFacing.NORTH) z = pos.getZ() + 0.001D;
        else if (direction == EnumFacing.SOUTH) z = pos.getZ() + 0.999D;
        for (AxisAlignedBB box : boxes) {
            if (box.minX <= x && x <= box.maxX && box.minY <= y && y <= box.maxY
                    && box.minZ <= z && z <= box.maxZ) return true;
        }
        return false;
    }

    public static boolean isWaterloggedFlowOccluded(Block liquid, World world,
            BlockPos source, BlockPos neighbor) {
        if (liquid == null || world == null || source == null || neighbor == null) {
            return false;
        }
        Fluid liquidFluid = getFluidForBlock(liquid);
        if (liquidFluid == null || !isWaterlogged(world, neighbor)
                || getContainedFluid(world, neighbor) != liquidFluid) {
            return false;
        }
        EnumFacing side = EnumFacing.getFacingFromVector(
                neighbor.getX() - source.getX(),
                neighbor.getY() - source.getY(),
                neighbor.getZ() - source.getZ());
        return !WaterloggedPlantFluid.canPassThrough(world, source, neighbor, side);
    }

    public static int waterloggedDepthAbove(int depth, Block liquid, World world, BlockPos pos) {
        if (world == null || pos == null) {
            return depth;
        }
        depth = waterloggedLiquidDepth(depth, liquid, world, pos.up());
        return depth >= 0 && isWaterloggedFlowOccluded(liquid, world, pos, pos.up())
                ? -1 : depth;
    }

    public static boolean isWaterlogged(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        // A saved contained-fluid record must win during chunk/client
        // reconstruction: vanilla may expose the host palette as dry for a
        // few ticks before the persisted side state is replayed.  Once the
        // record is removed by the authoritative dry transition, the listed
        // host state becomes the fallback exactly like BlockGrass.snowy.
        if (FFDStoredFluidStates.has(world, pos)) {
            return true;
        }
        if (hasUniversalWaterloggedProperty(state)
                || state.getBlock() instanceof IWaterloggableBlock
                || withWaterlogged(state, true) != null) {
            return isWaterlogged(state);
        }
        return false;
    }

    /** Server-side predicate used by removal and replacement paths. */
    public static boolean isConfirmedWaterlogged(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (FFDStoredFluidStates.has(world, pos)) return true;
        if (isWaterlogged(state)) return true;
        if (withWaterlogged(state, true) != null) return false;
        return false;
    }

    public static boolean containsWater(IBlockState state) {
        return WaterloggedPlantFluid.containsWater(state);
    }

    public static boolean containsWater(IBlockAccess world, BlockPos pos) {
        return getContainedFluid(world, pos) == FluidRegistry.WATER;
    }

    public static boolean isWaterSource(IBlockState state) {
        return WaterloggedPlantFluid.isSourceWater(state);
    }

    public static boolean isWaterSource(IBlockAccess world, BlockPos pos) {
        return getSourceFluid(world, pos) == FluidRegistry.WATER;
    }

    public static IBlockState withWaterlogged(IBlockState state, boolean waterlogged) {
        return WaterloggedPlantFluid.withWaterlogged(state, waterlogged);
    }

    public static boolean setContainedFluid(World world, BlockPos pos, IBlockState state,
                                            Fluid fluid, int flags) {
        if (!WaterloggedConfig.isFluidAllowed(world, fluid) || isWaterlogged(world, pos)) {
            return false;
        }
        IBlockState wetState = withWaterlogged(state, true);
        if (wetState == null && WaterloggedConfig.isAdditionalWaterloggingBlock(world, state.getBlock())) {
            FFDStoredFluidStates.set(world, pos, sourceState(fluid));
            world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
            world.scheduleUpdate(pos, state.getBlock(), fluidTickRate(world, fluid));
            return true;
        }
        if (wetState == null) {
            return false;
        }
        if (!world.setBlockState(pos, wetState, flags)) {
            return false;
        }
        recordContainedFluid(world, pos, fluid);
        world.scheduleUpdate(pos, wetState.getBlock(), fluidTickRate(world, fluid));
        return true;
    }

    public static boolean isInternalStateWrite() {
        return Boolean.TRUE.equals(INTERNAL_STATE_WRITE.get());
    }

    public static boolean setBlockStateWithoutWaterloggingHook(
            World world, BlockPos pos, IBlockState state, int flags) {
        boolean previous = isInternalStateWrite();
        INTERNAL_STATE_WRITE.set(Boolean.TRUE);
        try {
            return world.setBlockState(pos, state, flags);
        } finally {
            if (previous) {
                INTERNAL_STATE_WRITE.set(Boolean.TRUE);
            } else {
                INTERNAL_STATE_WRITE.remove();
            }
        }
    }

    public static boolean setDryState(World world, BlockPos pos, IBlockState state, int flags) {
        IBlockState saved = FFDStoredFluidStates.getHostState(world, pos);
        IBlockState live = world.getBlockState(pos);
        boolean recordedWet = FFDStoredFluidStates.has(world, pos);
        boolean listedWet = isWaterlogged(live);
        if (!recordedWet && !listedWet && saved == null) return false;
        IBlockState hostState = saved != null ? saved : live;
        if (hostState.getBlock() != live.getBlock()) hostState = state;
        IBlockState dryState = withWaterlogged(hostState, false);
        if (dryState == null) return false;
        IBlockState current = world.getBlockState(pos);
        if (!dryState.equals(current)
                && !setBlockStateWithoutWaterloggingHook(world, pos, dryState, flags)) {
            return false;
        }
        if (!dryState.equals(world.getBlockState(pos))) return false;
        FFDStoredFluidStates.remove(world, pos);
        if (!world.isRemote) {
            world.notifyBlockUpdate(pos, current, dryState, 3);
            world.notifyNeighborsOfStateChange(pos, dryState.getBlock(), false);
        }
        FFDStoredFluidStates.sync(world, pos);
        if (!world.isRemote) {
            world.getChunk(pos).markDirty();
        }
        return true;
    }

    /** Match the upstream removal path: harvest the host first, then replace
     * a waterlogged host with its contained fluid instead of air. */
    public static boolean removeWaterloggedBlockByPlayer(World world, BlockPos pos,
                                                         IBlockState removedState) {
        return setBlockStateAfterRemoval(world, pos, Blocks.AIR.getDefaultState(),
                world.isRemote ? 11 : 3, removedState);
    }

    public static boolean setBlockStateAfterRemoval(World world, BlockPos pos,
                                                     IBlockState replacement, int flags,
                                                     IBlockState removedState) {
        if (world == null || pos == null) {
            return false;
        }
        boolean wasWaterlogged = world.isRemote
                ? isWaterlogged(world, pos) : isConfirmedWaterlogged(world, pos);
        IBlockState next = wasWaterlogged
                ? getReplacementState(world, pos, removedState) : replacement;
        if (next == null) {
            return false;
        }
        // A static vanilla source has no pending update after its host is
        // harvested. Restore the dynamic form so it can spread on the next
        // fluid tick, just as a newly placed source does.
        if (wasWaterlogged && next.getBlock() == Blocks.WATER) {
            next = Blocks.FLOWING_WATER.getDefaultState()
                    .withProperty(BlockLiquid.LEVEL, next.getValue(BlockLiquid.LEVEL));
        } else if (wasWaterlogged && next.getBlock() == Blocks.LAVA) {
            next = Blocks.FLOWING_LAVA.getDefaultState()
                    .withProperty(BlockLiquid.LEVEL, next.getValue(BlockLiquid.LEVEL));
        }
        boolean changed = setBlockStateWithoutWaterloggingHook(world, pos, next, flags);
        if (changed) {
            recordContainedFluid(world, pos, null);
            Fluid restored = getFluidForBlock(next.getBlock());
            if (!world.isRemote && restored != null) {
                world.scheduleUpdate(pos, next.getBlock(), 1);
                world.notifyNeighborsOfStateChange(pos, next.getBlock(), false);
            }
            if (world.isRemote) {
                world.markBlockRangeForRenderUpdate(pos.add(-1, -1, -1),
                        pos.add(1, 1, 1));
            }
        }
        return changed;
    }

    /**
     * Apply the upstream removal rule used by ItemShears.  A sheared
     * waterlogged host is replaced by its contained fluid state, while a dry
     * host keeps vanilla's requested replacement state.
     */
    public static boolean setBlockStateAfterShearing(World world, BlockPos pos,
                                                       IBlockState replacement, int flags) {
        if (world == null || pos == null) {
            return false;
        }

        IBlockState removed = world.getBlockState(pos);
        return setBlockStateAfterRemoval(world, pos, replacement, flags, removed);
    }

    public static void runBlockTick(Block block, World world, BlockPos pos,
                                    IBlockState state, Random random) {
        if (!world.isRemote && getFluidForBlock(block) != null) {
            boolean mixed = WaterloggedPlantFluid.tryMixFluidNeighbors(world, pos, state);
            if (mixed && getFluidForBlock(world.getBlockState(pos).getBlock()) == null) {
                return;
            }
        }
        block.updateTick(world, pos, state, random);
        IBlockState current = world.getBlockState(pos);
        if (isWaterlogged(world, pos)) {
            updateTick(world, pos, current);
        } else {
            WaterloggedPlantFluid.tryFormWaterSource(world, pos, current);
        }
    }

    public static void recordContainedFluid(World world, BlockPos pos, Fluid fluid) {
        if (world == null || pos == null) {
            return;
        }
        if (fluid == null) {
            // Keep removal in one lifecycle entry point.  FFDStoredFluidStates
            // also clears the type fallback, so a dry host cannot retain a
            // second independent fluid record.
            FFDStoredFluidStates.remove(world, pos);
            return;
        }
        // Persist both the concrete fluid state (including level) and the
        // fluid identity.  Water is deliberately included in both tables;
        // it is an existing vanilla fluid, not a block to recreate on reload.
        FFDStoredFluidStates.set(world, pos, sourceState(fluid));
        FFDFluidloggedData.set(world, pos, fluid);
    }

    public static Fluid getContainedFluid(IBlockAccess world, BlockPos pos) {
        IBlockState storedState = FFDStoredFluidStates.get(world, pos);
        if (storedState != null) {
            return getFluidForBlock(storedState.getBlock());
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(state)) {
            Fluid stored = FFDFluidloggedData.get(world, pos);
            return stored == null ? FluidRegistry.WATER : stored;
        }
        return getFluidForBlock(state.getBlock());
    }

    public static boolean containsFluid(IBlockAccess world, BlockPos pos, Fluid fluid) {
        return fluid != null && getContainedFluid(world, pos) == fluid;
    }

    public static boolean isFluidSource(IBlockAccess world, BlockPos pos) {
        return getSourceFluid(world, pos) != null;
    }

    public static Fluid getSourceFluid(IBlockAccess world, BlockPos pos) {
        IBlockState stored = FFDStoredFluidStates.get(world, pos);
        if (stored != null) {
            return WaterloggedPlantFluid.isSourceFluid(stored)
                    ? getFluidForBlock(stored.getBlock()) : null;
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(state)) {
            return getContainedFluid(world, pos);
        }
        Fluid fluid = getFluidForBlock(state.getBlock());
        return fluid != null && WaterloggedPlantFluid.isSourceFluid(state) ? fluid : null;
    }

    public static IBlockState getFluidState(IBlockState state) {
        return WaterloggedPlantFluid.getFluidState(state);
    }

    public static IBlockState getFluidState(IBlockAccess world, BlockPos pos) {
        IBlockState stored = FFDStoredFluidStates.get(world, pos);
        if (stored != null) {
            return stored;
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(state)) {
            return sourceState(getContainedFluid(world, pos));
        }
        if (getFluidForBlock(state.getBlock()) != null) return state;
        return getFluidState(state);
    }

    public static IBlockState getReplacementState(IBlockState state) {
        return isWaterlogged(state) ? Blocks.WATER.getDefaultState()
                : Blocks.AIR.getDefaultState();
    }

    public static IBlockState getReplacementState(IBlockAccess world, BlockPos pos,
                                                   IBlockState state) {
        return isWaterlogged(world, pos) ? getFluidState(world, pos)
                : Blocks.AIR.getDefaultState();
    }

    public static boolean restoreFluid(World world, BlockPos pos, IBlockState state, int flags) {
        IBlockState replacement = getReplacementState(world, pos, state);
        boolean changed = world.setBlockState(pos, replacement, flags);
        if (changed) {
            FFDFluidloggedData.set(world, pos, null);
        }
        return changed;
    }

    public static Vec3d modifyAcceleration(World world, BlockPos pos, Entity entity, Vec3d motion) {
        IBlockState state = world.getBlockState(pos);
        if (!isWaterlogged(world, pos)) {
            return containsWater(state)
                    ? Blocks.WATER.modifyAcceleration(world, pos, entity, motion) : motion;
        }
        int currentDepth = getRenderedWaterDepth(world, pos);
        Vec3d flow = Vec3d.ZERO;
        for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
            BlockPos target = pos.offset(direction);
            IBlockState targetState = world.getBlockState(target);
            int targetDepth = getRenderedWaterDepth(world, target);
            if (targetDepth >= 0) {
                flow = addFlow(flow, direction, targetDepth - currentDepth);
            } else if (!targetState.getMaterial().blocksMovement()) {
                int lowerDepth = getRenderedWaterDepth(world, target.down());
                if (lowerDepth >= 0) {
                    flow = addFlow(flow, direction, lowerDepth - (currentDepth - 8));
                }
            }
        }
        return motion.add(flow.normalize());
    }

    private static int getRenderedWaterDepth(IBlockState state) {
        if (isWaterlogged(state)) {
            return 0;
        }
        if (state == null || state.getMaterial() != net.minecraft.block.material.Material.WATER
                || !state.getPropertyKeys().contains(BlockLiquid.LEVEL)) {
            return -1;
        }
        int depth = state.getValue(BlockLiquid.LEVEL);
        return depth >= 8 ? 0 : depth;
    }

    /** Resolve a rendered liquid depth from the world, including stored flow
     * levels attached to a waterlogged host. */
    private static int getRenderedWaterDepth(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return -1;
        }
        IBlockState stored = FFDStoredFluidStates.get(world, pos);
        if (stored != null) {
            return getRenderedWaterDepth(stored);
        }
        IBlockState state = world.getBlockState(pos);
        if (isWaterlogged(world, pos)
                && getContainedFluid(world, pos) == FluidRegistry.WATER) {
            return 0;
        }
        return getRenderedWaterDepth(state);
    }

    private static Vec3d addFlow(Vec3d flow, EnumFacing direction, int depthDifference) {
        return flow.add(new Vec3d(direction.getXOffset() * depthDifference,
                direction.getYOffset() * depthDifference,
                direction.getZOffset() * depthDifference));
    }

    public static IUnlistedProperty<?>[] extendedProperties() {
        return WaterloggedPlantFluid.extendedProperties();
    }

    public static IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return WaterloggedPlantFluid.getExtendedState(state, world, pos);
    }

    public static void onBlockAdded(World world, BlockPos pos, Block block) {
        WaterloggedPlantFluid.onBlockAdded(world, pos, block);
    }

    public static void onNeighborChanged(World world, BlockPos pos, Block block) {
        WaterloggedPlantFluid.onNeighborChanged(world, pos, block);
    }

    public static void updateTick(World world, BlockPos pos, IBlockState state) {
        WaterloggedPlantFluid.updateTick(world, pos, state);
    }

    /** Refresh only the contained water state of a trapdoor after its own state changes. */
    public static void refreshTrapDoorContainedFluid(World world, BlockPos pos,
            IBlockState state) {
        if (world == null || world.isRemote || state == null
                || !(state.getBlock() instanceof BlockTrapDoor)
                || !isWaterlogged(world, pos)) return;
        Fluid fluid = getContainedFluid(world, pos);
        if (fluid == null) return;
        IBlockState source = sourceState(fluid);
        FFDStoredFluidStates.set(world, pos, source);
        FFDFluidloggedData.set(world, pos, fluid);
        world.scheduleUpdate(pos, state.getBlock(), 1);
        world.notifyNeighborsOfStateChange(pos, state.getBlock(), false);
        world.notifyBlockUpdate(pos, state, state, 3);
        FFDStoredFluidStates.sync(world, pos);
    }

    public static IBlockState sourceState(Fluid fluid) {
        if (fluid == null || fluid.getBlock() == null) {
            return Blocks.AIR.getDefaultState();
        }
        try {
            return fluid.getBlock().getStateFromMeta(0);
        } catch (RuntimeException ignored) {
            return fluid.getBlock().getDefaultState();
        }
    }

    public static Fluid getFluidForBlock(Block block) {
        if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
            return FluidRegistry.WATER;
        }
        if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
            return FluidRegistry.LAVA;
        }
        return FluidRegistry.lookupFluidForBlock(block);
    }

    public static int fluidTickRate(World world, Fluid fluid) {
        if (fluid == null || fluid.getBlock() == null) {
            return 5;
        }
        return Math.max(1, fluid.getBlock().tickRate(world));
    }

    private static final class UniversalWaterloggedProperty extends PropertyBool {
        private UniversalWaterloggedProperty() {
            super("waterlogged");
        }

        @Override
        public Collection<Boolean> getAllowedValues() {
            return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
        }
    }
}
