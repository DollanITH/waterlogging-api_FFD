package dev.xy177.waterlogged.common.fluid;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.network.WaterloggedNetwork;

public final class FFDStoredFluidStates {
    public static final String TAG = "FFDContainedStates";
    private static final String HOST_STATE = "HostState";
    private static final String WET = "Wet";
    private static final Map<World, Map<Long, Map<Long, NBTTagCompound>>> DATA = new WeakHashMap<>();
    private static final Map<Chunk, Map<Long, NBTTagCompound>> UNLOADED = new WeakHashMap<>();
    private static final Map<Chunk, NBTTagCompound> PENDING_LOAD = new WeakHashMap<>();
    private static final Set<Chunk> RESTORING =
            Collections.newSetFromMap(new WeakHashMap<Chunk, Boolean>());
    private static final Map<World, Set<Long>> DIRTY = new WeakHashMap<>();
    private static final Set<Block> RENDER_HOSTS =
            Collections.newSetFromMap(new ConcurrentHashMap<Block, Boolean>());
    /** Client chunk packets can overwrite the host palette after the side-state packet. */
    private static final Map<World, Map<Long, Integer>> CLIENT_REPLAY = new WeakHashMap<>();

    private FFDStoredFluidStates() {}

    private static long key(int x, int z) {
        return x & 0xffffffffL | (long) z << 32;
    }

