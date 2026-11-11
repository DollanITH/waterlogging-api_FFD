package dev.xy177.waterlogged.proxy;

import java.util.Map;
import dev.xy177.waterlogged.client.WaterloggedClientEvents;
import dev.xy177.waterlogged.common.fluid.FFDFluidloggedData;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends CommonProxy {
    @Override public void preInit() {
        MinecraftForge.EVENT_BUS.register(WaterloggedClientEvents.class);
        WaterloggedClientEvents.registerStateMappers();
    }
    @Override public World getClientWorld() { return Minecraft.getMinecraft().world; }
    @Override public void handleFluidloggedChunk(int dimension, int x, int z,
                                                  Map<Long, String> fluids) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            World world = getClientWorld();
            if (world != null && world.provider.getDimension() == dimension)
                FFDFluidloggedData.receiveChunk(world, x, z, fluids);
        });
    }
    @Override public void handleFluidloggedUpdate(int dimension, BlockPos pos, String fluid) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            World world = getClientWorld();
            if (world != null && world.provider.getDimension() == dimension)
                FFDFluidloggedData.receiveUpdate(world, pos, fluid);
        });
    }
    @Override public void handleStoredFluidChunk(int dimension, int x, int z,
                                                  NBTTagCompound data, boolean replace) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            World world = getClientWorld();
            if (world != null && world.provider.getDimension() == dimension)
                FFDStoredFluidStates.receive(world, x, z, data, replace);
        });
    }
}
