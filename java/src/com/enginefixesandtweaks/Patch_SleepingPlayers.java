package com.enginefixesandtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

/**
 * Prevent local aim input from waking a remote sleeping player.
 *
 * IsoPlayer.processWakingUp() runs for remote players on a multiplayer client, but vanilla pressedAim() reads the
 * local keyboard/mouse binding without checking whether the player owns that input.
 */
public final class Patch_SleepingPlayers {
	private Patch_SleepingPlayers() {
	}

	@Patch(className = "zombie.characters.IsoPlayer", methodName = "pressedAim")
	public static final class Patch_IsoPlayer_pressedAim {
		@Patch.OnExit
		public static void exit(@Patch.This IsoPlayer self, @Patch.Return(readOnly = false) boolean result) {
			if (GameClient.client && !self.isLocal()) {
				result = false;
			}
		}
	}
}
