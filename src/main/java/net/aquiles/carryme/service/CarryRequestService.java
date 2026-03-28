package net.aquiles.carryme.service;

import net.aquiles.carryme.compat.CompatibilityManager;
import net.aquiles.carryme.config.ConfigKeys;
import net.aquiles.carryme.util.PlatformScheduler;
import net.aquiles.carryme.util.ScheduledTaskHandle;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CarryRequestService implements Listener {

    private final MessageService messageService;
    private final CompatibilityManager compatibilityManager;
    private final PlatformScheduler platformScheduler;
    private final Map<UUID, UUID> requests = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTaskHandle> expirationTasks = new ConcurrentHashMap<>();

    public CarryRequestService(MessageService messageService, CompatibilityManager compatibilityManager, PlatformScheduler platformScheduler) {
        this.messageService = messageService;
        this.compatibilityManager = compatibilityManager;
        this.platformScheduler = platformScheduler;
    }

    public void sendCarryRequest(Player requester, String[] args) {
        if (!platformScheduler.canAccessEntity(requester)) {
            platformScheduler.executeEntity(requester, () -> sendCarryRequest(requester, args));
            return;
        }

        if (args.length == 0) {
            requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.USAGE));
            return;
        }

        double maxDistance = messageService.getDouble(ConfigKeys.Settings.MAX_DISTANCE, 5.0D);
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.PLAYER_NOT_FOUND));
            return;
        }

        if (target.equals(requester)) {
            requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.SELF_CARRY));
            return;
        }

        if (!platformScheduler.canAccessEntity(target)) {
            requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.TOO_FAR)
                    .replace("%distance%", String.valueOf(maxDistance)));
            return;
        }

        if (isUnavailable(requester) || isUnavailable(target)) {
            requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.OCCUPIED));
            return;
        }

        if (!target.getWorld().equals(requester.getWorld()) || target.getLocation().distance(requester.getLocation()) > maxDistance) {
            requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.TOO_FAR)
                    .replace("%distance%", String.valueOf(maxDistance)));
            return;
        }

        UUID targetId = target.getUniqueId();
        UUID requesterId = requester.getUniqueId();
        UUID previousRequester = requests.put(targetId, requesterId);
        cancelExpirationTask(targetId);

        if (previousRequester != null && !previousRequester.equals(requesterId)) {
            Player previousPlayer = Bukkit.getPlayer(previousRequester);
            if (previousPlayer != null && previousPlayer.isOnline()) {
                platformScheduler.executeEntity(previousPlayer, () ->
                        previousPlayer.sendMessage(messageService.getMessage(ConfigKeys.Messages.Requests.EXPIRED))
                );
            }
        }

        requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Requests.SENT)
                .replace("%target%", target.getName()));

        TextComponent baseMessage = messageService.legacyTextComponent(
                messageService.getMessage(ConfigKeys.Messages.Requests.RECEIVED)
                        .replace("%player%", requester.getName())
        );

        TextComponent acceptButton = messageService.legacyTextComponent(
                messageService.getMessage(ConfigKeys.Messages.Requests.ACCEPT_BUTTON) + " "
        );
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getPreferredCommandTrigger("aceptar")));

        TextComponent rejectButton = messageService.legacyTextComponent(
                messageService.getMessage(ConfigKeys.Messages.Requests.REJECT_BUTTON)
        );
        rejectButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, getPreferredCommandTrigger("rechazar")));

        baseMessage.addExtra(acceptButton);
        baseMessage.addExtra(rejectButton);
        platformScheduler.executeEntity(target, () -> target.spigot().sendMessage(baseMessage));

        long delayTicks = messageService.getInt(ConfigKeys.Settings.REQUEST_EXPIRATION_SECONDS, 20) * 20L;
        ScheduledTaskHandle task = platformScheduler.runEntityLater(target, delayTicks, () -> expireRequest(targetId, requesterId));
        expirationTasks.put(targetId, task);
    }

    public void acceptRequest(Player player) {
        if (!platformScheduler.canAccessEntity(player)) {
            platformScheduler.executeEntity(player, () -> acceptRequest(player));
            return;
        }

        UUID requesterId = requests.get(player.getUniqueId());
        if (requesterId == null) {
            player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Requests.EXPIRED));
            return;
        }

        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            clearRequest(player.getUniqueId());
            player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Errors.PLAYER_NOT_FOUND));
            return;
        }

        clearRequest(player.getUniqueId());
        platformScheduler.executeEntity(requester, () -> completeAccept(player.getUniqueId(), requesterId));
    }

    public void rejectRequest(Player player) {
        if (!platformScheduler.canAccessEntity(player)) {
            platformScheduler.executeEntity(player, () -> rejectRequest(player));
            return;
        }

        UUID requesterId = requests.get(player.getUniqueId());
        if (requesterId == null) {
            return;
        }

        clearRequest(player.getUniqueId());
        sendMessageToPlayer(requesterId, messageService.getMessage(ConfigKeys.Messages.Requests.REJECTED)
                .replace("%target%", player.getName()));
    }

    public void dropPassengers(Player player) {
        if (!platformScheduler.canAccessEntity(player)) {
            platformScheduler.executeEntity(player, () -> dropPassengers(player));
            return;
        }

        if (player.getPassengers().isEmpty()) {
            return;
        }

        for (Entity passenger : player.getPassengers()) {
            player.removePassenger(passenger);
            player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Actions.DROPPED)
                    .replace("%target%", passenger.getName()));
        }
    }

    public List<String> suggestPlayerNames(Player sender) {
        List<String> names = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.getUniqueId().equals(sender.getUniqueId())) {
                names.add(onlinePlayer.getName());
            }
        }
        return names;
    }

    public void cancelAll() {
        for (ScheduledTaskHandle task : expirationTasks.values()) {
            task.cancel();
        }
        expirationTasks.clear();
        requests.clear();
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player passenger && event.getDismounted() instanceof Player carrier) {
            carrier.sendMessage(messageService.getMessage(ConfigKeys.Messages.Actions.DROPPED)
                    .replace("%target%", passenger.getName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        clearRequest(playerId);
        removeRequestsByRequester(playerId);
    }

    private boolean isUnavailable(Player player) {
        return player.getVehicle() != null || !player.getPassengers().isEmpty() || compatibilityManager.blocksCarry(player);
    }

    private void completeAccept(UUID playerId, UUID requesterId) {
        Player player = Bukkit.getPlayer(playerId);
        Player requester = Bukkit.getPlayer(requesterId);

        if (player == null || !player.isOnline() || requester == null || !requester.isOnline()) {
            sendMessageToPlayer(playerId, messageService.getMessage(ConfigKeys.Messages.Errors.PLAYER_NOT_FOUND));
            return;
        }

        double maxDistance = messageService.getDouble(ConfigKeys.Settings.MAX_DISTANCE, 5.0D);
        if (!platformScheduler.canAccessEntity(requester) || !platformScheduler.canAccessEntity(player)) {
            sendMessageToPlayer(playerId, messageService.getMessage(ConfigKeys.Messages.Errors.TOO_FAR)
                    .replace("%distance%", String.valueOf(maxDistance)));
            return;
        }

        if (!requester.getWorld().equals(player.getWorld()) || requester.getLocation().distance(player.getLocation()) > maxDistance) {
            sendMessageToPlayer(playerId, messageService.getMessage(ConfigKeys.Messages.Errors.TOO_FAR)
                    .replace("%distance%", String.valueOf(maxDistance)));
            return;
        }

        if (isUnavailable(requester) || isUnavailable(player)) {
            sendMessageToPlayer(playerId, messageService.getMessage(ConfigKeys.Messages.Errors.OCCUPIED));
            return;
        }

        requester.addPassenger(player);
        requester.sendMessage(messageService.getMessage(ConfigKeys.Messages.Actions.CARRYING)
                .replace("%target%", player.getName()));
        player.sendMessage(messageService.getMessage(ConfigKeys.Messages.Actions.BEING_CARRIED)
                .replace("%player%", requester.getName()));
    }

    private String getPreferredCommandTrigger(String commandName) {
        List<String> aliases = getConfiguredAliases(commandName);
        return "/" + (aliases.isEmpty() ? commandName : aliases.get(0));
    }

    private List<String> getConfiguredAliases(String commandName) {
        return messageService.getStringList(ConfigKeys.CommandAliases.path(commandName)).stream()
                .map(String::trim)
                .filter(alias -> !alias.isEmpty())
                .map(alias -> alias.startsWith("/") ? alias.substring(1) : alias)
                .toList();
    }

    private void expireRequest(UUID targetId, UUID requesterId) {
        UUID currentRequester = requests.get(targetId);
        if (currentRequester == null || !currentRequester.equals(requesterId)) {
            return;
        }

        clearRequest(targetId);
        sendMessageToPlayer(requesterId, messageService.getMessage(ConfigKeys.Messages.Requests.EXPIRED));
        sendMessageToPlayer(targetId, messageService.getMessage(ConfigKeys.Messages.Requests.EXPIRED));
    }

    private void clearRequest(UUID targetId) {
        requests.remove(targetId);
        cancelExpirationTask(targetId);
    }

    private void removeRequestsByRequester(UUID requesterId) {
        List<UUID> targetsToClear = requests.entrySet().stream()
                .filter(entry -> entry.getValue().equals(requesterId))
                .map(Map.Entry::getKey)
                .toList();
        for (UUID targetId : targetsToClear) {
            clearRequest(targetId);
        }
    }

    private void cancelExpirationTask(UUID targetId) {
        ScheduledTaskHandle task = expirationTasks.remove(targetId);
        if (task != null) {
            task.cancel();
        }
    }

    private void sendMessageToPlayer(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        platformScheduler.executeEntity(player, () -> player.sendMessage(message));
    }
}
