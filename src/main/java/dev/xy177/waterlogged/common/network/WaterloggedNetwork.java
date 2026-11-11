package dev.xy177.waterlogged.common.network;

import java.util.Map;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class WaterloggedNetwork {
    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("waterlogged");

    private WaterloggedNetwork() {}

    public static void init() {
        CHANNEL.registerMessage(PacketFluidloggedChunk.Handler.class, PacketFluidloggedChunk.class,
                0, Side.CLIENT);
        CHANNEL.registerMessage(PacketFluidloggedUpdate.Handler.class, PacketFluidloggedUpdate.class,
                1, Side.CLIENT);
        CHANNEL.registerMessage(PacketStoredFluidChunk.Handler.class, PacketStoredFluidChunk.class,
                2, Side.CLIENT);
    }

    public static void sendFluidloggedChunk(EntityPlayerMP player, World world, int x, int z,
                                             Map<Long, String> fluids) {
        CHANNEL.sendTo(new PacketFluidloggedChunk(world.provider.getDimension(), x, z, fluids), player);
    }

    public static void sendFluidloggedUpdate(World world, BlockPos pos, String fluidName) {
        CHANNEL.sendToAllAround(new PacketFluidloggedUpdate(world.provider.getDimension(), pos, fluidName),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX() + 0.5,
                        pos.getY() + 0.5, pos.getZ() + 0.5, 256));
    }

    public static void sendStoredFluidChunk(EntityPlayerMP player, int x, int z,
                                             NBTTagCompound data) {
        NBTTagList entries = data.getTagList("Entries", 10);
        for (int start = 0; start == 0 || start < entries.tagCount(); start += 128) {
            NBTTagCompound batch = new NBTTagCompound();
            NBTTagList part = new NBTTagList();
            for (int i = start; i < Math.min(start + 128, entries.tagCount()); i++)
                part.appendTag(entries.getCompoundTagAt(i));
            batch.setTag("Entries", part);
            if (start == 0) batch.setTag("Policy", WaterloggedConfig.fluidPolicy());
            CHANNEL.sendTo(new PacketStoredFluidChunk(player.dimension, x, z, batch, start == 0),
                    player);
        }
    }

    public static void sendStoredFluidChunk(World world, int x, int z, NBTTagCompound data) {
        if (!(world instanceof WorldServer)) return;
        WorldServer server = (WorldServer) world;
        for (EntityPlayerMP player : server.getMinecraftServer().getPlayerList().getPlayers()) {
            if (player.world == world && server.getPlayerChunkMap().isPlayerWatchingChunk(player, x, z))
                sendStoredFluidChunk(player, x, z, data);
        }
    }
}
