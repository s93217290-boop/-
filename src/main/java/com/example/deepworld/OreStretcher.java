package com.example.deepworld;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.DecoratedFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureSpread;
import net.minecraft.world.gen.feature.FeatureSpreadConfig;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.ReplaceBlockConfig;
import net.minecraft.world.gen.placement.ConfiguredPlacement;
import net.minecraft.world.gen.placement.CountRangeConfig;
import net.minecraft.world.gen.placement.DepthAverageConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.placement.TopSolidRangeConfig;
import net.minecraftforge.event.world.BiomeLoadingEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rewrites the height placement of ore features (vanilla and modded) so they fit the +64 terrain.
 *
 * Handled placements: RANGE (TopSolidRangeConfig), COUNT_RANGE (CountRangeConfig),
 * DEPTH_AVERAGE (DepthAverageConfig) and COUNT (FeatureSpreadConfig).
 * A feature counts as an ore when its innermost config is OreFeatureConfig or ReplaceBlockConfig.
 * Ores that use a custom height placement or a custom config class are left untouched.
 */
public final class OreStretcher {
    private static final int WORLD_TOP = 256;
    private static final double MAX_MULTIPLIER = 8.0;

    // Vanilla features are shared between biomes, so never mutate them and transform each only once.
    private static final Map<ConfiguredFeature<?, ?>, ConfiguredFeature<?, ?>> CACHE = new IdentityHashMap<>();

    private OreStretcher() {
    }

    public static void onBiomeLoad(BiomeLoadingEvent event) {
        if (!Config.ADJUST_ORES.get()) {
            return;
        }
        Biome.Category category = event.getCategory();
        if (category == Biome.Category.NETHER || category == Biome.Category.THEEND || category == Biome.Category.NONE) {
            return;
        }
        if (event.getName() != null && Config.EXCLUDED_NAMESPACES.get().contains(event.getName().getNamespace())) {
            return;
        }

        GenerationStage.Decoration[] stages = {
                GenerationStage.Decoration.UNDERGROUND_ORES,
                GenerationStage.Decoration.UNDERGROUND_DECORATION
        };
        for (GenerationStage.Decoration stage : stages) {
            List<Supplier<ConfiguredFeature<?, ?>>> list = event.getGeneration().getFeatures(stage);
            for (int i = 0; i < list.size(); i++) {
                list.set(i, lazy(list.get(i)));
            }
        }
    }

    /** Wraps a supplier so the transformation happens when the feature is first needed. */
    private static Supplier<ConfiguredFeature<?, ?>> lazy(Supplier<ConfiguredFeature<?, ?>> source) {
        return new Supplier<ConfiguredFeature<?, ?>>() {
            private ConfiguredFeature<?, ?> value;

            @Override
            public synchronized ConfiguredFeature<?, ?> get() {
                if (value == null) {
                    ConfiguredFeature<?, ?> original = source.get();
                    value = transform(original);
                }
                return value;
            }
        };
    }

