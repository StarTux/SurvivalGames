package com.cavetale.survivalgames;

import java.util.Date;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Data @RequiredArgsConstructor
public final class SurvivalPlayer {
    public enum Type { PLAYER, SPECTATOR; }
    protected final UUID uuid;
    protected Type type = Type.SPECTATOR;
    protected boolean ready = false;
    protected Location spawnLocation = null;
    protected Location safeLocation = null;
    protected long discTicks = 0;
    protected String name = "";
    protected UUID lastDamager = null;
    protected int kills = 0;
    protected Date startTime;
    protected Date endTime;
    protected boolean winner = false;
    protected boolean didPlay = false;
    protected double health;

    boolean isPlayer() {
        return type == Type.PLAYER;
    }

    boolean isSpectator() {
        return type == Type.SPECTATOR;
    }

    void setPlayer() {
        type = Type.PLAYER;
        didPlay = true;
    }

    void setSpectator() {
        type = Type.SPECTATOR;
    }

    void addKills(final int moreKills) {
        kills += moreKills;
    }

    Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    boolean isOnline() {
        return getPlayer() != null;
    }
}
