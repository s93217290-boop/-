package com.example.deepworld;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Deep World +64: raises the overworld terrain by 64 blocks and stretches ore
 * generation into the new space below the surface.
 */
@Mod(DeepWorldMod.MOD_ID)
public class DeepWorldMod {
    public static final String MOD_ID = "deepworld";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public DeepWorldMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        // LOWEST: run after every other mod has added its ores to the biome.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, OreStretcher::onBiomeLoad);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(TerrainShift::apply);
    }

    // Fired on the client only; ClientCloudFix is the only class that touches client-side code.
    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ClientCloudFix::apply);
    }
}
