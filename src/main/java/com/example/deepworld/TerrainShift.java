package com.example.deepworld;

import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.gen.settings.DimensionSettings;
import net.minecraft.world.gen.settings.NoiseSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Raises the vanilla overworld terrain by {@link #SHIFT} blocks.
 *
 * How it works (1.16.5 NoiseChunkGenerator): at every vertical noise cell i the density gets
 * (1 - 2*i/cellCount) * densityFactor + densityOffset. Moving the surface up by N blocks means
 * adding N/128 to densityOffset (for 256 high worlds, factor 1.0). The sea level is moved by the
 * same amount so oceans stay oceans.
 *
 * The same change is also shipped as data/minecraft/worldgen/noise_settings/overworld.json.
 * Both are absolute values, so applying both is harmless.
 */
public final class TerrainShift {
    public static final int SHIFT = 64;

    private static final double VANILLA_DENSITY_OFFSET = -0.46875;
    private static final int VANILLA_SEA_LEVEL = 63;

    private TerrainShift() {
    }

    public static void apply() {
        try {
            DimensionSettings settings = WorldGenRegistries.NOISE_GENERATOR_SETTINGS.get(DimensionSettings.OVERWORLD);
            if (settings == null) {
                DeepWorldMod.LOGGER.error("Overworld noise settings not found, terrain NOT shifted by code (data pack JSON may still apply).");
                return;
            }

            // --- density offset ---
            Field noiseField = fieldOfType(DimensionSettings.class, NoiseSettings.class);
            Object noise = noiseField.get(settings);
            List<Field> doubles = fieldsOf(NoiseSettings.class, double.class);
            // declared order in NoiseSettings: densityFactor, densityOffset
            Field offsetField = doubles.get(1);
            double oldOffset = offsetField.getDouble(noise);
            if (Math.abs(oldOffset - VANILLA_DENSITY_OFFSET) < 1.0E-9) {
                offsetField.setDouble(noise, oldOffset + SHIFT / 128.0);
                DeepWorldMod.LOGGER.info("Overworld density offset {} -> {}", oldOffset, oldOffset + SHIFT / 128.0);
            } else {
                DeepWorldMod.LOGGER.warn("Overworld density offset is {} (expected {}); leaving it alone.", oldOffset, VANILLA_DENSITY_OFFSET);
            }

            // --- sea level ---
            boolean seaDone = false;
            for (Field f : fieldsOf(DimensionSettings.class, int.class)) {
                if (f.getInt(settings) == VANILLA_SEA_LEVEL) {
                    f.setInt(settings, VANILLA_SEA_LEVEL + SHIFT);
                    DeepWorldMod.LOGGER.info("Overworld sea level {} -> {}", VANILLA_SEA_LEVEL, VANILLA_SEA_LEVEL + SHIFT);
                    seaDone = true;
                    break;
                }
            }
            if (!seaDone) {
                DeepWorldMod.LOGGER.warn("Overworld sea level field not found or already changed.");
            }
        } catch (Throwable t) {
            DeepWorldMod.LOGGER.error("Failed to shift overworld terrain by code. The data pack JSON in this mod is the fallback.", t);
        }
    }

    private static Field fieldOfType(Class<?> owner, Class<?> type) {
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new IllegalStateException("No field of type " + type + " in " + owner);
    }

    private static List<Field> fieldsOf(Class<?> owner, Class<?> primitive) {
        List<Field> out = new ArrayList<>();
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == primitive) {
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out;
    }
}