    @Nullable
    public static synchronized IBlockState get(IBlockAccess access, BlockPos pos) {
        World world = FFDFluidloggedData.resolveWorld(access);
        if (world == null) return null;
        Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.get(world);
        Map<Long, NBTTagCompound> data = chunks == null ? null
                : chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        NBTTagCompound entry = data == null ? null : data.get(pos.toLong());
        if (entry == null) return null;
        // The universal carrier can decode a saved wet palette entry through a
        // legacy/meta alias during chunk reconstruction.  Requiring the exact
        // registry-name string here discarded valid diffusion records before
        // the block state was fully restored, which made water reappear only
        // after breaking the host.  Validate the live host's capability instead
        // of rejecting the persisted record on that transient name mismatch.
        Block liveHost = access.getBlockState(pos).getBlock();
        if (!mayContain(liveHost)
                && !WaterloggedBlockApi.hasUniversalWaterloggedProperty(
                        access.getBlockState(pos))) {
            return null;
        }
        try {
            IBlockState fluid = NBTUtil.readBlockState(entry.getCompoundTag("State"));
            return WaterloggedBlockApi.getFluidForBlock(fluid.getBlock()) == null
                    ? null : fluid;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public static boolean has(IBlockAccess access, BlockPos pos) {
        IBlockState state = get(access, pos);
        return state != null && state.getBlock() != net.minecraft.init.Blocks.AIR;
    }

    @Nullable
    public static synchronized IBlockState getHostState(IBlockAccess access, BlockPos pos) {
        World world = FFDFluidloggedData.resolveWorld(access);
        if (world == null) return null;
        Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.get(world);
        Map<Long, NBTTagCompound> data = chunks == null ? null
                : chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        NBTTagCompound entry = data == null ? null : data.get(pos.toLong());
        return readWetHostState(entry);
    }

    public static int level(IBlockAccess access, BlockPos pos) {
        IBlockState state = get(access, pos);
        if (state == null) return 0;
        if (state.getPropertyKeys().contains(net.minecraft.block.BlockLiquid.LEVEL))
            return state.getValue(net.minecraft.block.BlockLiquid.LEVEL);
        if (state.getPropertyKeys().contains(net.minecraftforge.fluids.BlockFluidBase.LEVEL))
            return state.getValue(net.minecraftforge.fluids.BlockFluidBase.LEVEL);
        return state.getBlock().getMetaFromState(state);
    }

    public static IBlockState changed(IBlockState old, Chunk chunk, BlockPos pos, IBlockState next) {
        if (RESTORING.contains(chunk)) return old;
        if (old != null && !WaterloggedBlockApi.isInternalStateWrite()
                && (old.getBlock() != next.getBlock()
                || WaterloggedBlockApi.isWaterlogged(old) && !WaterloggedBlockApi.isWaterlogged(next))) {
            remove(chunk.getWorld(), pos);
        }
        // WorldEdit and other bulk writers often bypass World#setBlockState
        // hooks and write directly through Chunk#setBlockState. Materialize
        // or refresh the paired fluid record after stale old-host data clears.
        if (!chunk.getWorld().isRemote && WaterloggedBlockApi.isWaterlogged(next)
                && !WaterloggedBlockApi.isInternalStateWrite()) {
            Fluid fluid = FFDFluidloggedData.get(chunk.getWorld(), pos);
            if (fluid == null) fluid = net.minecraftforge.fluids.FluidRegistry.WATER;
            if (dev.xy177.waterlogged.common.WaterloggedConfig.isFluidAllowed(
                    chunk.getWorld(), fluid)) {
                put(chunk, pos, WaterloggedBlockApi.sourceState(fluid));
                sync(chunk.getWorld(), pos);
            }
        }
        if (old != null && !chunk.getWorld().isRemote
                && WaterloggedBlockApi.getFluidForBlock(old.getBlock()) != null
                && dev.xy177.waterlogged.common.block.WaterloggedPlantFluid.isSourceFluid(old)
                && dev.xy177.waterlogged.common.WaterloggedConfig.isAdditionalWaterloggingBlock(next.getBlock())
                && dev.xy177.waterlogged.common.WaterloggedConfig.isFluidAllowed(
                        WaterloggedBlockApi.getFluidForBlock(old.getBlock()))) {
            put(chunk, pos, old);
            sync(chunk.getWorld(), pos);
            chunk.getWorld().scheduleUpdate(pos, next.getBlock(),
                    WaterloggedBlockApi.fluidTickRate(chunk.getWorld(),
                            WaterloggedBlockApi.getFluidForBlock(old.getBlock())));
        }
        return old;
    }

    public static boolean mayContain(Block block) {
        return RENDER_HOSTS.contains(block)
                || dev.xy177.waterlogged.common.WaterloggedConfig.isAdditionalWaterloggingBlock(block);
    }

    public static synchronized void put(Chunk chunk, BlockPos pos, IBlockState fluid) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setLong("Pos", pos.toLong());
        Block host = chunk.getBlockState(pos).getBlock();
        entry.setString("Host", String.valueOf(host.getRegistryName()));
        IBlockState hostState = chunk.getBlockState(pos);
        IBlockState wetHost = WaterloggedBlockApi.withWaterlogged(hostState, true);
        if (wetHost != null) hostState = wetHost;
        entry.setBoolean(WET, true);
        entry.setTag(HOST_STATE, NBTUtil.writeBlockState(new NBTTagCompound(), hostState));
        entry.setTag("State", NBTUtil.writeBlockState(new NBTTagCompound(), fluid));
        DATA.computeIfAbsent(chunk.getWorld(), ignored -> new HashMap<>())
                .computeIfAbsent(key(chunk.x, chunk.z), ignored -> new LinkedHashMap<>())
                .put(pos.toLong(), entry);
        RENDER_HOSTS.add(host);
        chunk.markDirty();
        FFDFluidloggedData.set(chunk.getWorld(), pos,
                WaterloggedBlockApi.getFluidForBlock(fluid.getBlock()));
    }

    public static void set(World world, BlockPos pos, IBlockState fluid) {
        put(world.getChunk(pos), pos, fluid);
        sync(world, pos);
    }

    public static synchronized void remove(World world, BlockPos pos) {
        Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.get(world);
        Map<Long, NBTTagCompound> data = chunks == null ? null
                : chunks.get(key(pos.getX() >> 4, pos.getZ() >> 4));
        boolean removed = data != null && data.remove(pos.toLong()) != null;
        // Clear the type fallback even when the state table was already empty;
        // stale type-only records must never resurrect a dry host on reload.
        FFDFluidloggedData.set(world, pos, null);
        if (removed) {
            if (world.isBlockLoaded(pos)) {
                world.getChunk(pos).markDirty();
                sync(world, pos);
            }
        }
    }

    public static synchronized void sync(World world, BlockPos pos) {
        if (!world.isRemote) {
            DIRTY.computeIfAbsent(world, ignored -> new java.util.HashSet<>())
                    .add(key(pos.getX() >> 4, pos.getZ() >> 4));
            world.checkLight(pos);
        } else {
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }

    @SubscribeEvent
    public static synchronized void flush(net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END
                || event.world.isRemote) return;
        Set<Long> dirty = DIRTY.remove(event.world);
        if (dirty != null) for (long key : dirty)
            WaterloggedNetwork.sendStoredFluidChunk(event.world, (int) key, (int) (key >> 32),
                    snapshot(event.world, (int) key, (int) (key >> 32)));
    }

    public static synchronized NBTTagCompound snapshot(World world, int x, int z) {
        Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.get(world);
        return encode(chunks == null ? null : chunks.get(key(x, z)));
    }

    private static NBTTagCompound encode(Map<Long, NBTTagCompound> entries) {
        NBTTagList list = new NBTTagList();
        if (entries != null) for (NBTTagCompound entry : entries.values()) list.appendTag(entry.copy());
        NBTTagCompound result = new NBTTagCompound();
        result.setTag("Entries", list);
        return result;
    }

    private static synchronized void read(World world, int x, int z, NBTTagCompound root, boolean replace) {
        Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.computeIfAbsent(world, ignored -> new HashMap<>());
        Map<Long, NBTTagCompound> entries = replace ? new LinkedHashMap<>()
                : chunks.getOrDefault(key(x, z), new LinkedHashMap<>());
        NBTTagList list = root.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            BlockPos pos = BlockPos.fromLong(entry.getLong("Pos"));
            if ((pos.getX() >> 4) != x || (pos.getZ() >> 4) != z) continue;
            try {
                IBlockState fluid = NBTUtil.readBlockState(entry.getCompoundTag("State"));
                if (WaterloggedBlockApi.getFluidForBlock(fluid.getBlock()) == null) continue;
                entries.put(pos.toLong(), entry.copy());
                Block block = Block.getBlockFromName(entry.getString("Host"));
                if (block != null) RENDER_HOSTS.add(block);
                FFDFluidloggedData.set(world, pos,
                        WaterloggedBlockApi.getFluidForBlock(fluid.getBlock()));
            } catch (RuntimeException ignored) {
                // Ignore malformed entries instead of restoring a wet host
                // with no concrete fluid state behind it.
            }
        }
        chunks.put(key(x, z), entries);
        // The identity table is a fallback for the concrete state table.  A
        // replace read (including an empty client packet) must remove any
        // older type-only records in this chunk immediately.
        FFDFluidloggedData.retainChunk(world, x, z, entries.keySet());
    }

