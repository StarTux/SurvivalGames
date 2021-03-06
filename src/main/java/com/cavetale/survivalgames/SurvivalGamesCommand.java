package com.cavetale.survivalgames;

import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

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
        rootNode.addChild("event").denyTabCompletion()
            .description("Toggle event mode")
            .senderCaller(this::event);
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
        boolean val = !plugin.isEventMode();
        plugin.setEventMode(val);
        sender.sendMessage("Event mode turned " + val);
        return true;
    }
}
