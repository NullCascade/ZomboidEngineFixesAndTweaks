package com.enginefixesandtweaks;

import zombie.SandboxOptions;

public final class Options {
    private static final String ENABLE_CAPACITY_PATCH = "EngineFixesAndTweaks.EnableCapacityPatch";
    private static final String ENABLE_WEAPON_LEVEL_PATCH = "EngineFixesAndTweaks.EnableWeaponLevelPatch";
    private static final String INVENTORY_ITEM_CAPACITY = "EngineFixesAndTweaks.InventoryItemCapacity";
    private static final String DEFAULT_CONTAINER_CAPACITY = "EngineFixesAndTweaks.DefaultContainerCapacity";

    private Options() {
    }

    public static boolean isCapacityPatchEnabled() {
        return getBoolean(ENABLE_CAPACITY_PATCH);
    }

    public static boolean isWeaponLevelPatchEnabled() {
        return getBoolean(ENABLE_WEAPON_LEVEL_PATCH);
    }

    public static int getInventoryItemCapacity(int fallback) {
        return getInteger(INVENTORY_ITEM_CAPACITY, fallback);
    }

    public static int getDefaultContainerCapacity(int fallback) {
        return getInteger(DEFAULT_CONTAINER_CAPACITY, fallback);
    }

    private static boolean getBoolean(String name) {
        SandboxOptions.SandboxOption option = SandboxOptions.instance.getOptionByName(name);
        if (option == null) {
            return false;
        }

        Object value = option.asConfigOption().getValueAsObject();
        return value instanceof Boolean && (Boolean)value;
    }

    private static int getInteger(String name, int fallback) {
        SandboxOptions.SandboxOption option = SandboxOptions.instance.getOptionByName(name);
        if (option == null) {
            return fallback;
        }

        Object value = option.asConfigOption().getValueAsObject();
        if (!(value instanceof Number number)) {
            return fallback;
        }

        return Math.max(1, Math.min(100000, number.intValue()));
    }
}
