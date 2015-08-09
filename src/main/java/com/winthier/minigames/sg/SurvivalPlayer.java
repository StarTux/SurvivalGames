package com.winthier.minigames.sg;

import java.util.Date;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Data
public class SurvivalPlayer
{
    final SurvivalGames sg;
    static enum Type { PLAYER, SPECTATOR; }
    Type type = Type.PLAYER;
    boolean ready = false;
    Location spawnLocation = null;
    Location safeLocation = null;
    long discTicks = 0;
    final UUID uuid;
    String name = "";
    UUID lastDamager = null;
    int kills = 0;
    Date startTime, endTime;
    boolean winner = false;
    boolean highscoreRecorded = false;
    boolean didPlay = false;

    public SurvivalPlayer(SurvivalGames game, UUID uuid)
    {
        this.sg = game;
        this.uuid = uuid;
    }

    Location getSpawnLocation()
    {
        if (spawnLocation == null) {
            spawnLocation = sg.dealSpawnLocation();
        }
        return spawnLocation;
    }

    Location getSafeLocation()
    {
        if (safeLocation == null) {
            return getSpawnLocation();
        } else {
            return safeLocation;
        }
    }

    boolean isPlayer() { return type == Type.PLAYER; }
    boolean isSpectator() { return type == Type.SPECTATOR; }

    void setPlayer() {
        type = Type.PLAYER;
        didPlay = true;
    }
    void setSpectator() { type = Type.SPECTATOR; }

    void addKills(int kills)
    {
        this.kills += kills;
    }

    void recordHighscore()
    {
        switch (sg.state) {
        case INIT: case WAIT_FOR_PLAYERS: return;
        }
        if (!didPlay) return;
        if (highscoreRecorded) return;
        sg.highscore.store(sg.gameUuid, uuid, name, startTime, endTime, kills, winner);
        sg.getLogger().info("Stored highscore of " + name);
        highscoreRecorded = true;
    }
}
