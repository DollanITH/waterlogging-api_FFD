package dev.xy177.waterlogged;

import dev.xy177.waterlogged.api.WaterloggedBlockApi;
import dev.xy177.waterlogged.common.WaterloggedConfig;
import dev.xy177.waterlogged.common.WaterloggedBucketHandler;
import dev.xy177.waterlogged.common.fluid.FFDFluidloggedData;
import dev.xy177.waterlogged.common.fluid.FFDStoredFluidStates;
import dev.xy177.waterlogged.common.network.WaterloggedNetwork;
import dev.xy177.waterlogged.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLModIdMappingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = WaterloggedMod.MODID, name = "Waterlogged", version = "0.1.0")
@Mod.EventBusSubscriber(modid = WaterloggedMod.MODID)
public final class WaterloggedMod {
    public static final String MODID = "waterlogged";

    @SidedProxy(clientSide = "dev.xy177.waterlogged.proxy.ClientProxy",
            serverSide = "dev.xy177.waterlogged.proxy.CommonProxy")
    public static CommonProxy proxy;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        WaterloggedBlockApi.prepareUniversalStateCarriers(event.getRegistry());
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        WaterloggedConfig.load(event.getSuggestedConfigurationFile());
        WaterloggedNetwork.init();
        MinecraftForge.EVENT_BUS.register(WaterloggedBucketHandler.class);
        MinecraftForge.EVENT_BUS.register(FFDFluidloggedData.class);
        MinecraftForge.EVENT_BUS.register(FFDStoredFluidStates.class);
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        WaterloggedBlockApi.initializeUniversalStateMappings();
    }

    @Mod.EventHandler
    public void onModIdMapping(FMLModIdMappingEvent event) {
        WaterloggedBlockApi.initializeUniversalStateMappings();
    }
}
