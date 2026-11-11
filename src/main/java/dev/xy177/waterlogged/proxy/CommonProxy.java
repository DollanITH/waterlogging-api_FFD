package dev.xy177.waterlogged.proxy;

import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CommonProxy {
    public void preInit() {}
    public World getClientWorld() { return null; }
    public void handleFluidloggedChunk(int dimension, int x, int z, Map<Long, String> fluids) {}
    public void handleFluidloggedUpdate(int dimension, BlockPos pos, String fluid) {}
    public void handleStoredFluidChunk(int dimension, int x, int z, NBTTagCompound data,
                                       boolean replace) {}
}
