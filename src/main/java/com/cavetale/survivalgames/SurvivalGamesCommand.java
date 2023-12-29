package com.cavetale.survivalgames;

import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.playercache.PlayerCache;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
            .completableList(ctx -> listWorldPaths())
            .description("Start game in a world")
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop the game")
            .senderCaller(this::stop);
        rootNode.addChild("skip").denyTabCompletion()
            .description("Skip timer")
            .senderCaller(this::skip);
        rootNode.addChild("debug").denyTabCompletion()
            .description("Toggle debug mode")
            .senderCaller(this::debug);
        rootNode.addChild("event").arguments("true|false")
            .completers(CommandArgCompleter.list("true", "false"))
            .description("Set event mode")
            .senderCaller(this::event);
        rootNode.addChild("pause").arguments("true|false")
            .completers(CommandArgCompleter.list("true", "false"))
            .description("Set pause mode")
            .senderCaller(this::pause);
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

    private List<String> listWorldPaths() {
        List<String> result = new ArrayList<>();
        for (BuildWorld buildWorld : BuildWorld.findMinigameWorlds(plugin.MINIGAME_TYPE, false)) {
            result.add(buildWorld.getPath());
        }
        return result;
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
        player.sendMessage(text("Given item " + key, YELLOW));
        return true;
    }

    private boolean start(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String worldName = args[0];
        final BuildWorld buildWorld = BuildWorld.findWithPath(worldName);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != plugin.MINIGAME_TYPE) {
            throw new CommandWarn("Not a Survival Games world: " + buildWorld.getName());
        }
        sender.sendMessage(text("Starting game in " + buildWorld.getName(), YELLOW));
        plugin.startGame(buildWorld);
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

    private boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            plugin.saveTag.event = CommandArgCompleter.requireBoolean(args[0]);
            plugin.save();
        }
        sender.sendMessage(text("Event mode: " + plugin.saveTag.event, YELLOW));
        return true;
    }

    private boolean pause(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            plugin.saveTag.pause = CommandArgCompleter.requireBoolean(args[0]);
            plugin.save();
        }
        sender.sendMessage(text("Pause mode: " + plugin.saveTag.pause, YELLOW));
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

    private void skip(CommandSender sender) {
        plugin.stateTicks = plugin.state.seconds * 20;
        sender.sendMessage(text("Skipped state " + plugin.state, YELLOW));
    }
}