    private static ConfiguredFeature<?, ?> transform(ConfiguredFeature<?, ?> original) {
        synchronized (CACHE) {
            ConfiguredFeature<?, ?> cached = CACHE.get(original);
            if (cached != null) {
                return cached;
            }
        }
        ConfiguredFeature<?, ?> result;
        try {
            result = rebuild(original);
        } catch (Throwable t) {
            DeepWorldMod.LOGGER.warn("Could not adjust a feature, leaving it unchanged: {}", t.toString());
            result = original;
        }
        synchronized (CACHE) {
            CACHE.put(original, result);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ConfiguredFeature<?, ?> rebuild(ConfiguredFeature<?, ?> original) throws Exception {
        // Unwrap decorators: chain is ordered outermost -> innermost.
        List<ConfiguredPlacement<?>> chain = new ArrayList<>();
        ConfiguredFeature<?, ?> core = original;
        int guard = 0;
        while (guard++ < 16) {
            Object cfg = get(core, IFeatureConfig.class);
            if (!(cfg instanceof DecoratedFeatureConfig)) {
                break;
            }
            chain.add((ConfiguredPlacement<?>) get(cfg, ConfiguredPlacement.class));
            Supplier<ConfiguredFeature<?, ?>> inner = (Supplier) get(cfg, Supplier.class);
            core = inner.get();
        }

        Object coreConfig = get(core, IFeatureConfig.class);
        if (!(coreConfig instanceof OreFeatureConfig || coreConfig instanceof ReplaceBlockConfig)) {
            return original;
        }

        final boolean stretch = Config.STRETCH_DOWN.get();
        final int shift = TerrainShift.SHIFT;
        List<ConfiguredPlacement<?>> out = new ArrayList<>(chain);
        boolean changed = false;
        double rangeMultiplier = 1.0;

        // Pass 1: height ranges.
        for (int i = 0; i < chain.size(); i++) {
            ConfiguredPlacement<?> cp = chain.get(i);
            Placement<?> placement = (Placement<?>) get(cp, Placement.class);
            Object conf = get(cp, IPlacementConfig.class);

            if (conf instanceof TopSolidRangeConfig) {
                int[] v = ints(conf); // bottomOffset, topOffset, maximum
                int upper = v[0] + v[2] - v[1];
                if (upper >= WORLD_TOP) {
                    continue; // already reaches the top of the world
                }
                int add = Math.min(shift, WORLD_TOP - upper);
                IPlacementConfig newConf;
                if (stretch) {
                    newConf = new TopSolidRangeConfig(v[0], v[1], v[2] + add);
                    rangeMultiplier *= (double) (v[2] - v[1] + add) / Math.max(1, v[2] - v[1]);
                } else {
                    newConf = new TopSolidRangeConfig(v[0] + add, v[1], v[2]);
                }
                out.set(i, new ConfiguredPlacement(placement, newConf));
                changed = true;
            } else if (conf instanceof CountRangeConfig) {
                int[] v = ints(conf); // count, bottomOffset, topOffset, maximum
                int upper = v[1] + v[3] - v[2];
                if (upper >= WORLD_TOP) {
                    continue;
                }
                int add = Math.min(shift, WORLD_TOP - upper);
                IPlacementConfig newConf;
                if (stretch) {
                    double mult = Config.SCALE_COUNTS.get()
                            ? Math.min(MAX_MULTIPLIER, (double) (v[3] - v[2] + add) / Math.max(1, v[3] - v[2]))
                            : 1.0;
                    newConf = new CountRangeConfig(Math.max(1, (int) Math.round(v[0] * mult)), v[1], v[2], v[3] + add);
                } else {
                    newConf = new CountRangeConfig(v[0], v[1] + add, v[2], v[3]);
                }
                out.set(i, new ConfiguredPlacement(placement, newConf));
                changed = true;
            } else if (conf instanceof DepthAverageConfig) {
                int[] v = ints(conf); // baseline, spread
                IPlacementConfig newConf;
                if (stretch) {
                    int half = shift / 2;
                    newConf = new DepthAverageConfig(v[0] + half, v[1] + half);
                    rangeMultiplier *= (double) (v[1] + half) / Math.max(1, v[1]);
                } else {
                    newConf = new DepthAverageConfig(v[0] + shift, v[1]);
                }
                out.set(i, new ConfiguredPlacement(placement, newConf));
                changed = true;
            }
        }

        // Pass 2: veins per chunk, so density per block stays the same.
        if (changed && stretch && Config.SCALE_COUNTS.get() && rangeMultiplier > 1.0) {
            double mult = Math.min(MAX_MULTIPLIER, rangeMultiplier);
            for (int i = 0; i < chain.size(); i++) {
                ConfiguredPlacement<?> cp = chain.get(i);
                Object conf = get(cp, IPlacementConfig.class);
                if (conf instanceof FeatureSpreadConfig) {
                    FeatureSpread spread = (FeatureSpread) get(conf, FeatureSpread.class);
                    int[] v = ints(spread); // base, spread
                    if (v.length >= 2 && v[1] == 0) {
                        Placement<?> placement = (Placement<?>) get(cp, Placement.class);
                        int newCount = Math.max(1, (int) Math.round(v[0] * mult));
                        out.set(i, new ConfiguredPlacement(placement, new FeatureSpreadConfig(newCount)));
                    }
                }
            }
        }

        if (!changed) {
            return original;
        }

        ConfiguredFeature<?, ?> built = core;
        for (int i = out.size() - 1; i >= 0; i--) {
            built = wrap(built, out.get(i));
        }
        return built;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ConfiguredFeature<?, ?> wrap(ConfiguredFeature<?, ?> inner, ConfiguredPlacement<?> placement) {
        return new ConfiguredFeature(Feature.DECORATED, new DecoratedFeatureConfig(() -> inner, placement));
    }

    /** First non-static field assignable to {@code type}; looked up by type so it works with obfuscated names. */
    @SuppressWarnings("unchecked")
    private static <T> T get(Object owner, Class<T> type) throws IllegalAccessException {
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (T) f.get(owner);
                }
            }
        }
        throw new IllegalStateException("No field of type " + type.getSimpleName() + " in " + owner.getClass());
    }

    /** All non-static int fields in declaration order. */
    private static int[] ints(Object owner) throws IllegalAccessException {
        List<Integer> values = new ArrayList<>();
        for (Field f : owner.getClass().getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                f.setAccessible(true);
                values.add(f.getInt(owner));
            }
        }
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
