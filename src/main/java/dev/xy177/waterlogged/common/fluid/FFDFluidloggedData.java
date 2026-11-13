package dev.xy177.waterlogged.common.fluid;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import dev.xy177.waterlogged.WaterloggedMod;
import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import dev.xy177.waterlogged.common.network.WaterloggedNetwork;

public final class FFDFluidloggedData {
    private static final String NBT_DATA = "FFDFluidloggedFluids";
    private static final String NBT_POS = "Pos";
    private static final String NBT_FLUID = "Fluid";
    private static final Object LOCK = new Object();
    private static final Map<World, Map<Long, Map<Long, String>>> WORLD_DATA =
            new WeakHashMap<>();
    private static final Map<Chunk, Map<Long, String>> UNLOADED_CHUNK_DATA =
            new WeakHashMap<>();

    private FFDFluidloggedData() {
    }

    @Nullable
    public static Fluid get(IBlockAccess access, BlockPos pos) {
        World world = resolveWorld(access);
        if (world == null) {
            return null;
        }
        String name;
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(world, false);
            Map<Long, String> chunkData = worldData == null ? null
                    : worldData.get(chunkKey(pos.getX() >> 4, pos.getZ() >> 4));
            name = chunkData == null ? null : chunkData.get(pos.toLong());
        }
        return name == null ? null : FluidRegistry.getFluid(name);
    }

    public static void set(World world, BlockPos pos, @Nullable Fluid fluid) {
        if (world == null || pos == null) {
            return;
        }
        // Keep water's identity in the fallback table as well.  The contained
        // state table carries the level/state, while this table carries the
        // fluid type; both records must exist for a persisted diffusion cell.
        String name = fluid == null ? null : fluid.getName();
        boolean changed;
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(world, name != null);
            long chunkKey = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            Map<Long, String> chunkData = worldData == null ? null : worldData.get(chunkKey);
            if (name == null) {
                changed = chunkData != null && chunkData.remove(pos.toLong()) != null;
                if (chunkData != null && chunkData.isEmpty()) {
                    worldData.remove(chunkKey);
                }
            } else {
                if (chunkData == null) {
                    chunkData = new LinkedHashMap<>();
                    worldData.put(chunkKey, chunkData);
                }
                String previous = chunkData.put(pos.toLong(), name);
                changed = !name.equals(previous);
            }
        }
        if (!changed) {
            return;
        }
        Chunk chunk = world.getChunk(pos);
        chunk.markDirty();
        if (!world.isRemote) {
            world.checkLight(pos);
            WaterloggedNetwork.sendFluidloggedUpdate(world, pos, name);
        }
    }

    public static Map<Long, String> snapshot(World world, int chunkX, int chunkZ) {
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(world, false);
            Map<Long, String> chunkData = worldData == null ? null
                    : worldData.get(chunkKey(chunkX, chunkZ));
            return chunkData == null ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(chunkData);
        }
    }

    /** Remove type-only records that have no matching contained-state entry. */
    public static void retainChunk(World world, int chunkX, int chunkZ,
                                   Set<Long> validPositions) {
        if (world == null || validPositions == null) {
            return;
        }
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(world, false);
            if (worldData == null) {
                return;
            }
            long key = chunkKey(chunkX, chunkZ);
            Map<Long, String> chunkData = worldData.get(key);
            if (chunkData == null) {
                return;
            }
            chunkData.keySet().removeIf(position -> !validPositions.contains(position));
            if (chunkData.isEmpty()) {
                worldData.remove(key);
            }
        }
    }

    @SubscribeEvent
    public static void loadChunkData(ChunkDataEvent.Load event) {
        NBTTagList list = event.getData().getTagList(NBT_DATA, Constants.NBT.TAG_COMPOUND);
        Map<Long, String> loaded = new LinkedHashMap<>();
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            String fluidName = entry.getString(NBT_FLUID);
            if (FluidRegistry.getFluid(fluidName) != null) {
                loaded.put(entry.getLong(NBT_POS), fluidName);
            }
        }
        putChunk(event.getWorld(), event.getChunk().x, event.getChunk().z, loaded);
    }

    @SubscribeEvent
    public static void saveChunkData(ChunkDataEvent.Save event) {
        if (event.getWorld().isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        Map<Long, String> stored;
        synchronized (LOCK) {
            Map<Long, String> unloaded = UNLOADED_CHUNK_DATA.get(chunk);
            stored = unloaded == null ? snapshot(event.getWorld(), chunk.x, chunk.z)
                    : new LinkedHashMap<>(unloaded);
        }
        NBTTagList list = new NBTTagList();
        Map<Long, String> retained = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : stored.entrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getKey());
            IBlockState state = chunk.getBlockState(pos);
            if ((!FFDStoredFluidStates.has(event.getWorld(), pos)
                    && !WaterloggedBlockApi.isWaterlogged(state)
                    && WaterloggedBlockApi.withWaterlogged(state, true) == null
                    && !WaterloggedConfig.isAdditionalWaterloggingBlock(
                            event.getWorld(), state.getBlock()))
                    || FluidRegistry.getFluid(entry.getValue()) == null) {
                removeLocal(event.getWorld(), pos);
                continue;
            }
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong(NBT_POS, entry.getKey());
            tag.setString(NBT_FLUID, entry.getValue());
            list.appendTag(tag);
            retained.put(entry.getKey(), entry.getValue());
        }
        if (list.tagCount() > 0) {
            event.getData().setTag(NBT_DATA, list);
        } else {
            event.getData().removeTag(NBT_DATA);
        }
        synchronized (LOCK) {
            if (UNLOADED_CHUNK_DATA.containsKey(chunk)) {
                UNLOADED_CHUNK_DATA.put(chunk, retained);
            }
        }
    }

    @SubscribeEvent
    public static void watchChunk(ChunkWatchEvent.Watch event) {
        Chunk chunk = event.getChunkInstance();
        if (chunk != null) {
            WaterloggedNetwork.sendFluidloggedChunk(event.getPlayer(), chunk.getWorld(), chunk.x, chunk.z,
                    snapshot(chunk.getWorld(), chunk.x, chunk.z));
        }
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(event.getWorld(), false);
            if (worldData != null) {
                Map<Long, String> removed = worldData.remove(
                        chunkKey(event.getChunk().x, event.getChunk().z));
                if (!event.getWorld().isRemote && removed != null) {
                    UNLOADED_CHUNK_DATA.put(event.getChunk(), removed);
                }
            }
        }
    }

    @SubscribeEvent
    public static void loadChunk(ChunkEvent.Load event) {
        synchronized (LOCK) {
            Map<Long, String> retained = UNLOADED_CHUNK_DATA.remove(event.getChunk());
            if (retained != null) {
                putChunk(event.getWorld(), event.getChunk().x, event.getChunk().z, retained);
            }
        }
        if (!event.getWorld().isRemote) {
            Map<Long, String> loaded = snapshot(event.getWorld(), event.getChunk().x, event.getChunk().z);
            for (Long packed : loaded.keySet()) {
                BlockPos pos = BlockPos.fromLong(packed);
                IBlockState state = event.getWorld().getBlockState(pos);
                if (WaterloggedBlockApi.isWaterlogged(state)) continue;
                IBlockState wet = WaterloggedBlockApi.withWaterlogged(state, true);
                if (wet != null) {
                    WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(
                            event.getWorld(), pos, wet, 3);
                }
            }
        }
    }

    @SubscribeEvent
    public static void unloadWorld(WorldEvent.Unload event) {
        synchronized (LOCK) {
            WORLD_DATA.remove(event.getWorld());
            UNLOADED_CHUNK_DATA.keySet().removeIf(chunk -> chunk.getWorld() == event.getWorld());
        }
    }

    public static void receiveChunk(World world, int chunkX, int chunkZ,
                                    Map<Long, String> fluids) {
        putChunk(world, chunkX, chunkZ, fluids);
        world.markBlockRangeForRenderUpdate(chunkX << 4, -64, chunkZ << 4,
                (chunkX << 4) + 15, 319, (chunkZ << 4) + 15);
    }

    public static void receiveUpdate(World world, BlockPos pos, @Nullable String fluidName) {
        Fluid fluid = fluidName == null || fluidName.isEmpty()
                ? null : FluidRegistry.getFluid(fluidName);
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(world, fluid != null);
            long chunkKey = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            Map<Long, String> chunkData = worldData == null ? null : worldData.get(chunkKey);
            if (fluid == null) {
                if (chunkData != null) {
                    chunkData.remove(pos.toLong());
                    if (chunkData.isEmpty()) {
                        worldData.remove(chunkKey);
                    }
                }
            } else {
                if (chunkData == null) {
                    chunkData = new LinkedHashMap<>();
                    worldData.put(chunkKey, chunkData);
                }
                chunkData.put(pos.toLong(), fluid.getName());
            }
        }
        world.markBlockRangeForRenderUpdate(pos, pos);
    }

    private static void putChunk(World world, int chunkX, int chunkZ, Map<Long, String> data) {
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(world, !data.isEmpty());
            if (worldData == null) {
                return;
            }
            long key = chunkKey(chunkX, chunkZ);
            if (data.isEmpty()) {
                worldData.remove(key);
            } else {
                worldData.put(key, new LinkedHashMap<>(data));
            }
        }
    }

    private static void removeLocal(World world, BlockPos pos) {
        synchronized (LOCK) {
            Map<Long, Map<Long, String>> worldData = dataFor(world, false);
            if (worldData == null) {
                return;
            }
            long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
            Map<Long, String> chunkData = worldData.get(key);
            if (chunkData != null) {
                chunkData.remove(pos.toLong());
                if (chunkData.isEmpty()) {
                    worldData.remove(key);
                }
            }
        }
    }

    @Nullable
    public static World resolveWorld(IBlockAccess access) {
        if (access instanceof World) {
            return (World) access;
        }
        World clientWorld = WaterloggedMod.proxy.getClientWorld();
        return clientWorld != null && clientWorld.provider != null ? clientWorld : null;
    }

    @Nullable
    private static Map<Long, Map<Long, String>> dataFor(World world, boolean create) {
        Map<Long, Map<Long, String>> data = WORLD_DATA.get(world);
        if (data == null && create) {
            data = new HashMap<>();
            WORLD_DATA.put(world, data);
        }
        return data;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return chunkX & 0xFFFFFFFFL | (long) chunkZ << 32;
    }
}
