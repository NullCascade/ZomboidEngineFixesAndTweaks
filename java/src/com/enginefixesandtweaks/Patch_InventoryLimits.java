package com.enginefixesandtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.InventoryContainer;

/**
 * Patch: Expand Inventory Limits
 * 
 * - Dehardcodes the ItemContainer limit of 50 and the InventoryContainer limit of 100.
 * - Default values are now 100 and 1000.
 * - Mostly a quality of life fix. Allows characters with a backpack on to still be able to pick up fridges.
 */

public final class Patch_InventoryLimits {
	public static final int VANILLA_INVENTORY_ITEM_LIMIT = 50;
	public static final int VANILLA_DEFAULT_CONTAINER_LIMIT = 100;

	private Patch_InventoryLimits() {
	}

	@Patch(className = "zombie.inventory.ItemContainer", methodName = "getCapacity")
	public static class Patch_ItemContainer_getCapacity {
		@Patch.OnExit
		public static void exit(@Patch.This ItemContainer self, @Patch.Return(readOnly = false) int result) {
			if (!Options.isCapacityPatchEnabled()) {
				return;
			}

			int capacity = self.capacity;
			if (self.isOccupiedVehicleSeat()) {
				capacity /= 4;
			}

			if (self.getVehiclePart() != null) {
				result = Math.min(capacity, 1000);
				return;
			}

			if (self.parent instanceof IsoGameCharacter) {
				result = Math.max(capacity, Options.getInventoryItemCapacity(VANILLA_INVENTORY_ITEM_LIMIT));
				return;
			}

			boolean inventoryItem = self.getContainingItem() != null;
			int vanillaLimit = inventoryItem ? VANILLA_INVENTORY_ITEM_LIMIT : VANILLA_DEFAULT_CONTAINER_LIMIT;
			int maxCapacity = inventoryItem ? Options.getInventoryItemCapacity(vanillaLimit) : Options.getDefaultContainerCapacity(vanillaLimit);
			result = capacity >= vanillaLimit ? Math.min(capacity, maxCapacity) : capacity;
		}
	}

	@Patch(className = "zombie.inventory.types.InventoryContainer", methodName = "getCapacity")
	public static class Patch_InventoryContainer_getCapacity {
		@Patch.OnExit
		public static void exit(@Patch.This InventoryContainer self, @Patch.Return(readOnly = false) int result) {
			if (!Options.isCapacityPatchEnabled()) {
				return;
			}

			int capacity = self.getInventory().getCapacity();
			int limit = Options.getInventoryItemCapacity(capacity);
			if (capacity > limit - self.getActualWeight()) {
				capacity = (int)(limit - self.getActualWeight());
			}

			result = capacity;
		}
	}

	@Patch(className = "zombie.inventory.types.InventoryContainer", methodName = "getEffectiveCapacity")
	public static class Patch_InventoryContainer_getEffectiveCapacity {
		@Patch.OnExit
		public static void exit(@Patch.This InventoryContainer self, @Patch.Argument(0) IsoGameCharacter chr, @Patch.Return(readOnly = false) int result) {
			if (!Options.isCapacityPatchEnabled()) {
				return;
			}

			int capacity = self.getInventory().getEffectiveCapacity(chr);
			int limit = Options.getInventoryItemCapacity(capacity);
			if (capacity > limit - self.getActualWeight()) {
				capacity = (int)(limit - self.getActualWeight());
			}

			result = capacity;
		}
	}
}
