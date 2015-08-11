package com.winthier.minigames.sg;

import com.winthier.minigames.MinigamesPlugin;
import com.winthier.minigames.event.player.PlayerLeaveEvent;
import com.winthier.minigames.game.Game;
import com.winthier.minigames.player.PlayerInfo;
import com.winthier.minigames.util.BukkitFuture;
import com.winthier.minigames.util.Msg;
import com.winthier.minigames.util.Players;
import com.winthier.minigames.util.Title;
import com.winthier.minigames.util.WorldLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.Value;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.SkullType;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

public class SurvivalGames extends Game implements Listener {
    @Value class ChunkCoord { int x, z; }
    static enum State {
        INIT,
        WAIT_FOR_PLAYERS(60),
        COUNTDOWN(5),
        LOOTING(45),
        FREE_FOR_ALL(60*10),
        COUNTDOWN_SUDDEN_DEATH(10),
        SUDDEN_DEATH,
        END(60),
        ;
        final long seconds;
        State() { this.seconds = 0L; }
        State(long seconds) { this.seconds = seconds; }
    }
    // const
    final static long RESTOCK_SECONDS = 60;
    final static long RESTOCK_VARIANCE = 30;
    final static int MIN_PLAYERS = 2; // TODO set higher?
    final static long SUDDEN_DEATH_WITHER_SECONDS = 60;
    final static double SUDDEN_DEATH_RADIUS = 20;
    // minigame stuf
    World world;
    BukkitRunnable tickTask;
    // chunk processing
    Set<ChunkCoord> processedChunks = new HashSet<>();
    boolean didSomeoneJoin = false;
    // score keeping
    Scoreboard scoreboard;
    Objective sidebarObjective;
    // map config from crawler
    final List<Location> spawnLocations = new ArrayList<>();
    boolean spawnLocationsRandomized = false;
    boolean compassesGiven = false;
    int spawnLocationIter = 0;
    final Set<Block> restockChests = new HashSet<>();
    final Map<Block, UUID> landMines = new HashMap<>();
    final List<String> credits = new ArrayList<>();
    // state
    final Random random = new Random(System.currentTimeMillis());
    long ticks;
    long stateTicks;
    long emptyTicks;
    long restockTicks = 0;
    int restockPhase = 0;
    String winnerName = null;
    State state = State.INIT;
    // file config
    final Map<String, ItemStack> stockItems = new HashMap<>();
    final List<List<List<String>>> phaseItems = new ArrayList<>();
    int phase = 0;
    // Highscore
    final UUID gameUuid = UUID.randomUUID();
    final Highscore highscore = new Highscore();
    
