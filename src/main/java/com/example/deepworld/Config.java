package com.example.deepworld;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Collections;
import java.util.List;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ADJUST_ORES;
    public static final ForgeConfigSpec.BooleanValue STRETCH_DOWN;
    public static final ForgeConfigSpec.BooleanValue SCALE_COUNTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_NAMESPACES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ores");
        ADJUST_ORES = b
                .comment("Adjust ore generation for the +64 terrain shift. Turn off to leave ores untouched.")
                .define("adjustOres", true);
        STRETCH_DOWN = b
                .comment("true: ore ranges keep their old bottom and grow upward by the terrain shift (64), so the new lower layers get ores.",
                        "false: ore ranges are simply moved up by the terrain shift (the bottom layers get no extra ores).")
                .define("stretchDown", true);
        SCALE_COUNTS = b
                .comment("When stretching, multiply veins per chunk by the range growth so ore density per block stays the same.")
                .define("scaleCounts", true);
        EXCLUDED_NAMESPACES = b
                .comment("Biome namespaces (mod ids) whose ores must not be touched, e.g. [\"twilightforest\"].",
                        "Use this for dimensions that do NOT use the vanilla overworld noise settings.")
                .defineList("excludedBiomeNamespaces", Collections.emptyList(), o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }
}
