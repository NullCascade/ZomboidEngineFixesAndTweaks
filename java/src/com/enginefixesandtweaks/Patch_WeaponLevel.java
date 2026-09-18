package com.enginefixesandtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.skills.PerkFactory;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponType;
import zombie.scripting.objects.WeaponCategory;

/**
 * Fix the vanilla weapon-level calculation for non-Axe melee weapons.
 *
 * Vanilla starts the accumulator at -1 and only assigns the Axe skill directly.  Every other supported weapon
 * category therefore reports one level below the character's actual perk level.
 */
public final class Patch_WeaponLevel {
	private Patch_WeaponLevel() {
	}

	public static int getWeaponLevel(IsoGameCharacter character, HandWeapon weapon) {
		if (character == null || weapon == null) {
			return 0;
		}

		WeaponType weaponType = WeaponType.getWeaponType(weapon);
		if (weaponType == null || weaponType == WeaponType.UNARMED) {
			return 0;
		}

		int level = 0;
		if (weapon.isOfWeaponCategory(WeaponCategory.AXE)) {
			level += character.getPerkLevel(PerkFactory.Perks.Axe);
		}

		if (weapon.isOfWeaponCategory(WeaponCategory.SPEAR)) {
			level += character.getPerkLevel(PerkFactory.Perks.Spear);
		}

		if (weapon.isOfWeaponCategory(WeaponCategory.SMALL_BLADE)) {
			level += character.getPerkLevel(PerkFactory.Perks.SmallBlade);
		}

		if (weapon.isOfWeaponCategory(WeaponCategory.LONG_BLADE)) {
			level += character.getPerkLevel(PerkFactory.Perks.LongBlade);
		}

		if (weapon.isOfWeaponCategory(WeaponCategory.BLUNT)) {
			level += character.getPerkLevel(PerkFactory.Perks.Blunt);
		}

		if (weapon.isOfWeaponCategory(WeaponCategory.SMALL_BLUNT)) {
			level += character.getPerkLevel(PerkFactory.Perks.SmallBlunt);
		}

		return Math.min(level, 10);
	}

	// I don't see a way to replace functions with variable arguments using this API, so we'll have to just consume all
	// versions of the function.
	@Patch(className = "zombie.characters.IsoGameCharacter", methodName = "getWeaponLevel")
	public static final class Patch_IsoGameCharacter_getWeaponLevel {
		@Patch.OnExit
		public static void exit(@Patch.This IsoGameCharacter self, @Patch.AllArguments Object[] arguments, @Patch.Return(readOnly = false) int result) {
			if (!Options.isWeaponLevelPatchEnabled()) {
				return;
			}

			HandWeapon weapon = (arguments.length > 0 && arguments[0] instanceof HandWeapon handWeapon) ? handWeapon : null;
			if (weapon == null && self.getPrimaryHandItem() instanceof HandWeapon handWeapon) {
				weapon = handWeapon;
			}

			result = Patch_WeaponLevel.getWeaponLevel(self, weapon);
		}
	}

	@Patch(className = "zombie.inventory.InventoryItem", methodName = "getWeaponLevel")
	public static final class Patch_InventoryItem_getWeaponLevel {
		@Patch.OnExit
		public static void exit(@Patch.This InventoryItem self, @Patch.Return(readOnly = false) int result) {
			if (!Options.isWeaponLevelPatchEnabled()) {
				return;
			}

			if (self.isEquipped() && self instanceof HandWeapon weapon) {
				result = Patch_WeaponLevel.getWeaponLevel(self.getUser(), weapon);
			}
		}
	}
}
