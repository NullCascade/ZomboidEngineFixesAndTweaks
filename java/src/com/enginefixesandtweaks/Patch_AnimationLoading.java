package com.enginefixesandtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.ZomboidFileSystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps ZomboidFileSystem prefix validation safe while mods and animation assets are being reloaded.
 *
 * The game owns activeFileMap and uses a plain HashMap for it. Animation tasks read it indirectly from worker threads
 * while Reset/init/loadMod can mutate it on the game thread. This patch copies the active absolute paths while
 * lifecycle operations are serialized, and validation only reads the immutable copy.
 */
public final class Patch_AnimationLoading {
	/*
	 * Advice is inlined into zombie.ZomboidFileSystem. These members therefore need to be public so the injected
	 * bytecode can access them at runtime.
	 */
	public static final ReentrantLock FILE_SYSTEM_LOCK = new ReentrantLock();
	public static final ThreadLocal<Integer> LOAD_MODS_DEPTH = ThreadLocal.withInitial(() -> 0);
	public static volatile Set<String> activeFilePaths = Set.of();

	private Patch_AnimationLoading() {
	}

	/**
	 * Serialize the vanilla folder scan. Without this, two lazy-prefix
	 * evaluations can observe modFolders while another call is still filling it.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "getAllModFolders")
	public static final class Patch_ZomboidFileSystem_getAllModFolders {
		@Patch.OnEnter
		public static void enter() {
			FILE_SYSTEM_LOCK.lock();
		}

		@Patch.OnExit(onThrowable = Throwable.class)
		public static void exit() {
			FILE_SYSTEM_LOCK.unlock();
		}
	}

	/**
	 * Keep the active-file snapshot in step with the base files loaded by init.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "init")
	public static final class Patch_ZomboidFileSystem_init {
		@Patch.OnEnter
		public static void enter() {
			FILE_SYSTEM_LOCK.lock();
		}

		@Patch.OnExit(onThrowable = Throwable.class)
		public static void exit(@Patch.This ZomboidFileSystem self, @Patch.Thrown Throwable thrown) {
			try {
				if (thrown == null) {
					publishActiveFileSnapshot(self);
				} else {
					activeFilePaths = Set.of();
				}
			} finally {
				FILE_SYSTEM_LOCK.unlock();
			}
		}
	}

	/**
	 * Never leave paths from the previous reload generation in the fallback.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "Reset")
	public static final class Patch_ZomboidFileSystem_Reset {
		@Patch.OnEnter
		public static void enter() {
			FILE_SYSTEM_LOCK.lock();
		}

		@Patch.OnExit(onThrowable = Throwable.class)
		public static void exit() {
			try {
				activeFilePaths = Set.of();
			} finally {
				FILE_SYSTEM_LOCK.unlock();
			}
		}
	}

	/**
	 * Keep individual mod loads serialized. The snapshot is intentionally not copied here: loadMods invokes this
	 * method once per mod, so copying the entire activeFileMap at this point would turn startup into an O(mods * files)
	 * operation. The outer loadMods advice publishes one snapshot after the complete batch instead.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "loadMod")
	public static final class Patch_ZomboidFileSystem_loadMod {
		@Patch.OnEnter
		public static void enter() {
			FILE_SYSTEM_LOCK.lock();
		}

		@Patch.OnExit(onThrowable = Throwable.class)
		public static void exit(@Patch.This ZomboidFileSystem self, @Patch.Thrown Throwable thrown) {
			try {
				if (thrown == null && LOAD_MODS_DEPTH.get() == 0) {
					// Support callers that invoke loadMod directly outside the normal loadMods batch.
					refreshAfterModsLoaded(self);
				}
			} finally {
				FILE_SYSTEM_LOCK.unlock();
			}
		}
	}

	/**
	 * Rebuild the vanilla lazy-prefix input after the complete loadMods pass. The advice matches both loadMods
	 * overloads; the depth guard avoids doing this twice when the String overload calls the ArrayList one.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "loadMods")
	public static final class Patch_ZomboidFileSystem_loadMods {
		@Patch.OnEnter
		public static void enter() {
			FILE_SYSTEM_LOCK.lock();
			int depth = LOAD_MODS_DEPTH.get();
			LOAD_MODS_DEPTH.set(depth + 1);
		}

		@Patch.OnExit(onThrowable = Throwable.class)
		public static void exit(@Patch.This ZomboidFileSystem self, @Patch.Thrown Throwable thrown) {
			try {
				int depth = LOAD_MODS_DEPTH.get() - 1;
				LOAD_MODS_DEPTH.set(depth);
				if (thrown == null && depth == 0) {
					refreshAfterModsLoaded(self);
				}
			} finally {
				FILE_SYSTEM_LOCK.unlock();
			}
		}
	}

	/**
	 * Keep vanilla's reset operation serialized with validation and folder scans. The vanilla method resets
	 * allowedPrefixes itself.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "resetModFolders")
	public static final class Patch_ZomboidFileSystem_resetModFolders {
		@Patch.OnEnter
		public static void enter() {
			FILE_SYSTEM_LOCK.lock();
		}

		@Patch.OnExit(onThrowable = Throwable.class)
		public static void exit() {
			FILE_SYSTEM_LOCK.unlock();
		}
	}

	/**
	 * Vanilla remains the first authority. The fallback is only for an exact active-file path when the vanilla lazy
	 * prefix list rejected that path.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "validatePrefix")
	public static final class Patch_ZomboidFileSystem_validatePrefix {
		@Patch.OnExit(onThrowable = Throwable.class)
		public static void exit(@Patch.Argument(0) String input, @Patch.Thrown(readOnly = false) Throwable thrown) {
			if (thrown instanceof IllegalArgumentException exception && isInvalidPrefixException(exception) && isRegisteredActiveFile(input)) {
				thrown = null;
			}
		}
	}

	public static void publishActiveFileSnapshot(ZomboidFileSystem fileSystem) {
		Set<String> paths = new HashSet<>();
		for (Map.Entry<String, String> entry : fileSystem.activeFileMap.entrySet()) {
			if (!isAnimationPath(entry.getKey())) {
				continue;
			}

			String activePath = entry.getValue();
			String normalizedPath = normalize(activePath);
			if (normalizedPath != null) {
				paths.add(normalizedPath);
			}
		}

		activeFilePaths = Collections.unmodifiableSet(paths);
	}

	public static void refreshAfterModsLoaded(ZomboidFileSystem fileSystem) {
		// Do not force vanilla's lazy allowed-prefix value here. Its first initialization recursively registers every
		// mod media directory with DebugFileWatcher, and vanilla intentionally defers that work until it is needed.
		publishActiveFileSnapshot(fileSystem);
	}

	public static boolean isInvalidPrefixException(IllegalArgumentException exception) {
		String message = exception.getMessage();
		return message != null && message.startsWith("Invalid prefix found for:");
	}

	public static boolean isRegisteredActiveFile(String input) {
		String normalizedInput = normalize(input);
		if (normalizedInput == null || !isAnimationPath(normalizedInput) || !activeFilePaths.contains(normalizedInput)) {
			return false;
		}

		try {
			return Files.isRegularFile(Path.of(input));
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public static boolean isAnimationPath(String normalizedPath) {
		String path = normalizedPath.replace('\\', '/');
		boolean isAnimationDirectory = path.startsWith("media/anims/") || path.startsWith("media/anims_x/") || path.contains("/media/anims/") || path.contains("/media/anims_x/");
		boolean isAnimationFormat = path.endsWith(".x") || path.endsWith(".fbx") || path.endsWith(".glb") || path.endsWith(".txt");
		return isAnimationDirectory && isAnimationFormat;
	}

	public static String normalize(String path) {
		if (path == null) {
			return null;
		}

		try {
			return Path.of(path).normalize().toAbsolutePath().normalize().toString().toLowerCase(Locale.ENGLISH);
		} catch (RuntimeException exception) {
			return null;
		}
	}
}
