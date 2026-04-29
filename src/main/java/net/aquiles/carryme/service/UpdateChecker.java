package net.aquiles.carryme.service;

import net.aquiles.carryme.config.ConfigKeys;
import net.aquiles.carryme.util.PlatformScheduler;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker implements Listener {

    private static final String MODRINTH_PROJECT_ID = "Mt9b9Yn9";
    private static final String VERSION_CHECK_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version?include_changelog=false";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/carryme";
    private static final String DEFAULT_CONSOLE_LATEST_MESSAGE = "&8[&bCarryMe&8] &aYou are running the latest version!";
    private static final String DEFAULT_CONSOLE_AVAILABLE_MESSAGE = "&8[&bCarryMe&8] &aNew update is available &7Version: &f%current% &7| &7New version: &f%new%";
    private static final String DEFAULT_PLAYER_AVAILABLE_MESSAGE = "&8[&bCarryMe&8] &aUpdate available! &7(&f%current% &7-> &f%new%&7)";
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\"version_number\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final PlatformScheduler platformScheduler;
    private volatile String latestAvailableVersion;

    public UpdateChecker(JavaPlugin plugin, MessageService messageService, PlatformScheduler platformScheduler) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.platformScheduler = platformScheduler;
    }

    public void checkForUpdates() {
        platformScheduler.runAsync(() -> {
            try {
                String latestVersion = fetchLatestVersion();
                if (latestVersion.isEmpty()) {
                    return;
                }

                platformScheduler.runGlobal(() -> handleCheckedVersion(latestVersion));
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not check for CarryMe updates: " + exception.getMessage());
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission("carryme.update")) {
            sendUpdateNotification(event.getPlayer());
        }
    }

    public void sendUpdateNotification(Player player) {
        if (latestAvailableVersion == null || latestAvailableVersion.isEmpty()) {
            return;
        }

        List<String> lines = messageService.getStringList(ConfigKeys.UpdateCheck.AVAILABLE);
        if (lines.isEmpty()) {
            sendClickablePlayerMessage(player);
            return;
        }

        for (String rawLine : lines) {
            String parsedLine = applyVersionPlaceholders(rawLine);
            player.sendMessage(messageService.colorize(parsedLine));
        }
    }

    private void notifyOnlineOperators() {
        if (latestAvailableVersion == null || latestAvailableVersion.isEmpty()) {
            return;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("carryme.update")) {
                platformScheduler.executeEntity(onlinePlayer, () -> sendUpdateNotification(onlinePlayer));
            }
        }
    }

    private void handleCheckedVersion(String latestVersion) {
        String currentVersion = plugin.getDescription().getVersion();
        ConsoleCommandSender console = Bukkit.getConsoleSender();

        if (isNewerVersion(currentVersion, latestVersion)) {
            latestAvailableVersion = latestVersion;
            console.sendMessage(messageService.colorize(
                    applyVersionPlaceholders(DEFAULT_CONSOLE_AVAILABLE_MESSAGE)
            ));
            notifyOnlineOperators();
            return;
        }

        latestAvailableVersion = null;
        console.sendMessage(messageService.colorize(
                applyVersionPlaceholders(DEFAULT_CONSOLE_LATEST_MESSAGE)
        ));
    }

    private void sendClickablePlayerMessage(Player player) {
        TextComponent message = messageService.legacyTextComponent(
                applyVersionPlaceholders(messageService.getString(
                        ConfigKeys.UpdateCheck.AVAILABLE,
                        DEFAULT_PLAYER_AVAILABLE_MESSAGE
                ))
        );
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, MODRINTH_PROJECT_URL));
        player.spigot().sendMessage(message);
    }

    private String applyVersionPlaceholders(String message) {
        String currentVersion = plugin.getDescription().getVersion();
        return message
                .replace("%current_version%", currentVersion)
                .replace("%latest_version%", latestAvailableVersion == null ? "" : latestAvailableVersion)
                .replace("%current%", currentVersion)
                .replace("%new%", latestAvailableVersion == null ? "" : latestAvailableVersion);
    }

    private String fetchLatestVersion() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(VERSION_CHECK_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "zlAquiles/CarryMe/" + plugin.getDescription().getVersion() + " (Modrinth update checker)");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected response code: " + responseCode);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return findLatestVersionNumber(response.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    private String findLatestVersionNumber(String responseBody) {
        Matcher matcher = VERSION_NUMBER_PATTERN.matcher(responseBody);
        String latestVersion = "";
        while (matcher.find()) {
            String version = unescapeJsonString(matcher.group(1)).trim();
            if (version.isEmpty()) {
                continue;
            }
            if (latestVersion.isEmpty() || compareVersions(normalizeVersion(version), normalizeVersion(latestVersion)) > 0) {
                latestVersion = version;
            }
        }
        return latestVersion;
    }

    private String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private boolean isNewerVersion(String currentVersion, String latestVersion) {
        return compareVersions(normalizeVersion(latestVersion), normalizeVersion(currentVersion)) > 0;
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim().replaceFirst("^[vV]", "");
    }

    private int compareVersions(String firstVersion, String secondVersion) {
        String[] firstParts = firstVersion.split("[.-]");
        String[] secondParts = secondVersion.split("[.-]");
        int maxLength = Math.max(firstParts.length, secondParts.length);

        for (int index = 0; index < maxLength; index++) {
            String firstPart = index < firstParts.length ? firstParts[index] : "0";
            String secondPart = index < secondParts.length ? secondParts[index] : "0";
            int comparison = compareVersionPart(firstPart, secondPart);
            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    private int compareVersionPart(String firstPart, String secondPart) {
        boolean firstNumeric = firstPart.matches("\\d+");
        boolean secondNumeric = secondPart.matches("\\d+");

        if (firstNumeric && secondNumeric) {
            return Integer.compare(Integer.parseInt(firstPart), Integer.parseInt(secondPart));
        }
        if (firstNumeric) {
            return 1;
        }
        if (secondNumeric) {
            return -1;
        }
        return firstPart.compareToIgnoreCase(secondPart);
    }
}
