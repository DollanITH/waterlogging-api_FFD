package dev.xy177.waterlogged.core;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.fluid.FFDFluidloggedData;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Runtime-only bridge used by the optional WorldEdit transformer. */
public final class WaterloggedWorldEditBridge {
    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();
    private static final Map<World, Map<Long, Pending>> QUEUED = new WeakHashMap<>();
    private static final Map<Object, Map<Long, Pending>> CLIPBOARDS = new WeakHashMap<>();
    private WaterloggedWorldEditBridge() {}

    public static void begin(Object sourceExtent, Object sourcePos) {
        try {
            BlockPos source = blockPos(sourcePos);
            World world = worldOf(sourceExtent);
            if (world == null) {
                Pending clipboardState = clipboardState(sourceExtent, source);
                if (clipboardState != null) {
                    PENDING.set(clipboardState);
                    log("clipboard source capture source=" + source + " extent="
                            + className(sourceExtent) + " wet=" + clipboardState.waterlogged);
                    return;
                }
            }
            if (world != null && source != null) {
                IBlockState fluid = FFDStoredFluidStates.get(world, source);
                IBlockState host = FFDStoredFluidStates.getHostState(world, source);
                Fluid type = FFDFluidloggedData.get(world, source);
                boolean wet = WaterloggedBlockApi.isWaterlogged(world, source)
                        || fluid != null || type != null || host != null;
                if (wet && fluid == null && type == null) {
                    type = FluidRegistry.WATER;
                    fluid = WaterloggedBlockApi.sourceState(type);
                }
                PENDING.set(new Pending(world, source,
                        fluid, type, host, wet));
                log("source capture source=" + source
                        + " world=" + world.getClass().getName() + " wet=" + wet
                        + " fluidState=" + (fluid != null) + " fluidType=" + type
                        + " hostState=" + (host != null));
                return;
            }
        } catch (Throwable error) {
            log("source capture failed extent=" + className(sourceExtent)
                    + " pos=" + sourcePos + " error=" + error);
        }
        BlockPos parsedSource = null;
        try {
            parsedSource = blockPos(sourcePos);
        } catch (Throwable ignored) {
            // The fallback record retains the extent for later diagnostics.
        }
        PENDING.set(new Pending(sourceExtent, sourcePos, null, parsedSource,
                null, null, null, false));
        log("source extent unresolved extent=" + className(sourceExtent)
                + " source=" + parsedSource);
    }

    public static void finish(Object destinationExtent, Object targetPos, boolean writeSucceeded) {
        log("finish invoked destination=" + className(destinationExtent) + " target="
                + targetPos + " writeSucceeded=" + writeSucceeded);
        if (!writeSucceeded) {
            PENDING.remove();
            return;
        }
        try {
            BlockPos target = blockPos(targetPos);
            World world = worldOf(destinationExtent);
            if (world == null) {
                storeClipboardState(destinationExtent, target);
                return;
            }
            finish(world, target);
        } catch (Throwable error) {
            log("destination resolution failed extent=" + className(destinationExtent)
                    + " target=" + targetPos + " error=" + error);
            PENDING.remove();
        }
    }

    public static void finish(World targetWorld, BlockPos target) {
        Pending pending = PENDING.get();
        PENDING.remove();
        if (pending == null || targetWorld == null) {
            log("copy queue skipped pending=" + (pending != null)
                    + ", targetWorld=" + (targetWorld != null));
            return;
        }
        if (!pending.waterlogged && pending.fluidState == null
                && pending.fluidType == null && pending.hostState == null) {
            return;
        }
        if (pending.source == null) {
            log("copy queue skipped: wet source position unavailable target=" + target
                    + " extent=" + className(pending.sourceExtent));
            return;
        }
        synchronized (QUEUED) {
            QUEUED.computeIfAbsent(targetWorld, ignored -> new HashMap<>())
                    .put(target.toLong(), pending);
        }
        log("queued fluid copy source=" + pending.source + " target=" + target);
    }

    private static void storeClipboardState(Object clipboard, BlockPos target) {
        Pending pending = PENDING.get();
        PENDING.remove();
        if (clipboard == null || pending == null
                || (!pending.waterlogged && pending.fluidState == null
                        && pending.fluidType == null && pending.hostState == null)) return;
        synchronized (CLIPBOARDS) {
            CLIPBOARDS.computeIfAbsent(clipboard, ignored -> new HashMap<>())
                    .put(target.toLong(), pending);
        }
        log("clipboard fluid stored extent=" + className(clipboard)
                + " pos=" + target + " fluidState=" + (pending.fluidState != null));
    }

