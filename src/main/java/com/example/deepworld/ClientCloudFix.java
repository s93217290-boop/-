package com.example.deepworld;

import net.minecraft.client.world.DimensionRenderInfo;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Client only. Vanilla draws overworld clouds at y=128. With the terrain raised by
 * {@link TerrainShift#SHIFT} blocks the clouds would sit inside the ground, so they are raised too.
 */
public final class ClientCloudFix {
    private static final float VANILLA_CLOUD_HEIGHT = 128.0F;

    private ClientCloudFix() {
    }

    @SuppressWarnings("unchecked")
    public static void apply() {
        try {
            // Static map of dimension effects (looked up by type, so mapping names do not matter).
            Map<ResourceLocation, DimensionRenderInfo> effects = null;
            for (Field f : DimensionRenderInfo.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    effects = (Map<ResourceLocation, DimensionRenderInfo>) f.get(null);
                    break;
                }
            }
            if (effects == null) {
                DeepWorldMod.LOGGER.warn("Dimension effects map not found, clouds NOT raised.");
                return;
            }

            DimensionRenderInfo overworld = effects.get(new ResourceLocation("overworld"));
            if (overworld == null) {
                DeepWorldMod.LOGGER.warn("Overworld render info not found, clouds NOT raised.");
                return;
            }

            // The only float field in DimensionRenderInfo is the cloud level.
            for (Field f : DimensionRenderInfo.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == float.class) {
                    f.setAccessible(true);
                    float old = f.getFloat(overworld);
                    if (old == VANILLA_CLOUD_HEIGHT) {
                        float updated = old + TerrainShift.SHIFT;
                        f.setFloat(overworld, updated);
                        DeepWorldMod.LOGGER.info("Overworld cloud height {} -> {}", old, updated);
                    } else {
                        DeepWorldMod.LOGGER.warn("Overworld cloud height is {} (expected {}); leaving it alone.", old, VANILLA_CLOUD_HEIGHT);
                    }
                    return;
                }
            }
            DeepWorldMod.LOGGER.warn("Cloud height field not found, clouds NOT raised.");
        } catch (Throwable t) {
            DeepWorldMod.LOGGER.error("Failed to raise clouds.", t);
        }
    }
}
