package net.aquiles.carryme.compat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class CompatibilityManager {

    private final JavaPlugin plugin;
    private final List<CarryBlockerHook> hooks = new ArrayList<>();

    public CompatibilityManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadHooks();
    }

    public boolean blocksCarry(Player player) {
        for (CarryBlockerHook hook : hooks) {
            if (hook.blocksCarry(player)) {
                return true;
            }
        }
        return false;
    }

    private void loadHooks() {
        Plugin gsit = plugin.getServer().getPluginManager().getPlugin("GSit");
        if (gsit != null && gsit.isEnabled()) {
            GSitHook gsitHook = new GSitHook(plugin);
            if (gsitHook.isAvailable()) {
                hooks.add(gsitHook);
                plugin.getLogger().info("GSit compatibility hook enabled.");
            }
        }
    }
}