    private static Pending clipboardState(Object clipboard, BlockPos source) {
        Object owner = clipboardExtent(clipboard);
        synchronized (CLIPBOARDS) {
            Map<Long, Pending> entries = owner == null ? null : CLIPBOARDS.get(owner);
            return entries == null ? null : entries.get(source.toLong());
        }
    }

    private static Object clipboardExtent(Object extent) {
        Object current = extent;
        for (int i = 0; i < 8 && current != null; i++) {
            if (current.getClass().getName().contains("Clipboard")) return current;
            Object next = invokeNoArg(current, "getExtent");
            if (next == current) break;
            current = next;
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void afterWorldEditBlockWrite(World world, BlockPos target) {
        Pending pending;
        synchronized (QUEUED) {
            Map<Long, Pending> entries = QUEUED.get(world);
            pending = entries == null ? null : entries.remove(target.toLong());
            if (entries != null && entries.isEmpty()) QUEUED.remove(world);
        }
        if (pending == null) return;
        try {
            IBlockState targetState = world.getBlockState(target);
            IBlockState wet = WaterloggedBlockApi.withWaterlogged(targetState, true);
            if (wet == null) {
                log("deferred copy target cannot be waterlogged target=" + target
                        + " state=" + targetState);
                return;
            }
            if (!wet.equals(targetState)
                    && !WaterloggedBlockApi.setBlockStateWithoutWaterloggingHook(
                            world, target, wet, 3)) {
                log("deferred host state write failed target=" + target + " state=" + targetState);
                return;
            }
            IBlockState fluid = pending.fluidState;
            Fluid type = pending.fluidType;
            if (fluid == null && type != null) fluid = WaterloggedBlockApi.sourceState(type);
            if (type == null && fluid != null) type = WaterloggedBlockApi.getFluidForBlock(fluid.getBlock());
            if (fluid != null) FFDStoredFluidStates.set(world, target, fluid);
            else if (type != null) FFDStoredFluidStates.set(world, target,
                    WaterloggedBlockApi.sourceState(type));
            if (type != null) FFDFluidloggedData.set(world, target, type);
            else if (fluid != null) FFDFluidloggedData.set(world, target,
                    WaterloggedBlockApi.getFluidForBlock(fluid.getBlock()));
            if (fluid != null || type != null) FFDStoredFluidStates.sync(world, target);
            log("deferred copy complete source=" + pending.source + " target=" + target
                    + " wet=" + pending.waterlogged + " fluidState=" + (fluid != null)
                    + " fluidType=" + type + " hostState=" + world.getBlockState(target)
                    + " storedState=" + FFDStoredFluidStates.get(world, target));
        } catch (Throwable error) {
            log("deferred contained-fluid copy failed target=" + target + " error=" + error);
            // WorldEdit is optional; a reflection mismatch must never break block writes.
        }
    }

    private static World worldOf(Object extent) throws Exception {
        Object value = extent;
        for (int i = 0; i < 4 && value != null; i++) {
            Method m;
            try { m = value.getClass().getMethod("getWorld"); }
            catch (NoSuchMethodException e) { return value instanceof World ? (World) value : null; }
            m.setAccessible(true);
            value = m.invoke(value);
        }
        return value instanceof World ? (World) value : null;
    }

    private static BlockPos blockPos(Object vector) throws Exception {
        Method x = vector.getClass().getMethod("getBlockX");
        Method y = vector.getClass().getMethod("getBlockY");
        Method z = vector.getClass().getMethod("getBlockZ");
        return new BlockPos(((Number) x.invoke(vector)).intValue(),
                ((Number) y.invoke(vector)).intValue(), ((Number) z.invoke(vector)).intValue());
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static void log(String message) {
        System.out.println("[Waterlogged/WorldEdit] " + message);
    }

    private static final class Pending {
        final Object sourceExtent, sourcePos;
        final World sourceWorld; final BlockPos source;
        final IBlockState fluidState, hostState; final Fluid fluidType; final boolean waterlogged;
        Pending(Object sourceExtent, Object sourcePos) {
            this(sourceExtent, sourcePos, null, null, null, null, null, false);
        }
        Pending(World world, BlockPos source, IBlockState fluidState, Fluid fluidType,
                IBlockState hostState, boolean waterlogged) {
            this(null, null, world, source, fluidState, fluidType, hostState, waterlogged);
        }
        private Pending(Object sourceExtent, Object sourcePos, World sourceWorld, BlockPos source,
                        IBlockState fluidState, Fluid fluidType, IBlockState hostState,
                        boolean waterlogged) {
            this.sourceExtent = sourceExtent; this.sourcePos = sourcePos;
            this.sourceWorld = sourceWorld; this.source = source;
            this.fluidState = fluidState; this.fluidType = fluidType;
            this.hostState = hostState; this.waterlogged = waterlogged;
        }
    }
}
