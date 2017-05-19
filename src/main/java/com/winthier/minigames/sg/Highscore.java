package com.winthier.minigames.sg;

import com.winthier.minigames.MinigamesPlugin;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Value;

public class Highscore {
    @Value class Entry{ String name; int count; int kills; int wins; }

    public void init() {
        System.out.println("Setting up Survival Games highscore");
        final String sql =
            "CREATE TABLE IF NOT EXISTS `SurvivalGames` (" +
            " `id` INT(11) NOT NULL AUTO_INCREMENT," +
            " `game_uuid` VARCHAR(40) NOT NULL," +
            " `player_uuid` VARCHAR(40) NOT NULL," +
            " `player_name` VARCHAR(16) NOT NULL," +
            " `start_time` DATETIME NOT NULL," +
            " `end_time` DATETIME NOT NULL," +
            " `kills` INT(11) NOT NULL," +
            " `winner` BOOLEAN NOT NULL," +
            " PRIMARY KEY (`id`)" +
            ")";
        try {
            MinigamesPlugin.getInstance().getDb().executeUpdate(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Done setting up Survival Games highscore");
    }

    public void store(UUID gameUuid, UUID playerUuid, String playerName, Date startTime, Date endTime, int kills, boolean winner) {
        final String sql =
            "INSERT INTO `SurvivalGames` (" +
            " `game_uuid`, `player_uuid`, `player_name`, `start_time`, `end_time`, `kills`, `winner`" +
            ") VALUES (" +
            " ?, ?, ?, ?, ?, ?, ?" +
            ")";
        try (PreparedStatement update = MinigamesPlugin.getInstance().getDb().getConnection().prepareStatement(sql)) {
            update.setString(1, gameUuid.toString());
            update.setString(2, playerUuid.toString());
            update.setString(3, playerName);
            update.setTimestamp(4, new java.sql.Timestamp(startTime.getTime()));
            update.setTimestamp(5, new java.sql.Timestamp(endTime.getTime()));
            update.setInt(6, kills);
            update.setBoolean(7, winner);
            update.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    List<Entry> list() {
        final String sql =
            "SELECT player_name, COUNT(*) AS count, SUM(kills) AS kills, SUM(winner) AS wins FROM SurvivalGames GROUP BY player_uuid ORDER BY wins DESC, kills DESC, count DESC LIMIT 10";
        List<Entry> result = new ArrayList<>();
        try (ResultSet row = MinigamesPlugin.getInstance().getDb().executeQuery(sql)) {
            while (row.next()) {
                String name = row.getString("player_name");
                int count = row.getInt("count");
                int kills = row.getInt("kills");
                int wins = row.getInt("wins");
                result.add(new Entry(name, count, kills, wins));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
