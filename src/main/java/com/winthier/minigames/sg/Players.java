package com.winthier.minigames.sg;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class Players {
    public static void reset(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        // flight
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFlySpeed(0.1f);
        player.setFallDistance(0.0f);
        // speed
        player.setWalkSpeed(0.2f);
        // bed
        player.setBedSpawnLocation(null, true);
        player.setSleepingIgnored(false);
        // exp
        player.setLevel(0);
        player.setExp(0.0f);
        // scoreboard
        player.setScoreboard(Bukkit.getServer().getScoreboardManager().getMainScoreboard());
        // time
        player.resetPlayerTime();
        player.resetPlayerWeather();
        // health
        player.setHealth(20.0);
        // food
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        // inventory
        player.getInventory().clear();
        player.getEnderChest().clear();
        {
            ItemStack[] items = player.getInventory().getArmorContents();
            for (int i = 0; i < items.length; ++i) items[i] = null;
            player.getInventory().setArmorContents(items);
        }
        player.getInventory().setHeldItemSlot(0);
        // potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setFireTicks(0);
        // vehicle
        if (player.getVehicle() != null) player.getVehicle().remove();
        // NoCheatPlus
        Msg.consoleCommand("ncp removeplayer " + player.getName());
    }
}
