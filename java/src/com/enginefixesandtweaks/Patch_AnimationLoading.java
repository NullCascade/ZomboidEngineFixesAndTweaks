package com.enginefixesandtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.ZomboidFileSystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Keeps ZomboidFileSystem prefix validation safe while mods and animation assets are being reloaded.
 *
 * The game owns activeFileMap and uses a plain HashMap for it. Animation tasks read it indirectly from worker threads
 * while Reset/init/loadMod can mutate it on the game thread. This patch copies the active absolute paths while
 * lifecycle operations are serialized, and validation only reads the immutable copy.
 */
public final class Patch_AnimationLoading {
	private static final Object FILE_SYSTEM_LOCK = new Object();
	private static final ThreadLocal<Integer> LOAD_MODS_DEPTH = ThreadLocal.withInitial(() -> 0);
	private static volatile Set<String> activeFilePaths = Set.of();

	private Patch_AnimationLoading() {
	}

	/**
	 * Serialize the vanilla folder scan. Without this, two lazy-prefix
	 * evaluations can observe modFolders while another call is still filling it.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "getAllModFolders", isAdvice = false)
	public static final class Patch_ZomboidFileSystem_getAllModFolders {
		@Patch.RuntimeType
		public static void getAllModFolders(@Patch.This ZomboidFileSystem self, @Patch.Argument(0) List<String> output, @Patch.SuperCall Runnable original) {
			synchronized (FILE_SYSTEM_LOCK) {
				original.run();
			}
		}
	}

	/**
	 * Keep the active-file snapshot in step with the base files loaded by init.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "init", isAdvice = false)
	public static final class Patch_ZomboidFileSystem_init {
		@Patch.RuntimeType
		public static void init(@Patch.This ZomboidFileSystem self, @Patch.SuperCall Callable<?> original) throws Exception {
			synchronized (FILE_SYSTEM_LOCK) {
				try {
					original.call();
					publishActiveFileSnapshot(self);
				} catch (Exception exception) {
					activeFilePaths = Set.of();
					throw exception;
				}
			}
		}
	}

	/**
	 * Never leave paths from the previous reload generation in the fallback.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "Reset", isAdvice = false)
	public static final class Patch_ZomboidFileSystem_Reset {
		@Patch.RuntimeType
		public static void reset(@Patch.This ZomboidFileSystem self, @Patch.SuperCall Runnable original) {
			synchronized (FILE_SYSTEM_LOCK) {
				try {
					original.run();
				} finally {
					activeFilePaths = Set.of();
				}
			}
		}
	}

	/**
	 * Publish each completed mod load. This also protects the snapshot copy from concurrent mutation of the game's HashMap.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "loadMod", isAdvice = false)
	public static final class Patch_ZomboidFileSystem_loadMod {
		@Patch.RuntimeType
		public static void loadMod(@Patch.This ZomboidFileSystem self, @Patch.Argument(0) String modId, @Patch.SuperCall Runnable original) {
			synchronized (FILE_SYSTEM_LOCK) {
				original.run();
				publishActiveFileSnapshot(self);
				if (LOAD_MODS_DEPTH.get() == 0) {
					refreshAfterModsLoaded(self);
				}
			}
		}
	}

	/**
	 * Rebuild the vanilla lazy-prefix input after the complete loadMods pass. The delegation signature matches both
	 * loadMods overloads; the depth guard avoids doing this twice when the String overload calls the ArrayList one.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "loadMods", isAdvice = false)
	public static final class Patch_ZomboidFileSystem_loadMods {
		@Patch.RuntimeType
		public static void loadMods(@Patch.This ZomboidFileSystem self, @Patch.SuperCall Runnable original) {
			synchronized (FILE_SYSTEM_LOCK) {
				int depth = LOAD_MODS_DEPTH.get();
				LOAD_MODS_DEPTH.set(depth + 1);
				boolean succeeded = false;
				try {
					original.run();
					succeeded = true;
				} finally {
					LOAD_MODS_DEPTH.set(depth);
				}

				if (succeeded && depth == 0) {
					refreshAfterModsLoaded(self);
				}
			}
		}
	}

	/**
	 * Keep vanilla's reset operation serialized with validation and folder scans. The vanilla method resets allowedPrefixes
	 * itself.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "resetModFolders", isAdvice = false)
	public static final class Patch_ZomboidFileSystem_resetModFolders {
		@Patch.RuntimeType
		public static void resetModFolders(@Patch.This ZomboidFileSystem self, @Patch.SuperCall Runnable original) {
			synchronized (FILE_SYSTEM_LOCK) {
				original.run();
			}
		}
	}

	/**
	 * Vanilla remains the first authority. The fallback is only for an exact active-file path when the vanilla lazy
	 * prefix list rejected that path.
	 */
	@Patch(className = "zombie.ZomboidFileSystem", methodName = "validatePrefix", isAdvice = false)
	public static final class Patch_ZomboidFileSystem_validatePrefix {
		@Patch.RuntimeType
		public static void validatePrefix(@Patch.This ZomboidFileSystem self, @Patch.Argument(0) String input, @Patch.SuperCall Callable<?> original) throws Exception {
			try {
				synchronized (FILE_SYSTEM_LOCK) {
					original.call();
				}
			} catch (IllegalArgumentException exception) {
				if (isInvalidPrefixException(exception) && isRegisteredActiveFile(input)) {
					return;
				}

				throw exception;
			}
		}
	}

	private static void publishActiveFileSnapshot(ZomboidFileSystem fileSystem) {
		Set<String> paths = new HashSet<>();
		for (String activePath : fileSystem.activeFileMap.values()) {
			String normalizedPath = normalize(activePath);
			if (normalizedPath != null) {
				paths.add(normalizedPath);
			}
		}

		activeFilePaths = Collections.unmodifiableSet(paths);
	}

	private static void refreshAfterModsLoaded(ZomboidFileSystem fileSystem) {
		// loadMod changes active files but vanilla does not invalidate the lazy prefix value after every load.
		fileSystem.resetModFolders();
		fileSystem.getAllModFolders(new ArrayList<>());
		publishActiveFileSnapshot(fileSystem);

		// Force the lazy vanilla value to be created on the load thread, after the complete mod folder list is
		// available, rather than in an animation worker. The base directory is always an allowed vanilla prefix.
		if (fileSystem.base.absoluteFile != null) {
			fileSystem.validatePrefix(fileSystem.base.absoluteFile.getAbsolutePath());
		}
	}

	private static boolean isInvalidPrefixException(IllegalArgumentException exception) {
		String message = exception.getMessage();
		return message != null && message.startsWith("Invalid prefix found for:");
	}

	private static boolean isRegisteredActiveFile(String input) {
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

	private static boolean isAnimationPath(String normalizedPath) {
		String path = normalizedPath.replace('\\', '/');
		boolean isAnimationDirectory = path.contains("/media/anims/") || path.contains("/media/anims_x/");
		boolean isAnimationFormat = path.endsWith(".x") || path.endsWith(".fbx") || path.endsWith(".glb") || path.endsWith(".txt");
		return isAnimationDirectory && isAnimationFormat;
	}

	private static String normalize(String path) {
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
