package net.aquiles.carryme;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.util.HashMap;
import java.util.UUID;

public class CarryMe extends JavaPlugin implements CommandExecutor, Listener {

    private final HashMap<UUID, UUID> requests = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("cargar").setExecutor(this);
        getCommand("aceptar").setExecutor(this);
        getCommand("rechazar").setExecutor(this);
        getCommand("soltar").setExecutor(this);
        getCommand("carryme").setExecutor(this);

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CarryMe por Aquiles activado con éxito.");
    }

    private String getMsg(String path) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString(path, ""));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("carryme")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload") && player.hasPermission("carryme.reload")) {
                reloadConfig();
                String prefix = getMsg("Configuracion.prefijo");
                player.sendMessage(getMsg("Textos.Acciones.recargado").replace("%prefijo%", prefix));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("cargar")) {
            if (!player.hasPermission("carryme.cargar")) {
                player.sendMessage(getMsg("Textos.Errores.sin-permiso"));
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(getMsg("Textos.Errores.uso-incorrecto"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMsg("Textos.Errores.no-encontrado"));
                return true;
            }
            if (target.equals(player)) {
                player.sendMessage(getMsg("Textos.Errores.auto-carga"));
                return true;
            }

            double maxDist = getConfig().getDouble("Configuracion.distancia-maxima");
            if (!target.getWorld().equals(player.getWorld()) || target.getLocation().distance(player.getLocation()) > maxDist) {
                player.sendMessage(getMsg("Textos.Errores.muy-lejos").replace("%distancia%", String.valueOf(maxDist)));
                return true;
            }

            requests.put(target.getUniqueId(), player.getUniqueId());
            player.sendMessage(getMsg("Textos.Peticiones.enviada").replace("%target%", target.getName()));

            TextComponent baseMsg = new TextComponent(getMsg("Textos.Peticiones.recibida").replace("%player%", player.getName()));

            TextComponent acceptBtn = new TextComponent(getMsg("Textos.Peticiones.boton-aceptar") + " ");
            acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/aceptar"));

            TextComponent rejectBtn = new TextComponent(getMsg("Textos.Peticiones.boton-rechazar"));
            rejectBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rechazar"));

            baseMsg.addExtra(acceptBtn);
            baseMsg.addExtra(rejectBtn);
            target.spigot().sendMessage(baseMsg);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (requests.containsKey(target.getUniqueId()) && requests.get(target.getUniqueId()).equals(player.getUniqueId())) {
                    requests.remove(target.getUniqueId());
                    player.sendMessage(getMsg("Textos.Peticiones.expirada"));
                    target.sendMessage(getMsg("Textos.Peticiones.expirada"));
                }
            }, getConfig().getInt("Configuracion.tiempo-expiracion") * 20L);

            return true;
        }

        if (command.getName().equalsIgnoreCase("aceptar")) {
            if (!player.hasPermission("carryme.aceptar")) return true;

            if (!requests.containsKey(player.getUniqueId())) {
                player.sendMessage(getMsg("Textos.Peticiones.expirada"));
                return true;
            }

            Player senderPlayer = Bukkit.getPlayer(requests.get(player.getUniqueId()));
            requests.remove(player.getUniqueId());

            if (senderPlayer == null || !senderPlayer.isOnline()) {
                player.sendMessage(getMsg("Textos.Errores.no-encontrado"));
                return true;
            }

            if (!senderPlayer.getPassengers().isEmpty()) {
                player.sendMessage(getMsg("Textos.Errores.limite-alcanzado"));
                return true;
            }

            senderPlayer.addPassenger(player);
            senderPlayer.sendMessage(getMsg("Textos.Acciones.cargando").replace("%target%", player.getName()));
            player.sendMessage(getMsg("Textos.Acciones.siendo-cargado").replace("%player%", senderPlayer.getName()));

            return true;
        }

        if (command.getName().equalsIgnoreCase("rechazar")) {
            if (requests.containsKey(player.getUniqueId())) {
                Player senderPlayer = Bukkit.getPlayer(requests.get(player.getUniqueId()));
                requests.remove(player.getUniqueId());
                if (senderPlayer != null) {
                    senderPlayer.sendMessage(getMsg("Textos.Peticiones.rechazada").replace("%target%", player.getName()));
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("soltar")) {
            if (!player.hasPermission("carryme.soltar")) return true;

            if (player.getPassengers().isEmpty()) {
                return true;
            }

            player.getPassengers().forEach(passenger -> {
                player.removePassenger(passenger);
                player.sendMessage(getMsg("Textos.Acciones.soltado").replace("%target%", passenger.getName()));
            });
            return true;
        }

        return false;
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player && event.getDismounted() instanceof Player) {
            Player carrier = (Player) event.getDismounted();
            Player passenger = (Player) event.getEntity();
            carrier.sendMessage(getMsg("Textos.Acciones.soltado").replace("%target%", passenger.getName()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
    }
}