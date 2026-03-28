package net.aquiles.carryme.compat;

import org.bukkit.entity.Player;

public interface CarryBlockerHook {

    String getPluginName();

    boolean blocksCarry(Player player);
}