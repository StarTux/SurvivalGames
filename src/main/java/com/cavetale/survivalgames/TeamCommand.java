package com.cavetale.survivalgames;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class TeamCommand implements CommandExecutor {
    protected final SurvivalGamesPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[survivalgames:teammsg] player expected");
            return true;
        }
        if (args.length == 0) return false;
        Player player = (Player) sender;
        SurvivalTeam team = plugin.getSurvivalPlayer(player).team;
        if (team == null) {
            player.sendMessage(Component.text("No team!", NamedTextColor.RED));
            return true;
        }
        Component message = Component.join(JoinConfiguration.noSeparators(), new ComponentLike[] {
                Component.text("["),
                team.component,
                Component.text("]"),
                Component.text().color(NamedTextColor.GRAY)
                .append(player.displayName())
                .append(Component.text(": ")),
                Component.text(String.join(" ", args)),
            }).color(NamedTextColor.WHITE);
        for (SurvivalPlayer sp : plugin.getPlayers(team)) {
            Player target = sp.getPlayer();
            if (target == null) continue;
            target.sendMessage(message);
        }
        return true;
    }
}
