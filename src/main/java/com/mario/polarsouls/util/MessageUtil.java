package com.mario.polarsouls.util;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class MessageUtil {

    private static String prefix = "&8[&4☠&8] &r";
    private static final Map<String, String> messages = new HashMap<>();

    private MessageUtil() {}

    public static void loadMessages(FileConfiguration config) {
        prefix = config.getString("messages.prefix", "&8[&4☠&8] &r");

        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (key.equals("prefix")) continue;
                messages.put(key, config.getString("messages." + key, ""));
            }
        }
    }

    public static String getRaw(String key, Object... replacements) {
        String messageContent = messages.getOrDefault(key, "&cMissing message: " + key);

        // Optimized: use StringBuilder for multiple replacements to avoid creating intermediate strings
        if (replacements.length == 0) {
            return messageContent;
        }

        StringBuilder result = new StringBuilder(messageContent);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = "%" + replacements[i] + "%";
            String value = String.valueOf(replacements[i + 1]);

            int index = 0;
            while ((index = result.indexOf(placeholder, index)) != -1) {
                result.replace(index, index + placeholder.length(), value);
                index += value.length();
            }
        }
        return result.toString();
    }

    public static String get(String key, Object... replacements) {
        return colorize(prefix + getRaw(key, replacements));
    }

    public static String getNoPrefix(String key, Object... replacements) {
        return colorize(getRaw(key, replacements));
    }

    public static String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
