package com.sandydev.dcs;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Настройки мода. */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Подробные диагностические сообщения Dynamic Clothing System в latest.log.",
                    "Полезно для отладки загрузки скина, Curios и динамических текстур.")
            .define("debugLogging", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}
