package com.enginefixesandtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.characters.skills.PerkFactory;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponType;
import zombie.scripting.objects.WeaponCategory;

import java.util.concurrent.Callable;

/**
 * Fix the vanilla weapon-level calculation for non-Axe melee weapons.
 *
 * Vanilla starts the accumulator at -1 and only assigns the Axe skill
 * directly.  Every other supported weapon category therefore reports one
 * level below the character's actual perk level.
 */
public final class Patch_WeaponLevel {
	private Patch_WeaponLevel() {
	}

	private static int getWeaponLevel(IsoGameCharacter character, HandWeapon weapon) {
		if (character == null || weapon == null) {
			return 0;
		}

		WeaponType weaponType = WeaponType.getWeaponType(weapon);
		if (weaponType == null || weaponType == WeaponType.UNARMED) {
			return 0;
		}

		int level = 0;
		if (weapon.isOfWeaponCategory(WeaponCategory.AXE)) {
			level = character.getPerkLevel(PerkFactory.Perks.Axe);
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

	@Patch(className = "zombie.characters.IsoGameCharacter", methodName = "getWeaponLevel", isAdvice = false)
	public static final class Patch_IsoGameCharacter_getWeaponLevel {
		@Patch.RuntimeType
		public static int getWeaponLevel(@Patch.This IsoGameCharacter self, @Patch.Argument(0) HandWeapon weapon, @Patch.SuperCall Callable<Integer> original) throws Exception {
            if (!Options.isWeaponLevelPatchEnabled()) {
				return original.call();
			}

			if (weapon == null) {
				weapon = self.getPrimaryHandItem() instanceof HandWeapon handWeapon ? handWeapon : null;
			}

			return Patch_WeaponLevel.getWeaponLevel(self, weapon);
		}
	}

	@Patch(className = "zombie.inventory.InventoryItem", methodName = "getWeaponLevel", isAdvice = false)
	public static final class Patch_InventoryItem_getWeaponLevel {
		@Patch.RuntimeType
		public static int getWeaponLevel(@Patch.This InventoryItem self, @Patch.SuperCall Callable<Integer> original) throws Exception {
            if (!Options.isWeaponLevelPatchEnabled()) {
				return original.call();
			}

			if (!self.isEquipped() || !(self instanceof HandWeapon weapon)) {
				return 0;
			}

			return Patch_WeaponLevel.getWeaponLevel(self.getUser(), weapon);
		}
	}
}