    public static void receive(World world, int x, int z, NBTTagCompound root, boolean replace) {
        if (root.hasKey("Policy", 10))
            dev.xy177.waterlogged.common.WaterloggedConfig.receiveFluidPolicy(world, root.getCompoundTag("Policy"));
        if (!root.hasKey("Entries", 9)) {
            if (replace) {
                read(world, x, z, new NBTTagCompound(), true);
                world.markBlockRangeForRenderUpdate(x << 4, -64, z << 4,
                        (x << 4) + 15, 319, (z << 4) + 15);
            }
            return;
        }
        if (world.isRemote && replace) {
            clearRemovedClientHosts(world, x, z, root);
        }
        read(world, x, z, root, replace);
        // The side-state packet arrives separately from vanilla's chunk block
        // palette.  Reconcile the client host state immediately; otherwise the
        // renderer sees contained water while F3 still reports
        // waterlogged=false until an unrelated block update occurs.
        if (world.isRemote) {
            NBTTagList list = root.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                BlockPos pos = BlockPos.fromLong(entry.getLong("Pos"));
                IBlockState host = world.getBlockState(pos);
                IBlockState savedHost = readWetHostState(entry);
                if (savedHost != null && savedHost.getBlock() == host.getBlock()
                        && !savedHost.equals(host)) {
                    WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(world, pos,
                            savedHost, 3);
                    host = savedHost;
                }
                if (!WaterloggedBlockApi.isWaterlogged(host)) {
                    IBlockState wet = WaterloggedBlockApi.withWaterlogged(host, true);
                    if (wet != null) {
                        WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(world, pos, wet, 3);
                    }
                }
            }
        }
        if (world.isRemote) {
            synchronized (FFDStoredFluidStates.class) {
                CLIENT_REPLAY.computeIfAbsent(world, ignored -> new LinkedHashMap<>())
                        .put(key(x, z), 20);
            }
        }
        world.markBlockRangeForRenderUpdate(x << 4, -64, z << 4, (x << 4) + 15, 319,
                (z << 4) + 15);
    }

    /** A replace packet can remove a previously wet entry.  Clear the client
     * listed property at the same time; waiting for a chunk reload left F3
     * and the renderer showing waterlogged=true indefinitely. */
    private static synchronized void clearRemovedClientHosts(World world, int x, int z,
            NBTTagCompound root) {
        Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.get(world);
        Map<Long, NBTTagCompound> previous = chunks == null ? null : chunks.get(key(x, z));
        if (previous == null) return;
        Set<Long> incoming = new java.util.HashSet<>();
        NBTTagList list = root.getTagList("Entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            BlockPos pos = BlockPos.fromLong(entry.getLong("Pos"));
            if ((pos.getX() >> 4) == x && (pos.getZ() >> 4) == z) incoming.add(pos.toLong());
        }
        for (Long packed : new java.util.ArrayList<>(previous.keySet())) {
            if (incoming.contains(packed)) continue;
            BlockPos pos = BlockPos.fromLong(packed.longValue());
            IBlockState host = world.getBlockState(pos);
            IBlockState dry = WaterloggedBlockApi.withWaterlogged(host, false);
            if (dry != null && !dry.equals(host)) {
                WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(world, pos, dry, 3);
            }
        }
    }

    /** Reapply persisted host states after vanilla client chunk data has settled. */
    @SubscribeEvent
    public static void replayClientStates(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        World world = dev.xy177.waterlogged.WaterloggedMod.proxy.getClientWorld();
        if (world == null) return;
        Map<Long, Integer> pending;
        synchronized (FFDStoredFluidStates.class) {
            pending = CLIENT_REPLAY.get(world);
            if (pending == null || pending.isEmpty()) return;
            pending = new LinkedHashMap<>(pending);
        }
        for (Map.Entry<Long, Integer> task : pending.entrySet()) {
            Map<Long, NBTTagCompound> entries = DATA.get(world) == null ? null
                    : DATA.get(world).get(task.getKey());
            if (entries != null) for (long packed : entries.keySet()) {
                BlockPos pos = BlockPos.fromLong(packed);
                if (!world.isBlockLoaded(pos)) continue;
                IBlockState host = world.getBlockState(pos);
                IBlockState savedHost = readWetHostState(entries.get(packed));
                if (savedHost != null && savedHost.getBlock() == host.getBlock()
                        && !savedHost.equals(host)) {
                    WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(world, pos,
                            savedHost, 3);
                    host = savedHost;
                }
                IBlockState wet = WaterloggedBlockApi.withWaterlogged(host, true);
                if (wet != null && !WaterloggedBlockApi.isWaterlogged(host)) {
                    WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(world, pos, wet, 3);
                    world.markBlockRangeForRenderUpdate(pos, pos);
                }
            }
            int left = task.getValue() - 1;
            if (left <= 0 && entries != null) {
                FFDFluidloggedData.retainChunk(world, (int) task.getKey().longValue(),
                        (int) (task.getKey().longValue() >> 32), entries.keySet());
            }
            synchronized (FFDStoredFluidStates.class) {
                Map<Long, Integer> live = CLIENT_REPLAY.get(world);
                if (live != null) {
                    if (left <= 0) live.remove(task.getKey());
                    else live.put(task.getKey(), left);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void load(ChunkDataEvent.Load event) {
        // ChunkDataEvent.Load can fire before the chunk's block palette has been
        // replayed. Defer the side-state read until ChunkEvent.Load, otherwise
        // ChunkMixin.setBlockState treats the freshly loaded records as stale.
        PENDING_LOAD.put(event.getChunk(), event.getData().getCompoundTag(TAG).copy());
    }

    @SubscribeEvent
    public static synchronized void save(ChunkDataEvent.Save event) {
        if (event.getWorld().isRemote) return;
        Chunk chunk = event.getChunk();
        NBTTagCompound root = UNLOADED.containsKey(chunk)
                ? encode(UNLOADED.get(chunk)) : snapshot(event.getWorld(), chunk.x, chunk.z);
        event.getData().setTag(TAG, root);
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        Chunk chunk = event.getChunkInstance();
        if (chunk != null) WaterloggedNetwork.sendStoredFluidChunk(event.getPlayer(), chunk.x, chunk.z,
                snapshot(chunk.getWorld(), chunk.x, chunk.z));
    }

    @SubscribeEvent
    public static synchronized void unload(ChunkEvent.Unload event) {
        Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.get(event.getWorld());
        if (chunks != null) {
            Map<Long, NBTTagCompound> entries = chunks.remove(key(event.getChunk().x, event.getChunk().z));
            if (entries != null && !event.getWorld().isRemote) UNLOADED.put(event.getChunk(), entries);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static synchronized void reload(ChunkEvent.Load event) {
        RESTORING.add(event.getChunk());
        NBTTagCompound pending = PENDING_LOAD.remove(event.getChunk());
        if (pending != null) {
            read(event.getWorld(), event.getChunk().x, event.getChunk().z, pending, true);
        }
        Map<Long, NBTTagCompound> entries = UNLOADED.remove(event.getChunk());
        if (entries != null) DATA.computeIfAbsent(event.getWorld(), ignored -> new HashMap<>())
                .put(key(event.getChunk().x, event.getChunk().z), entries);
        Map<Long, Map<Long, NBTTagCompound>> restoredChunks = DATA.get(event.getWorld());
        Map<Long, NBTTagCompound> restored = restoredChunks == null ? null
                : restoredChunks.get(key(event.getChunk().x, event.getChunk().z));
        FFDFluidloggedData.retainChunk(event.getWorld(), event.getChunk().x,
                event.getChunk().z, restored == null
                        ? Collections.emptySet() : restored.keySet());
        if (!event.getWorld().isRemote) {
            Map<Long, Map<Long, NBTTagCompound>> chunks = DATA.get(event.getWorld());
            Map<Long, NBTTagCompound> loaded = chunks == null ? null
                    : chunks.get(key(event.getChunk().x, event.getChunk().z));
            if (loaded != null) for (long packed : loaded.keySet()) {
                BlockPos pos = BlockPos.fromLong(packed);
                IBlockState host = event.getWorld().getBlockState(pos);
                IBlockState savedHost = readWetHostState(loaded.get(packed));
                if (savedHost != null && savedHost.getBlock() == host.getBlock()
                        && !savedHost.equals(host)) {
                    WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(
                            event.getWorld(), pos, savedHost, 3);
                    host = savedHost;
                }
                // Restore the listed waterlogged property together with the
                // side-state record.  During chunk loading the palette may
                // contain the dry variant even though the saved entry is valid;
                // leaving it dry makes the fluid disappear until another block
                // update happens to touch the position.
                if (!WaterloggedBlockApi.isWaterlogged(host)) {
                    IBlockState wet = WaterloggedBlockApi.withWaterlogged(host, true);
                    if (wet != null) {
                        WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(
                                event.getWorld(), pos, wet, 3);
                        host = wet;
                    }
                }
                event.getWorld().scheduleUpdate(pos, host.getBlock(), 5);
            }
        }
        RESTORING.remove(event.getChunk());
    }

    @Nullable
    private static IBlockState readWetHostState(@Nullable NBTTagCompound entry) {
        if (entry == null || !entry.getBoolean(WET)
                || !entry.hasKey(HOST_STATE, Constants.NBT.TAG_COMPOUND)) return null;
        try {
            IBlockState state = NBTUtil.readBlockState(entry.getCompoundTag(HOST_STATE));
            return WaterloggedBlockApi.isWaterlogged(state) ? state
                    : WaterloggedBlockApi.withWaterlogged(state, true);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    @SubscribeEvent
    public static synchronized void unloadWorld(WorldEvent.Unload event) {
        DATA.remove(event.getWorld());
        DIRTY.remove(event.getWorld());
        UNLOADED.keySet().removeIf(chunk -> chunk.getWorld() == event.getWorld());
        PENDING_LOAD.keySet().removeIf(chunk -> chunk.getWorld() == event.getWorld());
        CLIENT_REPLAY.remove(event.getWorld());
    }
}
