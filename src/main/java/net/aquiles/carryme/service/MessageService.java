package net.aquiles.carryme.service;

import net.aquiles.carryme.config.ConfigKeys.PathPair;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class MessageService {

    private static final String MESSAGE_FILE_NAME = "message.yml";

    private final JavaPlugin plugin;
    private FileConfiguration messageConfig;
    private File messageFile;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ensureMessageFile();
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
    }

    public String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    public String getMessage(PathPair pathPair) {
        return colorize(getString(pathPair, ""));
    }

    public String getString(PathPair pathPair, String defaultValue) {
        ResolvedPath resolvedPath = resolvePath(pathPair);
        if (resolvedPath != null) {
            return resolvedPath.configuration().getString(resolvedPath.path(), defaultValue);
        }
        return defaultValue;
    }

    public String getString(String path, String defaultValue) {
        FileConfiguration configuration = findConfiguration(path);
        if (configuration != null) {
            return configuration.getString(path, defaultValue);
        }
        return defaultValue;
    }

    public double getDouble(PathPair pathPair, double defaultValue) {
        ResolvedPath resolvedPath = resolvePath(pathPair);
        if (resolvedPath != null) {
            return resolvedPath.configuration().getDouble(resolvedPath.path(), defaultValue);
        }
        return defaultValue;
    }

    public int getInt(PathPair pathPair, int defaultValue) {
        ResolvedPath resolvedPath = resolvePath(pathPair);
        if (resolvedPath != null) {
            return resolvedPath.configuration().getInt(resolvedPath.path(), defaultValue);
        }
        return defaultValue;
    }

    public List<String> getStringList(String path) {
        if (messageConfig != null && messageConfig.isList(path)) {
            return messageConfig.getStringList(path);
        }
        if (plugin.getConfig().isList(path)) {
            return plugin.getConfig().getStringList(path);
        }
        return Collections.emptyList();
    }

    public TextComponent legacyTextComponent(String message) {
        TextComponent component = new TextComponent();
        BaseComponent[] parts = TextComponent.fromLegacyText(colorize(message));
        for (BaseComponent part : parts) {
            component.addExtra(part);
        }
        return component;
    }

    private ResolvedPath resolvePath(PathPair pathPair) {
        FileConfiguration currentConfiguration = findConfiguration(pathPair.current());
        if (currentConfiguration != null) {
            return new ResolvedPath(currentConfiguration, pathPair.current());
        }
        if (pathPair.legacy() != null) {
            FileConfiguration legacyConfiguration = findConfiguration(pathPair.legacy());
            if (legacyConfiguration != null) {
                return new ResolvedPath(legacyConfiguration, pathPair.legacy());
            }
        }
        return null;
    }

    private FileConfiguration findConfiguration(String path) {
        if (messageConfig != null && messageConfig.isSet(path)) {
            return messageConfig;
        }
        if (plugin.getConfig().isSet(path)) {
            return plugin.getConfig();
        }
        return null;
    }

    private void ensureMessageFile() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder for CarryMe.");
        }

        messageFile = new File(plugin.getDataFolder(), MESSAGE_FILE_NAME);
        if (!messageFile.exists()) {
            plugin.saveResource(MESSAGE_FILE_NAME, false);
        }
    }

    private record ResolvedPath(FileConfiguration configuration, String path) {
    }
}
