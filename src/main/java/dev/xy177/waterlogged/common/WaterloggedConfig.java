package dev.xy177.waterlogged.common;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.fluid.FFDFluidloggedData;

/** Server fluid policy; the NBT shape follows Farmer's Future Delight. */
public final class WaterloggedConfig {
    private static Configuration config;
    private static String mode = "AUTO";
    private static String scope = "WATER";
    private static String[] fluids = new String[0];
    private static String[] blocks = new String[0];
    private static final Map<World, NBTTagCompound> remotePolicies = new WeakHashMap<>();

    private WaterloggedConfig() {}

    public static void load(File file) {
        config = new Configuration(file);
        config.load();
        mode = config.getString("waterloggingMode", "waterlogging", "AUTO",
                "ENABLED, DISABLED, or AUTO (yield to Fluidlogged API)");
        scope = config.getString("fluidloggingScope", "waterlogging", "WATER",
                "WATER, ALL_FLUIDS, WHITELIST, or BLACKLIST");
        fluids = config.getStringList("fluidloggingFluidList", "waterlogging", new String[0],
                "Forge fluid registry names");
        blocks = config.getStringList("waterloggingAdditionalBlocks", "waterlogging", new String[0],
                "Additional block registry names that can contain a fluid state");
        if (config.hasChanged()) config.save();
    }

    public static boolean isWaterloggingEnabled() {
        return !"DISABLED".equalsIgnoreCase(mode)
                && (!"AUTO".equalsIgnoreCase(mode) || !isFluidloggedApiLoaded());
    }

    private static boolean isFluidloggedApiLoaded() {
        try {
            return Loader.isModLoaded("fluidlogged_api");
        } catch (IllegalStateException | NullPointerException notInitialized) {
            return false;
        }
    }

    public static boolean isFluidAllowed(Fluid fluid) {
        return isWaterloggingEnabled() && allowed(scope, fluids, fluid);
    }

    public static boolean isFluidAllowed(IBlockAccess access, Fluid fluid) {
        NBTTagCompound policy = policy(access);
        return policy == null ? isFluidAllowed(fluid)
                : policy.getBoolean("Enabled") && allowed(policy.getString("Scope"),
                    strings(policy.getTagList("Fluids", 8)), fluid);
    }

    private static boolean allowed(String selection, String[] entries, Fluid fluid) {
        if (fluid == null || fluid.getBlock() == null) return false;
        if ("ALL_FLUIDS".equalsIgnoreCase(selection)) return true;
        if ("WATER".equalsIgnoreCase(selection)) return fluid == FluidRegistry.WATER;
        String blockName = String.valueOf(fluid.getBlock().getRegistryName());
        boolean listed = Arrays.stream(entries).anyMatch(value -> value != null
                && (value.trim().equalsIgnoreCase(fluid.getName())
                    || value.trim().equalsIgnoreCase(blockName)));
        return "WHITELIST".equalsIgnoreCase(selection) ? listed
                : "BLACKLIST".equalsIgnoreCase(selection) && !listed;
    }

    public static boolean isAdditionalWaterloggingBlock(Block block) {
        return block != null && block.getRegistryName() != null && Arrays.stream(blocks)
                .anyMatch(name -> name != null && name.trim().equals(block.getRegistryName().toString()));
    }

    public static boolean isAdditionalWaterloggingBlock(IBlockAccess access, Block block) {
        NBTTagCompound policy = policy(access);
        return policy == null ? isAdditionalWaterloggingBlock(block)
                : policy.getBoolean("Enabled") && block != null && block.getRegistryName() != null
                    && Arrays.stream(strings(policy.getTagList("Blocks", 8)))
                        .anyMatch(name -> name.equals(block.getRegistryName().toString()));
    }

    private static synchronized NBTTagCompound policy(IBlockAccess access) {
        return remotePolicies.get(FFDFluidloggedData.resolveWorld(access));
    }

    public static synchronized void receiveFluidPolicy(World world, NBTTagCompound policy) {
        if (world != null && world.isRemote) remotePolicies.put(world, policy.copy());
    }

    public static NBTTagCompound fluidPolicy() {
        NBTTagCompound policy = new NBTTagCompound();
        policy.setBoolean("Enabled", isWaterloggingEnabled());
        policy.setString("Scope", scope);
        NBTTagList blockTags = new NBTTagList();
        for (String block : blocks) blockTags.appendTag(new NBTTagString(block));
        policy.setTag("Blocks", blockTags);
        NBTTagList fluidTags = new NBTTagList();
        for (String fluid : fluids) fluidTags.appendTag(new NBTTagString(fluid));
        policy.setTag("Fluids", fluidTags);
        return policy;
    }

    private static String[] strings(NBTTagList list) {
        String[] result = new String[list.tagCount()];
        for (int i = 0; i < result.length; i++) result[i] = list.getStringTagAt(i);
        return result;
    }
}
