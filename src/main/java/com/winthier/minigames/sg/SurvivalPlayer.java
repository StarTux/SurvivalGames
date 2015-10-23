package com.winthier.minigames.sg;

import com.winthier.reward.RewardBuilder;
import java.util.Date;
import java.util.UUID;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
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
    boolean rewarded = false;

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
        if (sg.debug) return;
        switch (sg.state) {
        case INIT: case WAIT_FOR_PLAYERS: return;
        }
        if (!didPlay) return;
        if (highscoreRecorded) return;
        sg.highscore.store(sg.gameUuid, uuid, name, startTime, endTime, kills, winner);
        sg.getLogger().info("Stored highscore of " + name);
        highscoreRecorded = true;
        if (!rewarded) {
            rewarded = true;
            RewardBuilder reward = RewardBuilder.create().uuid(uuid).name(name);
            reward.comment(String.format("Game of Survival Games %s with %d kills.", (winner ? "won" : "played"), kills));
            ConfigurationSection config = sg.getConfigFile("rewards");
            for (int i = 0; i < kills; ++i) reward.config(config.getConfigurationSection("kill"));
            for (int i = 0; i <= kills; ++i) reward.config(config.getConfigurationSection("" + i + "kills"));
            if (winner) {
                reward.config(config.getConfigurationSection("win"));
            }
            reward.store();
        }
    }
}
