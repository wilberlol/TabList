package hu.montlikadani.tablist.utils;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class Util {

	private static final boolean MINIMESSAGE_SUPPORTED;

	private static String legacyNmsVersion;

	static {
		boolean supported;

		// Extra check as some library shading only classes without methods or idk
		try {
			Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
			Class.forName("net.kyori.adventure.text.minimessage.MiniMessage").getDeclaredMethod("miniMessage");
			supported = true;
		} catch (ClassNotFoundException | NoSuchMethodException cn) {
			supported = false;
		}

		MINIMESSAGE_SUPPORTED = supported;
	}

	public static void consolePrint(Level level, Plugin plugin, String text, Object... params) {
		if (hu.montlikadani.tablist.config.constantsLoader.ConfigValues.isLogConsole()) {
			printTrace(level, plugin, text, params);
		}
	}

	public static void printTrace(Level loggingLevel, Plugin plugin, String text, Object... parameters) {
		plugin.getLogger().log(loggingLevel, text, parameters);
	}

	public static String legacyNmsVersion() {
		if (legacyNmsVersion == null) {
			String[] parts = Bukkit.getServer().getClass().getPackage().getName().split("\\.", 4);

			// Minecraft 26.x+ has no version in the CraftBukkit package (org.bukkit.craftbukkit); callers only use
			// this for legacy class lookups, so a placeholder is fine there.
			legacyNmsVersion = parts.length > 3 ? parts[3] : "unversioned";
		}

		return legacyNmsVersion;
	}

	public static String applyTextFormat(String value) {
		return applyTextFormat(value, true);
	}

	@SuppressWarnings("deprecation")
	public static String applyTextFormat(String value, boolean applyLegacyColours) {
		if (MINIMESSAGE_SUPPORTED) {
			value = value.replace("&", "-{-}-").replace("§", "-{-}-");

			value = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
					.serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value))
					.replace("-{-}-", "&");
		}

		return applyLegacyColours ? org.bukkit.ChatColor.translateAlternateColorCodes('&', value) : value;
	}

	public static Optional<UUID> tryParseId(String uuid) {
		if (uuid == null) {
			return Optional.empty();
		}

		try {
			return Optional.of(UUID.fromString(uuid));
		} catch (IllegalArgumentException ignored) {
		}

		return Optional.empty();
	}
}
