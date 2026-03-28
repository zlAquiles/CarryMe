package net.aquiles.carryme.command;

import net.aquiles.carryme.config.ConfigKeys;
import net.aquiles.carryme.service.CarryRequestService;
import net.aquiles.carryme.service.MessageService;
import net.aquiles.carryme.service.UpdateChecker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CarryCommandHandler implements CommandExecutor, TabCompleter, Listener {

    private static final List<String> MANAGED_COMMANDS = List.of("carryme", "cargar", "aceptar", "rechazar", "soltar");

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final CarryRequestService carryRequestService;
    private final UpdateChecker updateChecker;

    public CarryCommandHandler(JavaPlugin plugin, MessageService messageService, CarryRequestService carryRequestService, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.carryRequestService = carryRequestService;
        this.updateChecker = updateChecker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        return executeCommand(sender, commandName, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return getSuggestions(sender, command.getName().toLowerCase(Locale.ROOT), args);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith("/")) {
            return;
        }

        String[] split = message.substring(1).split("\\s+", -1);
        if (split.length == 0) {
            return;
        }

        String resolvedCommand = resolveConfiguredCommand(split[0]);
        if (resolvedCommand == null || resolvedCommand.equalsIgnoreCase(split[0])) {
            return;
        }

        event.setCancelled(true);
        String[] args = Arrays.copyOfRange(split, 1, split.length);
        executeCommand(event.getPlayer(), resolvedCommand, args);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) {
            return;
        }

        String[] split = buffer.substring(1).split("\\s+", -1);
        if (split.length == 0) {
            return;
        }

        String resolvedCommand = resolveConfiguredCommand(split[0]);
        if (resolvedCommand == null || resolvedCommand.equalsIgnoreCase(split[0])) {
            return;
        }

        String[] args = Arrays.copyOfRange(split, 1, split.length);
        event.setCompletions(getSuggestions(event.getSender(), resolvedCommand, args));
    }

    private boolean executeCommand(CommandSender sender, String commandName, String[] args) {
        if ("carryme".equals(commandName)) {
            handleMainCommand(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.PLAYERS_ONLY));
            return true;
        }

        switch (commandName) {
            case "cargar" -> handleCarry(player, args);
            case "aceptar" -> handleAccept(player);
            case "rechazar" -> handleReject(player);
            case "soltar" -> handleDrop(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleMainCommand(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("carryme.reload")) {
                sender.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.NO_PERMISSION));
                return;
            }

            plugin.reloadConfig();
            messageService.reload();
            updateChecker.checkForUpdates();
            String prefix = messageService.getMessage(ConfigKeys.Settings.PREFIX);
            sender.sendMessage(messageService.getMessage(ConfigKeys.Messages.Actions.RELOADED).replace("%prefix%", prefix));
            return;
        }

        sender.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.ADMIN_USAGE));
    }

    private void handleCarry(Player player, String[] args) {
        if (!player.hasPermission("carryme.cargar")) {
            player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.NO_PERMISSION));
            return;
        }
        carryRequestService.sendCarryRequest(player, args);
    }

    private void handleAccept(Player player) {
        if (!player.hasPermission("carryme.aceptar")) {
            player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.NO_PERMISSION));
            return;
        }
        carryRequestService.acceptRequest(player);
    }

    private void handleDrop(Player player) {
        if (!player.hasPermission("carryme.soltar")) {
            player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.NO_PERMISSION));
            return;
        }
        carryRequestService.dropPassengers(player);
    }

    private void handleReject(Player player) {
        if (!player.hasPermission("carryme.rechazar")) {
            player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.NO_PERMISSION));
            return;
        }
        carryRequestService.rejectRequest(player);
    }

    private List<String> getSuggestions(CommandSender sender, String commandName, String[] args) {
        if ("carryme".equals(commandName)) {
            if (!sender.hasPermission("carryme.reload") || args.length > 1) {
                return Collections.emptyList();
            }
            return filterSuggestions(List.of("reload"), args);
        }

        if (!"cargar".equals(commandName) || !(sender instanceof Player player) || args.length > 1) {
            return Collections.emptyList();
        }

        return filterSuggestions(carryRequestService.suggestPlayerNames(player), args);
    }

    private List<String> filterSuggestions(List<String> suggestions, String[] args) {
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(input))
                .toList();
    }

    private String resolveConfiguredCommand(String input) {
        String normalizedInput = input.toLowerCase(Locale.ROOT);
        if (MANAGED_COMMANDS.contains(normalizedInput)) {
            return normalizedInput;
        }

        for (String commandName : MANAGED_COMMANDS) {
            if (messageService.getStringList(ConfigKeys.CommandAliases.path(commandName)).stream()
                    .map(String::trim)
                    .filter(alias -> !alias.isEmpty())
                    .map(alias -> alias.startsWith("/") ? alias.substring(1) : alias)
                    .map(alias -> alias.toLowerCase(Locale.ROOT))
                    .anyMatch(normalizedInput::equals)) {
                return commandName;
            }
        }
        return null;
    }
}