    // Setup event handlers
    @Override
    public void onEnable() {
        loadConfigFiles();
        WorldLoader.loadWorlds(this, new BukkitFuture<WorldLoader>() {
            @Override public void run() {
                onWorldsLoaded(get());
            }
        }, "/home/creative/minecraft/worlds/HungerGames/");
    }

    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        processedChunks.clear();
    }

    private void onWorldsLoaded(WorldLoader worldLoader) {
        this.world = worldLoader.getWorld(0);
        world.setDifficulty(Difficulty.HARD);
        world.setPVP(false);
        world.setTime(1000L);
        world.setGameRuleValue("doDaylightCycle", "true");
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("sendCommandFeedback", "false");
        this.tickTask = new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        };
        tickTask.runTaskTimer(MinigamesPlugin.getInstance(), 1L, 1L);
        MinigamesPlugin.getInstance().getEventManager().registerEvents(this, this);
        setupScoreboard();
        processChunkArea(world.getSpawnLocation().getChunk());
        highscore.init();
        ready();
    }

    void loadConfigFiles()
    {
        // Load config files
        ConfigurationSection config;
        config = getConfigFile("items");
        for (String key : config.getKeys(false)) {
            ItemStack item = config.getItemStack(key);
            if (item == null) {
                getLogger().warning("Bad item key: " + key);
            } else {
                stockItems.put(key, item);
            }
        }
        config = getConfigFile("phases");
        for (Object o : config.getList("phases")) {
            if (o instanceof List) {
                // Phase
                List<List<String>> phaseList = new ArrayList<>();
                for (Object p : (List<Object>)o) {
                    if (p instanceof String) {
                        phaseList.add(Arrays.asList((String)p));
                    } else if (p instanceof List) {
                        List<String> itemList = new ArrayList<>();
                        for (Object q : (List<Object>)p) {
                            if (q instanceof String) {
                                itemList.add((String)q);
                            }
                        }
                        phaseList.add(itemList);
                    }
                }
                phaseItems.add(phaseList);
            }
        }
        // Check
        for (List<List<String>> list1 : phaseItems) {
            for (List<String> list : list1) {
                for (String key : list) {
                    if (!stockItems.containsKey(key)) {
                        getLogger().warning("Item key not found: " + key);
                    }
                }
            }
        }
        //
    }

    private void onTick() {
        final long ticks = this.ticks++;
        if (getPlayerUuids().isEmpty()) {
            // All players left
            cancel();
            return;
        }
        if (state != State.INIT && state != State.WAIT_FOR_PLAYERS) {
            // Everyone logged off
            if (getOnlinePlayers().isEmpty()) {
                final long emptyTicks = this.emptyTicks++;
                if (emptyTicks >= 20*60) {
                    cancel();
                    return;
                }
            } else {
                emptyTicks = 0L;
            }
        }
        if (ticks % 20 == 0 && state != State.INIT && state != State.WAIT_FOR_PLAYERS && state != State.COUNTDOWN) {
            // Update compass targets
            for (Player player : getOnlinePlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    Location playerLoc = player.getLocation();
                    Location loc = world.getSpawnLocation();
                    double dist = Double.MAX_VALUE;
                    for (Player target : getOnlinePlayers()) {
                        if (!player.equals(target) && getSurvivalPlayer(target).isPlayer()) {
                            Location targetLoc = target.getLocation();
                            double newDist = playerLoc.distanceSquared(targetLoc);
                            if (newDist < dist) {
                                loc = targetLoc;
                                dist = newDist;
                            }
                        }
                    }
                    player.setCompassTarget(loc);
                }
            }
        }
        State newState = null;
        if (state != State.INIT && state != State.WAIT_FOR_PLAYERS && state != State.END) {
            // Check if only one player is left
            int aliveCount = 0;
            UUID survivor = null;
            for (PlayerInfo info : getPlayers()) {
                if (getSurvivalPlayer(info.getUuid()).isPlayer()) {
                    survivor = info.getUuid();
                    aliveCount++;
                }
            }
            if (aliveCount == 2 && !compassesGiven) {
                compassesGiven = true;
                for (Player player : getOnlinePlayers()) {
                    if (getSurvivalPlayer(player).isPlayer()) {
                        player.getWorld().dropItemNaturally(player.getEyeLocation(), stockItemForKey("SpecialCompass")).setPickupDelay(0);
                    }
                }
            }
            if (aliveCount == 1 && survivor != null && MIN_PLAYERS > 1) {
                winnerName = getSurvivalPlayer(survivor).getName();
                getSurvivalPlayer(survivor).setWinner(true);
                getSurvivalPlayer(survivor).setEndTime(new Date());
                getSurvivalPlayer(survivor).recordHighscore();
                newState = State.END;
            } else if (aliveCount == 0) {
                winnerName = null;
                newState = State.END;
            }
        }
        // Check for disconnects
        for (PlayerInfo info : getPlayers()) {
            SurvivalPlayer sp = getSurvivalPlayer(info.getUuid());
            if (!info.isOnline()) {
                // Kick players who disconnect too long
                long discTicks = sp.getDiscTicks();
                if (state == State.SUDDEN_DEATH) {
                    MinigamesPlugin.getInstance().leavePlayer(info.getUuid());
                } else if (discTicks > 20*60) {
                    getLogger().info("Kicking " + sp.getName() + " because they were disconnected too long");
                    MinigamesPlugin.getInstance().leavePlayer(info.getUuid());
                }
                sp.setDiscTicks(discTicks + 1);
            }
        }
        if (newState == null) newState = tickState(state);
        if (newState != null && state != newState) {
            getLogger().info("Entering state: " + newState);
            onStateChange(state, newState);
            state = newState;
        }
        processPlayerChunks();
    }

    void onStateChange(State oldState, State newState)
    {
        stateTicks = 0;
        switch (newState) {
        case COUNTDOWN:
            // Once the countdown starts, remove everyone who disconnected
            setupScoreboard();
            for (PlayerInfo info : getPlayers()) {
                if (!info.isOnline()) {
                    MinigamesPlugin.leavePlayer(info.getUuid());
                }
                if (getSurvivalPlayer(info.getUuid()).isPlayer()) {
                    sidebarObjective.getScore(getSurvivalPlayer(info.getUuid()).getName()).setScore(0);
                }
            }
            break;
        case LOOTING:
            // Remove everyone who disconnected, reset everyone else so they can start playing
            for (PlayerInfo info : getPlayers()) {
                if (info.isOnline()) {
                    Player player = info.getPlayer();
                    makeMobile(player);
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
            break;
        case FREE_FOR_ALL:
            for (Player player : getOnlinePlayers()) {
                Title.show(player, "", "&cFight!");
                Msg.send(player, "&cFight!");
                player.playSound(player.getEyeLocation(), Sound.WITHER_SPAWN, 1f, 1f);
            }
            world.setPVP(true);
            break;
        case COUNTDOWN_SUDDEN_DEATH:
            for (Player player : getOnlinePlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    makeImmobile(player, getSurvivalPlayer(player).getSpawnLocation());
                    player.playSound(player.getEyeLocation(), Sound.ENDERDRAGON_GROWL, 1f, 1f);
                }
            }
            world.setPVP(false);
            break;
        case SUDDEN_DEATH:
            for (Player player : getOnlinePlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    makeMobile(player);
                }
            }
            world.setPVP(true);
            break;
        case END:
            for (Player player : getOnlinePlayers()) {
                player.playSound(player.getEyeLocation(), Sound.ENDERDRAGON_DEATH, 1f, 1f);
            }
        }
    }

    State tickState(State state)
    {
        long ticks = this.stateTicks++;
        switch (state) {
        case INIT: return tickInit(ticks);
        case WAIT_FOR_PLAYERS: return tickWaitForPlayers(ticks);
        case COUNTDOWN: return tickCountdown(ticks);
        case LOOTING: return tickLooting(ticks);
        case FREE_FOR_ALL: return tickFreeForAll(ticks);
        case COUNTDOWN_SUDDEN_DEATH: return tickCountdownSuddenDeath(ticks);
        case SUDDEN_DEATH: return tickSuddenDeath(ticks);
        case END: return tickEnd(ticks);
        }
        getLogger().warning("State has no handler: " + state);
        return null;
    }

    State tickInit(long ticks)
    {
        if (!didSomeoneJoin) return null;
        return State.WAIT_FOR_PLAYERS;
    }

    State tickWaitForPlayers(long ticks)
    {
        if (ticks % (20*5) == 0) {
            for (Player player : getOnlinePlayers()) {
                if (!getSurvivalPlayer(player).isReady()) {
                    List<Object> list = new ArrayList<>();
                    list.add(Msg.format("&fClick here when ready: "));
                    list.add(button("&3[Ready]", "&3Mark yourself as ready", "/ready"));
                    list.add(Msg.format("&f or "));
                    list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
                    Msg.sendRaw(player, list);
                }
            }
        }
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20 == 0) {
            setSidebarTitle("Waiting", timeLeft);
            // If the player count is high enough and everybody's ready, go ahead and start.
            boolean allReady = true;
            int playerCount = 0;
            for (PlayerInfo info : getPlayers()) {
                playerCount++;
                if (!info.<SurvivalPlayer>getCustomData(SurvivalPlayer.class).isReady()) {
                    allReady = false;
                    break;
                }
            }
            if (allReady && playerCount >= MIN_PLAYERS) return State.COUNTDOWN;
        }
        if (timeLeft <= 0) {
            // Time runs out
            boolean allReady = true;
            int playerCount = 0;
            for (PlayerInfo info : getPlayers()) {
                playerCount++;
            }
            if (playerCount >= MIN_PLAYERS) return State.COUNTDOWN;
            // Cancel if there are not at least 2 people
            cancel();
        }
        return null;
    }

    State tickCountdown(long ticks)
    {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            long seconds = timeLeft / 20;
            for (Player player : getOnlinePlayers()) {
                setSidebarTitle("Get ready", timeLeft);
                if (seconds == 0) {
                    Title.show(player, "&a&lGO!", "");
                    Msg.send(player, "&bGO!");
                    player.playSound(player.getEyeLocation(), Sound.FIREWORK_LARGE_BLAST, 1f, 1f);
                } else if (seconds == state.seconds) {
                    Title.show(player, "&aGet Ready!", "&aGame starts in " + state.seconds + " seconds");
                    Msg.send(player, "&bGame starts in %d seconds", seconds);
                } else {
                    Title.show(player, "&a&l" + seconds, "&aGame Start");
                    Msg.send(player, "&b%d", seconds);
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)ticks/20));
                }
            }
        }
        if (timeLeft <= 0) return State.LOOTING;
        return null;
    }

    State tickLooting(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            setSidebarTitle("Peaceful", timeLeft);
        }
        if (timeLeft <= 0) return State.FREE_FOR_ALL;
        return null;
    }

    State tickFreeForAll(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            setSidebarTitle("Fight", timeLeft);
        }
        if (restockTicks <= 0) {
            restockTicks = (RESTOCK_SECONDS + (random.nextLong() % RESTOCK_VARIANCE) - (random.nextLong() % RESTOCK_VARIANCE)) * 20;
        } else {
            if (--restockTicks <= 0) {
                int phase = this.restockPhase++;
                restockAllChests(phase);
            }
        }
        if (timeLeft <= 0) return State.COUNTDOWN_SUDDEN_DEATH;
        return null;
    }

    State tickCountdownSuddenDeath(long ticks)
    {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            setSidebarTitle("Get ready", timeLeft);
            long seconds = timeLeft / 20;
            for (Player player : getOnlinePlayers()) {
                if (seconds == 0) {
                    Title.show(player, "", "&c&lKILL!");
                    Msg.send(player, "&bGO!");
                } else if (seconds == state.seconds) {
                    Title.show(player, "&cGet Ready!", "&cSudden death in " + state.seconds + " seconds");
                    Msg.send(player, "&bGame starts in %d seconds", seconds);
                } else {
                    Title.show(player, "&c&l" + seconds, "&cSudden Death");
                    Msg.send(player, "&b%d", seconds);
                }
            }
        }
        if (timeLeft <= 0) return State.SUDDEN_DEATH;
        return null;
    }

    State tickSuddenDeath(long ticks)
    {
        if (ticks % 20L == 0) {
            setSidebarTitle("Sudden Death", ticks);
        }
        if (ticks > 0 && ticks % SUDDEN_DEATH_WITHER_SECONDS == 0) {
            for (Player player : getOnlinePlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, (int)SUDDEN_DEATH_WITHER_SECONDS*20, 0, true));
                }
            }
        }
        return null;
    }

    State tickEnd(long ticks)
    {
        if (ticks == 0) {
            for (Player player : getOnlinePlayers()) {
                player.setGameMode(GameMode.SPECTATOR);
                if (winnerName != null) {
                    Msg.send(player, "&b%s wins the game!", winnerName);
                } else {
                    Msg.send(player, "&bDraw! Nobody wins.");
                }
                List<Object> list = new ArrayList<>();
                list.add("Click here to leave the game: ");
                list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
                Msg.sendRaw(player, list);
            }
        }
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            setSidebarTitle("Game Over", timeLeft);
        }
        if (timeLeft % (20*5) == 0) {
            for (Player player : getOnlinePlayers()) {
                if (winnerName != null) {
                    Title.show(player, "&a" + winnerName, "&aWins the Game!");
                } else {
                    Title.show(player, "&cDraw!", "&cNobody wins");
                }
                Location loc = player.getLocation();
                Firework firework = (Firework)loc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
                FireworkMeta meta = (FireworkMeta)stockItemForKey("SpecialFireworkVictory").getItemMeta();
                firework.setFireworkMeta(meta);
            }
        }
        if (timeLeft <= 0) cancel();
        return null;
    }

    ItemStack stockItemForKey(String key)
    {
        ItemStack result = stockItems.get(key);
        if (result == null) result = new ItemStack(Material.STONE);
        return result.clone();
    }

    List<ItemStack> stockItemForPhase(int phase)
    {
        phase = Math.min(phase, phaseItems.size() - 1);
        List<List<String>> cacheList = new ArrayList<>();
        for (int i = 0; i <= phase; ++i) {
            cacheList.addAll(phaseItems.get(i));
        }
        List<ItemStack> result = new ArrayList<>();
        int i = random.nextInt(cacheList.size());
        List<String> list = cacheList.get(i);
        for (String key : list) {
            result.add(stockItemForKey(key));
        }
        return result;
    }

    @Override
    public void onPlayerReady(Player player) {
        didSomeoneJoin = true;
        Players.reset(player);
        player.setScoreboard(scoreboard);
        final SurvivalPlayer survivalPlayer = getSurvivalPlayer(player);
        survivalPlayer.setName(player.getName());
        survivalPlayer.setStartTime(new Date());
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
            survivalPlayer.setPlayer();
            sidebarObjective.getScore(player.getName()).setScore(0);
            break;
        default:
            if (survivalPlayer.isPlayer()) {
                sidebarObjective.getScore(player.getName()).setScore(0);
            }
            survivalPlayer.setSpectator();
        }
        new BukkitRunnable() {
            @Override public void run() {
                showHighscore(player);
            }
        }.runTaskLater(MinigamesPlugin.getInstance(), 20*3);
    }

    SurvivalPlayer getSurvivalPlayer(UUID uuid)
    {
        return getPlayer(uuid).<SurvivalPlayer>getCustomData(SurvivalPlayer.class);
    }

    SurvivalPlayer getSurvivalPlayer(Player player)
    {
        return getSurvivalPlayer(player.getUniqueId());
    }

    @Override
    public Location getSpawnLocation(Player player)
    {
        switch (state)
        {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
        case COUNTDOWN_SUDDEN_DEATH:
            return getSurvivalPlayer(player).getSpawnLocation();
        default:
            if (getSurvivalPlayer(player).isSpectator()) {
                return world.getSpawnLocation();
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final SurvivalPlayer survivalPlayer = getSurvivalPlayer(player);
        player.setScoreboard(scoreboard);
        if (survivalPlayer.isSpectator()) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);
            return;
        }
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
        case COUNTDOWN_SUDDEN_DEATH:
            // Someone joins in the early stages, we make sure they are locked in the right place
            makeImmobile(player, survivalPlayer.getSpawnLocation());
            break;
        default:
            // Join later and we make sure you are in the right state
            makeMobile(player);
            player.setGameMode(GameMode.SURVIVAL);
        }
    }
    
    @Override
    public boolean onCommand(Player player, String command, String[] args) {
        if (command.equalsIgnoreCase("ready") && state == State.WAIT_FOR_PLAYERS) {
            getSurvivalPlayer(player).setReady(true);
            sidebarObjective.getScore(player.getName()).setScore(1);
            Msg.send(player, "&aMarked as ready");
        } else if (command.equalsIgnoreCase("item") && args.length == 1 && player.isOp()) {
            String key = args[0];
            player.getInventory().addItem(stockItemForKey(key));
            Msg.send(player, "&eGiven item %s", key);
        } else if (command.equalsIgnoreCase("tp") && getSurvivalPlayer(player).isSpectator()) {
            if (args.length != 1) {
                Msg.send(player, "&cUsage: /tp <player>");
                return true;
            }
            String arg = args[0];
            for (Player target : getOnlinePlayers()) {
                if (arg.equalsIgnoreCase(target.getName())) {
                    player.teleport(target);
                    Msg.send(player, "&bTeleported to %s", target.getName());
                    return true;
                }
            }
            Msg.send(player, "&cPlayer not found: %s", arg);
            return true;
        } else if (command.equalsIgnoreCase("highscore") || command.equalsIgnoreCase("hi")) {
            showHighscore(player);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean joinPlayers(List<UUID> uuids)
    {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
            return super.joinPlayers(uuids);
        default: return false;
        }
    }

    @Override
    public boolean joinSpectators(List<UUID> uuids)
    {
        getLogger().info("joinSpectators: " + uuids);
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
            getLogger().info("INIT/WAIT_FOR_PLAYERS: false");
            return false;
        default:
            getLogger().info("default");
            if (super.joinSpectators(uuids)) {
                getLogger().info("super.joinSpectators(): true");
                for (UUID uuid : uuids) {
                    getSurvivalPlayer(uuid).setSpectator();
                }
                return true;
            }
            getLogger().info("super.joinSpectators(): false");
        }
        return false;
    }

    private void processPlayerChunks() {
        for (Player player : getOnlinePlayers()) {
            Chunk chunk = player.getLocation().getChunk();
            processChunkArea(chunk.getX(), chunk.getZ());
        }
    }

    private void processChunkArea(Chunk chunk) {
        processChunkArea(chunk.getX(), chunk.getZ());
    }
    
    private void processChunkArea(int cx, int cz) {
        final int RADIUS = 3;
        for (int dx = -RADIUS; dx <= RADIUS; ++dx) {
            for (int dz = -RADIUS; dz <= RADIUS; ++dz) {
                final int x = cx + dx;
                final int z = cz + dz;
                processChunk(x, z);
            }
        }
    }

    private void processChunk(int x, int z) {
        final ChunkCoord cc = new ChunkCoord(x, z);
        if (processedChunks.contains(cc)) return;
        processedChunks.add(cc);
        // Process
        final Chunk chunk = world.getChunkAt(cc.getX(), cc.getZ());
        chunk.load();
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Chest) {
                Block block = state.getBlock();
                if (restockChests.contains(block)) {
                    getLogger().warning(String.format("Duplicate chest scanned at %d,%d,%d", block.getX(), block.getY(), block.getZ()));
                } else {
                    try {
                        ((Chest)state).getInventory().clear();
                    } catch (NullPointerException npe) {
                        npe.printStackTrace();
                    }
                    restockChests.add(state.getBlock());
                    for (int i = 0; i <= phase; ++i) {
                        stockBlock(state.getBlock(), i);
                    }
                }
            } else if (state instanceof Sign) {
                final Sign sign = (Sign)state;
                String firstLine = sign.getLine(0).toLowerCase();
                if (firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]")) {
                    if (firstLine.equals("[credits]")) {
                        for (int i = 1; i < 4; ++i) {
                            String credit = sign.getLine(i);
                            if (credit != null) credits.add(credit);
                        }
                        state.getBlock().setType(Material.AIR);
                    } else if (firstLine.equals("[spawn]")) {
                        Location location = state.getBlock().getLocation();
                        location.add(0.5, 0.5, 0.5);
                        Vector lookAt = world.getSpawnLocation().toVector().subtract(location.toVector());
                        location.setDirection(lookAt);
                        spawnLocations.add(location);
                        state.getBlock().setType(Material.AIR);
                    }
                }
            }
        }
    }

    private boolean isNearAnyPlayer(Block block) {
        final int RADIUS = 16;
        final int VRADIUS = 8;
        for (Player player : getOnlinePlayers()) {
            final int px, py, pz;
            {
                final Location tmp = player.getLocation();
                px = tmp.getBlockX();
                py = tmp.getBlockY();
                pz = tmp.getBlockZ();
            }
            final int dx = Math.abs(px - block.getX());
            if (dx > RADIUS) continue;
            final int dy = Math.abs(py - block.getY());
            if (dy > VRADIUS) continue;
            final int dz = Math.abs(pz - block.getZ());
            if (dz > RADIUS) continue;
            return true;
        }
        return false;
    }

    private void setupScoreboard() {
        scoreboard = MinigamesPlugin.getInstance().getServer().getScoreboardManager().getNewScoreboard();
        sidebarObjective = scoreboard.registerNewObjective("Sidebar", "dummy");
        sidebarObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebarObjective.setDisplayName(Msg.format("&aSurvival Games"));
        Objective healthObjective = scoreboard.registerNewObjective("Health", "health");
        healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        healthObjective.setDisplayName(Msg.format("&cHearts"));
        for (PlayerInfo info : getPlayers()) {
            if (info.isOnline()) {
                info.getPlayer().setScoreboard(scoreboard);
            }
            SurvivalPlayer sp = getSurvivalPlayer(info.getUuid());
        }
    }


    void makeImmobile(Player player, Location location)
    {
        if (!player.getLocation().getWorld().equals(location.getWorld()) ||
            player.getLocation().distanceSquared(location) > 4.0) {
            player.teleport(location);
            getLogger().info("Teleported " + player.getName() + " to their spawn location");
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0);
        player.setWalkSpeed(0);
    }

    void makeMobile(Player player)
    {
        player.setWalkSpeed(.2f);
        player.setFlySpeed(.1f);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    Location dealSpawnLocation() {
        if (spawnLocations.isEmpty()) return world.getSpawnLocation();
        if (!spawnLocationsRandomized) {
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations, random);
        }
        if (spawnLocationIter >= spawnLocations.size()) spawnLocationIter = 0;
        int i = spawnLocationIter++;
        return spawnLocations.get(i);
    }

    void setSidebarTitle(String title, long ticks)
    {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        sidebarObjective.setDisplayName(Msg.format("&3%s &f%02d&3:&f%02d", title, minutes, seconds % 60));
    }

    void restockAllChests(int phase)
    {
        int count = 0;
        int skipped = 0;
    blockLoop:
        for (Block block : restockChests) {
            // See if player is nearby
            final double CHEST_RADIUS = 32;
            for (Player player : getOnlinePlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    if (player.getLocation().distanceSquared(block.getLocation()) < CHEST_RADIUS * CHEST_RADIUS) {
                        skipped++;
                        continue blockLoop;
                    }
                }
            }
            if (stockBlock(block, phase)) count++;
        }
        getLogger().info("Phase " + phase + ": Restocked " + count + " chests, skipped " + skipped);
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : getOnlinePlayers()) {
                    Title.show(player, "", "&3Chests restocked");
                    player.playSound(player.getEyeLocation(), Sound.LEVEL_UP, 1f, 1f);
                }
            }
        }.runTaskLater(MinigamesPlugin.getInstance(), 20*3);
    }

    boolean stockBlock(Block block, int phase) {
        BlockState state = block.getState();
        if (!(state instanceof Chest)) return false;
        stockChest(((Chest)state).getBlockInventory(), phase);
        return false;
    }

    void stockChest(Inventory inv, int phase)
    {
        try {
            for (ItemStack item : stockItemForPhase(phase)) {
                int i = random.nextInt(inv.getSize());
                inv.setItem(i, item);
            }
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }

    // Event Handlers

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        switch (block.getType()) {
        case FIRE:
            return;
        case IRON_PLATE:
        case GOLD_PLATE:
        case STONE_PLATE:
        case WOOD_PLATE:
            if (stockItemForKey("SpecialLandMine").isSimilar(player.getItemInHand())) {
                landMines.put(block, player.getUniqueId());
                getLogger().info(String.format("%s placed Land Mine at %d,%d,%d", player.getName(), block.getX(), block.getY(), block.getZ()));
            }
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
            event.setCancelled(true);
            return;
        }
        switch (event.getBlock().getType()) {
        case LONG_GRASS:
        case DEAD_BUSH:
        case RED_ROSE:
        case DOUBLE_PLANT:
            // Allow
            return;
        case IRON_PLATE:
        case GOLD_PLATE:
        case STONE_PLATE:
        case WOOD_PLATE:
            if (landMines.containsKey(event.getBlock())) {
                event.setCancelled(true);
                landMines.remove(event.getBlock());
                event.getBlock().setType(Material.AIR);
                world.dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), stockItemForKey("SpecialLandMine"));
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        event.setCancelled(true);
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        switch (event.getCause()) {
        case EXPLOSION:
        case FIREBALL:
        case FLINT_AND_STEEL:
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        if (getSurvivalPlayer(player).isSpectator()) {
            player.teleport(world.getSpawnLocation());
            player.setGameMode(GameMode.SPECTATOR);
            player.setHealth(player.getMaxHealth());
            event.setDeathMessage(null);
            return;
        }
        Players.reset(player);
        player.setScoreboard(scoreboard);
        getSurvivalPlayer(player).setSpectator();
        player.setGameMode(GameMode.SPECTATOR);
        // Color
        int score = sidebarObjective.getScore(player.getName()).getScore();
        scoreboard.resetScores(player.getName());
        sidebarObjective.getScore(Msg.format("&4%s", player.getName())).setScore(score);
        // Score
        Player killer = player.getKiller();
        if (killer != null && !killer.equals(player)) {
            getSurvivalPlayer(killer).addKills(1);
            sidebarObjective.getScore(killer.getName()).setScore(getSurvivalPlayer(killer).getKills());
        } else {
            UUID lastDamager = getSurvivalPlayer(player).getLastDamager();
            if (lastDamager != null) {
                SurvivalPlayer spKiller = getSurvivalPlayer(lastDamager);
                if (spKiller.isPlayer()) {
                    getSurvivalPlayer(lastDamager).addKills(1);
                    sidebarObjective.getScore(spKiller.getName()).setScore(getSurvivalPlayer(lastDamager).getKills());
                }
            }
        }
        // Lighting
        player.getWorld().strikeLightningEffect(player.getLocation());
        // Announce
        for (Player other : getOnlinePlayers()) {
            Title.show(other, "", "&c" + event.getDeathMessage());
        }
        // Option to leave
        List<Object> list = new ArrayList<>();
        list.add("You died. Click here to leave the game: ");
        list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
        Msg.sendRaw(player, list);
        // Record end time
        getSurvivalPlayer(player).setEndTime(new Date());
        getSurvivalPlayer(player).recordHighscore();
    }

    void reduceItemInHand(Player player)
    {
        ItemStack item = player.getItemInHand();
        if (item.getAmount() <= 1) {
            player.setItemInHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
            player.setItemInHand(item);
        }
    }

    void onUse(Player player, Event event)
    {
        if (stockItemForKey("SpecialFirework").isSimilar(player.getItemInHand())) {
            for (Player other : getOnlinePlayers()) {
                if (!other.equals(player) && getSurvivalPlayer(other).isPlayer()) {
                    Location otherLoc = other.getLocation();
                    Block highest = otherLoc.getWorld().getHighestBlockAt(otherLoc);
                    Location loc = highest.getLocation();
                    loc = loc.add(0.5, 3.5, 0.5);
                    Firework firework = (Firework)otherLoc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
                    FireworkMeta meta = (FireworkMeta)stockItemForKey("SpecialFireworkEffect").getItemMeta();
                    firework.setFireworkMeta(meta);
                }
            }
            reduceItemInHand(player);
            if (event instanceof Cancellable) ((Cancellable)event).setCancelled(true);
            Msg.send(player, "&3Your enemies have been revealed!");
            player.playSound(player.getEyeLocation(), Sound.FIREWORK_LAUNCH, 1f, 1f);
        }
    }

    boolean onTrigger(Player player, Block block)
    {
        switch (block.getType()) {
        case IRON_PLATE:
        case GOLD_PLATE:
        case WOOD_PLATE:
        case STONE_PLATE:
            break;
        default: return false;
        }
        if (!landMines.containsKey(block)) return false;
        UUID owner = landMines.remove(block);
        getSurvivalPlayer(player).setLastDamager(owner);
        block.setType(Material.AIR);
        final double POWER = 4.0;
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), 4f, true, false);
        getLogger().info(String.format("%s triggered land mine at %d,%d,%d", player.getName(), block.getX(), block.getY(), block.getZ()));
        return true;
    }

    @EventHandler(ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        switch (event.getAction()) {
        case RIGHT_CLICK_AIR:
        case RIGHT_CLICK_BLOCK:
            onUse(event.getPlayer(), event);
            break;
        case PHYSICAL:
            if (event.hasBlock()) {
                if (onTrigger(event.getPlayer(), event.getClickedBlock())) {
                    event.setCancelled(true);
                }
            }
            break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        onUse(event.getPlayer(), event);
        if (event.getPlayer().getItemInHand().getType() == Material.FLINT_AND_STEEL &&
            world.getPVP() &&
            event.getRightClicked() instanceof Player) {
            reduceItemInHand(event.getPlayer());
            event.getRightClicked().setFireTicks(event.getRightClicked().getFireTicks() + 20*20);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLeave(PlayerLeaveEvent event) {
        if (getSurvivalPlayer(event.getPlayer()) == null) {
            getSurvivalPlayer(event.getPlayer()).setEndTime(new Date());
        }
        getSurvivalPlayer(event.getPlayer()).recordHighscore();
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
        case COUNTDOWN:
            if (event.getFrom().distanceSquared(event.getTo()) > 2.0) {
                makeImmobile(player, getSurvivalPlayer(player).getSpawnLocation());
                event.setCancelled(true);
            }
            break;
        case SUDDEN_DEATH:
            if (player.getLocation().distanceSquared(world.getSpawnLocation()) > SUDDEN_DEATH_RADIUS*SUDDEN_DEATH_RADIUS) {
                player.teleport(getSurvivalPlayer(player).getSafeLocation());
                Msg.send(player, "&4You cannot leave spawn during Sudden Death");
                Title.show(player, "", "&4Sudden Death");
            } else {
                getSurvivalPlayer(player).setSafeLocation(player.getLocation());
            }
            break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event)
    {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event)
    {
        if (!(event.getEntity() instanceof Player)) return;
        Player damagee = (Player)event.getEntity();
        if (!getSurvivalPlayer(damagee).isPlayer()) return;
        Player damager = null;
        Entity entity = event.getDamager();
        if (entity instanceof Player) {
            damager = (Player)damager;
        } else if (entity instanceof Projectile &&
                   ((Projectile)entity).getShooter() instanceof Player) {
            damager = (Player)((Projectile)entity).getShooter();
        } else {
            return;
        }
        if (damager == null) return;
        if (!getSurvivalPlayer(damager).isPlayer()) return;
        getSurvivalPlayer(damagee).setLastDamager(damager.getUniqueId());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event)
    {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player)event.getEntity();
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            player.teleport(getSurvivalPlayer(player).getSpawnLocation());
            if (getSurvivalPlayer(player).isPlayer()) {
                player.setHealth(0);
            }
        }
    }

    Object button(String chat, String tooltip, String command)
    {
        Map<String, Object> map = new HashMap<>();
        map.put("text", Msg.format(chat));
        Map<String, Object> map2 = new HashMap<>();
        map.put("clickEvent", map2);
        map2.put("action", "run_command");
        map2.put("value", command);
        map2 = new HashMap();
        map.put("hoverEvent", map2);
        map2.put("action", "show_text");
        map2.put("value", Msg.format(tooltip));
        return map;
    }

    void showHighscore(Player player, List<Highscore.Entry> entries)
    {
        int i = 1;
        Msg.send(player, "&b&lSurvival Games Highscore");
        Msg.send(player, "&3Rank &fGames &4Kills &3Name");
        for (Highscore.Entry entry : entries) {
            Msg.send(player, "&3#%02d &f%02d &4%d &3%s", i++, entry.getCount(), entry.getKills(), entry.getName());
        }
    }

    void showHighscore(Player player)
    {
        List<Highscore.Entry> entries = highscore.list();
        showHighscore(player, entries);
    }
}
