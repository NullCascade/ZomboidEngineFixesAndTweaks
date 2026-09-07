package com.nullcascade.nullsenginefixesandtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.InventoryContainer;

import java.util.concurrent.Callable;

/**
 * Patch: Expand Inventory Limits
 * 
 * - Dehardcodes the ItemContainer limit of 50 and the InventoryContainer limit of 100.
 * - Default values are now 100 and 1000.
 * - Mostly a quality of life fix. Allows characters with a backpack on to still be able to pick up fridges.
 */

public final class Patch_InventoryLimits {
	private Patch_InventoryLimits() {
	}

	@Patch(className = "zombie.inventory.ItemContainer", methodName = "getCapacity", isAdvice = false)
	public static class Patch_ItemContainer_getCapacity {
		@Patch.RuntimeType
		public static int getCapacity(@Patch.This ItemContainer self, @Patch.SuperCall Callable<Integer> original) throws Exception {
			if (!Options.isCapacityPatchEnabled()) {
				return original.call();
			}

			int capacity = self.capacity;
			if (self.isOccupiedVehicleSeat()) {
				capacity /= 4;
			}

			if (self.getVehiclePart() != null) {
				return Math.min(capacity, 1000);
			}

			int maxCapacity = self.getContainingItem() != null ? Options.getInventoryItemCapacity(capacity) : Options.getDefaultContainerCapacity(capacity);
			return Math.min(capacity, maxCapacity);
		}
	}

	@Patch(className = "zombie.inventory.types.InventoryContainer", methodName = "getCapacity", isAdvice = false)
	public static class Patch_InventoryContainer_getCapacity {
		@Patch.RuntimeType
		public static int getCapacity(@Patch.This InventoryContainer self, @Patch.SuperCall Callable<Integer> original) throws Exception {
			if (!Options.isCapacityPatchEnabled()) {
				return original.call();
			}

			int capacity = self.getInventory().getCapacity();
			int limit = Options.getInventoryItemCapacity(capacity);
			if (capacity > limit - self.getActualWeight()) {
				capacity = (int)(limit - self.getActualWeight());
			}

			return capacity;
		}
	}

	@Patch(className = "zombie.inventory.types.InventoryContainer", methodName = "getEffectiveCapacity", isAdvice = false)
	public static class Patch_InventoryContainer_getEffectiveCapacity {
		@Patch.RuntimeType
		public static int getEffectiveCapacity(@Patch.This InventoryContainer self, @Patch.Argument(0) IsoGameCharacter chr, @Patch.SuperCall Callable<Integer> original) throws Exception {
			if (!Options.isCapacityPatchEnabled()) {
				return original.call();
			}

			int capacity = self.getInventory().getEffectiveCapacity(chr);
			int limit = Options.getInventoryItemCapacity(capacity);
			if (capacity > limit - self.getActualWeight()) {
				capacity = (int)(limit - self.getActualWeight());
			}

			return capacity;
		}
	}
}
