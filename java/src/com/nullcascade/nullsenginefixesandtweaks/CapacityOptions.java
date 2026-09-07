package com.nullcascade.nullsenginefixesandtweaks;

import zombie.SandboxOptions;

public final class CapacityOptions {
	private static final String ENABLE = "NullsEngineFixesAndTweaks.EnableCapacityPatch";
	private static final String INVENTORY_ITEM_CAPACITY = "NullsEngineFixesAndTweaks.InventoryItemCapacity";
	private static final String DEFAULT_CONTAINER_CAPACITY = "NullsEngineFixesAndTweaks.DefaultContainerCapacity";

	private CapacityOptions() {
	}

	public static boolean isEnabled() {
		SandboxOptions.SandboxOption option = SandboxOptions.instance.getOptionByName(ENABLE);
		if (option == null) {
			return false;
		}

		Object value = option.asConfigOption().getValueAsObject();
		return value instanceof Boolean && (Boolean)value;
	}

	public static int getInventoryItemCapacity(int fallback) {
		return getInteger(INVENTORY_ITEM_CAPACITY, fallback);
	}

	public static int getDefaultContainerCapacity(int fallback) {
		return getInteger(DEFAULT_CONTAINER_CAPACITY, fallback);
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
