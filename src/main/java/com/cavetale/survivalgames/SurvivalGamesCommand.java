package com.cavetale.survivalgames;

import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class SurvivalGamesCommand implements TabExecutor {
    private final SurvivalGamesPlugin plugin;
    private CommandNode rootNode;

    protected SurvivalGamesCommand enable() {
        rootNode = new CommandNode("sg");
        rootNode.addChild("reload").denyTabCompletion()
            .description("Reload config files")
            .senderCaller(this::reload);
        rootNode.addChild("item").arguments("<item>")
            .description("Spawn in an item")
            .playerCaller(this::item);
        rootNode.addChild("start").arguments("<world>")
            .completableList(ctx -> plugin.getWorldNames())
            .description("Start game in a world")
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop the game")
            .senderCaller(this::stop);
        rootNode.addChild("debug").denyTabCompletion()
            .description("Toggle debug mode")
            .senderCaller(this::debug);
        rootNode.addChild("event").arguments("true|false")
            .completers(CommandArgCompleter.list("true", "false"))
            .description("Set event mode")
            .senderCaller(this::event);
        CommandNode score = rootNode.addChild("score")
            .description("Score subcommands");
        score.addChild("clear").denyTabCompletion()
            .description("Clear scores")
            .senderCaller(this::scoreClear);
        score.addChild("add").arguments("<player> <amount>")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> true))
            .description("Add score highscore")
            .senderCaller(this::scoreAdd);
        score.addChild("update").denyTabCompletion()
            .description("Update score highscore")
            .senderCaller(this::scoreUpdate);
        score.addChild("reward").denyTabCompletion()
            .description("Give out trophy rewards")
            .senderCaller(this::scoreReward);
        return this;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        return rootNode.call(sender, command, alias, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return rootNode.complete(sender, command, alias, args);
    }

    boolean reload(CommandSender sender, String[] args) {
        sender.sendMessage("Reloading config files...");
        plugin.loadConfigFiles();
        return true;
    }

    boolean item(Player player, String[] args) {
        if (args.length != 1) return false;
        String key = args[0];
        player.getInventory().addItem(plugin.itemForKey(key));
        player.sendMessage(ChatColor.YELLOW + "Given item " + key);
        return true;
    }

    boolean start(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String worldName = args[0];
        if (!plugin.getWorldNames().contains(worldName)) {
            throw new CommandWarn(worldName + " not in world list: " + plugin.getWorldNames());
        }
        sender.sendMessage("Starting game in " + worldName);
        plugin.startGame(worldName);
        return true;
    }

    boolean stop(CommandSender sender, String[] args) {
        sender.sendMessage("Stopping the game...");
        plugin.stopGame();
        return true;
    }

    boolean debug(CommandSender sender, String[] args) {
        boolean val = !plugin.isDebug();
        plugin.setDebug(val);
        sender.sendMessage("Debug mode turned " + val);
        return true;
    }

    boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            try {
                plugin.saveTag.event = Boolean.parseBoolean(args[0]);
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Boolean expected: " + args[0]);
            }
            plugin.save();
        }
        sender.sendMessage("Event mode: " + plugin.saveTag.event);
        return true;
    }

    private void scoreClear(CommandSender sender) {
        plugin.saveTag.kills.clear();
        plugin.save();
        plugin.computeHighscore();
        sender.sendMessage(text("Scores cleared", AQUA));
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        int amount = CommandArgCompleter.requireInt(args[1], i -> true);
        plugin.saveTag.addKills(target.uuid, amount);
        plugin.save();
        plugin.computeHighscore();
        sender.sendMessage(text("Score changed: " + target.name + ", " + amount, AQUA));
        return true;
    }

    private void scoreUpdate(CommandSender sender) {
        plugin.computeHighscore();
        sender.sendMessage(text("Score highscore updated", AQUA));
    }

    private void scoreReward(CommandSender sender) {
        plugin.computeHighscore();
        int count = plugin.rewardHighscore();
        sender.sendMessage(text(count + " scores rewarded", AQUA));
    }
}
