package com.cavetale.survivalgames;

import com.cavetale.afk.AFKPlugin;
import com.cavetale.core.util.Json;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.MytemsTag;
import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import com.winthier.title.TitlePlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

@Getter
public final class SurvivalGamesPlugin extends JavaPlugin implements Listener {
    static final long RESTOCK_SECONDS = 100;
    static final long RESTOCK_VARIANCE = 15;
    static final double SUDDEN_DEATH_RADIUS = 24;
    // minigame stuf
    World world;
    @Setter boolean debug = false;
    // chunk processing
    Set<ChunkCoord> processedChunks = new HashSet<>();
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
    long totalTicks;
    long stateTicks;
    long restockTicks = 0;
    long secondsLeft;
    int restockPhase = 0;
    UUID winnerUuid = null;
    String winnerName = null;
    SurvivalTeam winnerTeam = null;
    @Setter State state = State.IDLE;
    // file config
    final Map<String, ItemStack> stockItems = new HashMap<>();
    final List<List<LootItem>> phaseItems = new ArrayList<>();
    final List<ItemStack> kitItems = new ArrayList<>();
    final Map<UUID, SurvivalPlayer> survivalPlayers = new HashMap<>();
    final Map<SurvivalTeam, TeamScore> teams = new EnumMap<>(SurvivalTeam.class);
    private List<String> worldNames;
    private List<Mob> spawnedMonsters = new ArrayList<>();
    BossBar bossBar;
    protected File saveFile;
    protected SaveTag saveTag;

    @Value static final class ChunkCoord {
        int x;
        int z;
    }

    // Setup event handlers
    @Override @SuppressWarnings("unchecked")
    public void onEnable() {
        saveFile = new File(getDataFolder(), "save.json");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getCommand("survivalgames").setExecutor(new SurvivalGamesCommand(this).enable());
        getCommand("teammsg").setExecutor(new TeamCommand(this));
        bossBar = Bukkit.createBossBar("Survival Games", BarColor.RED, BarStyle.SOLID);
        try {
            loadConfigFiles();
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            enter(player);
        }
        load();
    }

    public void onDisable() {
        save();
        for (Player player : Bukkit.getOnlinePlayers()) {
            exit(player);
        }
    }

    protected void load() {
        saveTag = Json.load(saveFile, SaveTag.class, SaveTag::new);
    }

    protected void save() {
        Json.save(saveFile, saveTag);
    }

    void enter(Player player) {
        bossBar.addPlayer(player);
    }

    void exit(Player player) {
        bossBar.removePlayer(player);
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        player.setWalkSpeed(0.2f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.1f);
        player.setWalkSpeed(0.2f);
    }

    /**
     * Copy folder src to new destination and name dst.
     */
    void copyFolder(File src, File dst) {
        dst.mkdir();
        for (File file : src.listFiles()) {
            copyFileInternal(file, dst, 0);
        }
    }

    void copyFileInternal(File src, File dst, int level) {
        if (debug) {
            getLogger().info("Copying files: " + src + ", " + dst + ", " + level);
        }
        if (src.isDirectory()) {
            File dst2 = new File(dst, src.getName());
            dst2.mkdir();
            for (File src2 : src.listFiles()) {
                copyFileInternal(src2, dst2, level + 1);
            }
        } else if (src.isFile()) {
            if (level == 0 && src.getName().equals("uid.dat")) return;
            File dst2 = new File(dst, src.getName());
            try {
                Files.copy(src.toPath(), dst2.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException ioe) {
                getLogger().warning("Error copying files: " + src + ", " + dst + ", " + level);
                throw new IllegalStateException(ioe);
            }
        }
    }

    void removeFile(File file) {
        if (debug) {
            getLogger().info("Removing file: " + file);
        }
        if (file.isDirectory()) {
            for (File file2 : file.listFiles()) removeFile(file2);
        }
        if (!file.delete()) {
            throw new IllegalStateException("Cannot delete: " + file);
        }
    }

    void removeWorld() {
        if (world == null) return;
        World oldWorld = world;
        world = null;
        removeWorld(oldWorld);
    }

    void removeWorld(World theWorld) {
        for (Player player : theWorld.getPlayers()) {
            if (!player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation())) {
                player.kick(Component.text("Your world expired", NamedTextColor.RED));
            }
        }
        File file = theWorld.getWorldFolder();
        if (!Bukkit.unloadWorld(theWorld, false)) {
            throw new IllegalStateException("Cannot unload: " + theWorld.getName());
        }
        Bukkit.getScheduler().runTaskLater(this, () -> removeFile(file), 1L);
    }

    void loadWorld(String worldName) {
        File sourceFile = new File(new File(getDataFolder(), "worlds"), worldName);
        if (!sourceFile.isDirectory()) throw new IllegalStateException("Not a directory: " + sourceFile);
        String destName = null;
        File destFile = null;
        int worldSuffix = 0;
        do {
            worldSuffix += 1;
            destName = String.format("sg_%s_%02d", worldName.toLowerCase(), worldSuffix);
            destFile = new File(Bukkit.getWorldContainer(), destName);
            if (destFile.exists()) destFile = null;
        } while (destFile == null);
        copyFolder(sourceFile, destFile);
        ConfigurationSection worldConfig;
        File configFile = new File(destFile, "config.yml");
        if (!configFile.exists()) {
            getLogger().warning("World config not found: " + configFile);
        }
        worldConfig = YamlConfiguration.loadConfiguration(configFile);
        WorldCreator wc = WorldCreator.name(destName);
        wc.generator("VoidGenerator");
        wc.type(WorldType.FLAT);
        World.Environment environment;
        String env = worldConfig.getString("world.Environment", "NORMAL");
        try {
            environment = World.Environment.valueOf(env.toUpperCase());
        } catch (IllegalArgumentException iae) {
            getLogger().warning("Invalid environment: " + env);
            environment = World.Environment.NORMAL;
        }
        wc.environment(environment);
        processedChunks.clear();
        restockChests.clear();
        landMines.clear();
        credits.clear();
        world = wc.createWorld();
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.HARD);
        world.setPVP(false);
        world.setTime(1000L);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setStorm(false);
        world.setThundering(false);
        processChunk(world.getSpawnLocation().getChunk());
        for (Chunk chunk : world.getLoadedChunks()) {
            processChunk(world.getSpawnLocation().getChunk());
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadConfigFiles() {
        saveDefaultConfig();
        reloadConfig();
        worldNames = getConfig().getStringList("worlds");
        saveResource("items.yml", false);
        saveResource("phases.yml", false);
        // Load config files
        ConfigurationSection config;
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "items.yml"));
        stockItems.clear();
        for (String key : config.getKeys(false)) {
            ItemStack item = config.getItemStack(key);
            if (item == null) {
                getLogger().warning("Bad item key: " + key);
            } else {
                stockItems.put(key, item);
            }
        }
        config = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "phases.yml"));
        kitItems.clear();
        for (String key : config.getStringList("kit")) {
            kitItems.add(itemForKey(key));
        }
        phaseItems.clear();
        for (Object o : config.getList("phases")) {
            if (o instanceof List) {
                // Phase
                List<LootItem> phaseList = new ArrayList<>();
                for (Object p : (List<Object>) o) {
                    LootItem lootItem = new LootItem();
                    if (p instanceof String) {
                        String key = (String) p;
                        if (key.startsWith("#")) {
                            MytemsTag mytemsTag = MytemsTag.of(key.substring(1));
                            if (mytemsTag != null) {
                                for (Mytems mytems : mytemsTag.toList()) {
                                    LootItem lootItem2 = new LootItem();
                                    lootItem2.items.add(mytems.createItemStack());
                                    phaseList.add(lootItem2);
                                }
                            }
                            if (mytemsTag == null) {
                                throw new IllegalArgumentException("Tag not found: " + key);
                            }
                        } else {
                            lootItem.items.add(itemForKey(key));
                        }
                    } else if (p instanceof List) {
                        for (Object q : (List<Object>) p) {
                            if (q instanceof Number) {
                                Number number = (Number) q;
                                lootItem.chance = number.doubleValue();
                            } else if (q instanceof String) {
                                String key = (String) q;
                                lootItem.items.add(itemForKey(key));
                            }
                        }
                    }
                    if (!lootItem.items.isEmpty()) phaseList.add(lootItem);
                }
                phaseItems.add(phaseList);
            }
        }
        if (phaseItems.isEmpty()) throw new IllegalStateException("No phase items!");
    }

    private void tick() {
        if (world == null || state == State.IDLE) return;
        long ticks = totalTicks++;
        spawnedMonsters.removeIf(m -> !m.isValid());
        for (SurvivalPlayer sp : survivalPlayers.values()) {
            if (sp.isPlayer() && sp.isOnline()) {
                sp.health = sp.getPlayer().getHealth();
            }
        }
        if (ticks % 20 == 0 && state != State.COUNTDOWN) {
            // Update compass targets
            for (Player player : world.getPlayers()) {
                if (!getSurvivalPlayer(player).isPlayer() || !player.getInventory().contains(Material.COMPASS)) {
                    continue;
                }
                Location playerLoc = player.getLocation();
                Location loc = world.getSpawnLocation();
                double dist = Double.MAX_VALUE;
                for (Player target : world.getPlayers()) {
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
        State newState = null;
        if (state != State.END) {
            int aliveCount = 0;
            if (saveTag.useTeams) {
                for (TeamScore teamScore : teams.values()) {
                    teamScore.alivePlayers = 0;
                }
                for (SurvivalPlayer sp : getAlivePlayers()) {
                    TeamScore teamScore = teams.get(sp.team);
                    if (teamScore.alivePlayers == 0) aliveCount += 1;
                    teamScore.alivePlayers += 1;
                    winnerTeam = sp.team;
                }
                if (aliveCount > 1) winnerTeam = null;
            } else {
                for (SurvivalPlayer info : survivalPlayers.values()) {
                    if (info.isPlayer()) {
                        winnerUuid = info.getUuid();
                        aliveCount += 1;
                    }
                }
                if (aliveCount > 1) winnerUuid = null;
            }
            if (aliveCount == 2 && !compassesGiven) {
                compassesGiven = true;
                for (Player player : world.getPlayers()) {
                    if (getSurvivalPlayer(player).isPlayer()) {
                        player.getWorld().dropItemNaturally(player.getEyeLocation(), itemForKey("SpecialCompass")).setPickupDelay(0);
                    }
                }
            }
            if (!debug && saveTag.useTeams && aliveCount == 1 && winnerTeam != null) {
                if (debug) getLogger().info("Ending because there is 1 team left!");
                for (SurvivalPlayer sp : survivalPlayers.values()) {
                    if (sp.team == winnerTeam) sp.winner = true;
                }
                newState = State.END;
            } else if (!debug && !saveTag.useTeams && aliveCount == 1 && winnerUuid != null) {
                winnerName = getSurvivalPlayer(winnerUuid).getName();
                getSurvivalPlayer(winnerUuid).setWinner(true);
                getSurvivalPlayer(winnerUuid).setEndTime(new Date());
                if (debug) getLogger().info("Ending because there is 1 survivor!");
                newState = State.END;
            } else if (aliveCount == 0) {
                winnerName = null;
                if (debug) getLogger().info("Ending because there are no survivors!");
                newState = State.END;
            }
        }
        // Check for disconnects
        for (SurvivalPlayer info : new ArrayList<>(survivalPlayers.values())) {
            SurvivalPlayer sp = getSurvivalPlayer(info.getUuid());
            if (!info.isOnline()) {
                // Kick players who disconnect too long
                long discTicks = sp.getDiscTicks();
                if (state == State.SUDDEN_DEATH) {
                    survivalPlayers.remove(info.uuid);
                } else if (discTicks > 20 * 60) {
                    getLogger().info("Kicking " + sp.getName() + " because they were disconnected too long");
                    survivalPlayers.remove(info.uuid);
                }
                sp.setDiscTicks(discTicks + 1);
            }
        }
        if (newState == null) newState = tickState();
        if (newState != null && state != newState) {
            getLogger().info("Entering state: " + newState);
            onStateChange(state, newState);
            state = newState;
        }
    }

    void onStateChange(State oldState, State newState) {
        stateTicks = 0;
        switch (newState) {
        case COUNTDOWN:
            // Once the countdown starts, remove everyone who disconnected
            bossBar.setTitle(ChatColor.RED + "Get ready!");
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(1);
            for (SurvivalPlayer info : survivalPlayers.values()) {
                if (!info.isOnline()) {
                    survivalPlayers.remove(info.uuid);
                } else {
                    if (getSurvivalPlayer(info.getUuid()).isPlayer()) {
                        Player player = info.getPlayer();
                        if (player != null) {
                            for (ItemStack kitItem : kitItems) {
                                player.getInventory().addItem(kitItem.clone());
                            }
                        }
                    }
                }
            }
            break;
        case LOOTING:
            // Remove everyone who disconnected, reset everyone else so they can start playing
            bossBar.setTitle(ChatColor.GREEN + "Peaceful");
            bossBar.setColor(BarColor.GREEN);
            bossBar.setProgress(1);
            for (SurvivalPlayer info : survivalPlayers.values()) {
                if (info.isOnline() && info.isPlayer()) {
                    Player player = info.getPlayer();
                    makeMobile(player);
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }
            break;
        case FREE_FOR_ALL:
            bossBar.setTitle(ChatColor.DARK_RED + "Fight");
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(1);
            for (Player player : world.getPlayers()) {
                player.showTitle(Title.title(Component.empty(),
                                             Component.text("Fight!", NamedTextColor.RED)));
                player.sendMessage(Component.text("Fight!", NamedTextColor.RED));
                player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
            }
            world.setPVP(true);
            break;
        case COUNTDOWN_SUDDEN_DEATH:
            bossBar.setTitle(ChatColor.DARK_RED + "Sudden Death");
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(1);
            for (Player player : world.getPlayers()) {
                SurvivalPlayer sp = getSurvivalPlayer(player);
                if (sp.isPlayer()) {
                    makeImmobile(player, getSurvivalPlayer(player).getSpawnLocation());
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                } else if (sp.getSpawnLocation() != null) {
                    player.teleport(sp.getSpawnLocation());
                } else {
                    player.teleport(world.getSpawnLocation());
                }
            }
            world.setPVP(false);
            world.setTime(0);
            for (Mob mob : spawnedMonsters) {
                mob.remove();
            }
            spawnedMonsters.clear();
            break;
        case SUDDEN_DEATH:
            bossBar.setTitle(ChatColor.DARK_RED + "Sudden Death");
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(0);
            for (Player player : world.getPlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    makeMobile(player);
                }
            }
            world.setPVP(true);
            world.getWorldBorder().setCenter(world.getSpawnLocation());
            world.getWorldBorder().setSize(SUDDEN_DEATH_RADIUS * 2.0);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            break;
        case END:
            bossBar.setTitle(ChatColor.AQUA + "The End");
            bossBar.setColor(BarColor.BLUE);
            bossBar.setProgress(1);
            for (Player player : world.getPlayers()) {
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1f, 1f);
            }
            if (saveTag.event) {
                for (SurvivalPlayer sp : survivalPlayers.values()) {
                    if (sp.didPlay) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + sp.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + sp.getName() + " Katniss");
                    }
                    if (sp.winner) {
                        List<String> titles = List.of("Survivor", "Victor");
                        String cmd = "titles unlockset " + winnerName + " " + String.join(" ", titles);
                        getLogger().info("Running command: " + cmd);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    }
                }
            }
            for (Player player : world.getPlayers()) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            if (saveTag.useTeams) {
                if (winnerTeam != null) {
                    List<SurvivalPlayer> winners = getWinningPlayers();
                    String nameString = winners.stream()
                        .map(SurvivalPlayer::getName)
                        .collect(Collectors.joining(" "));
                    for (Player player : world.getPlayers()) {
                        player.sendMessage(Component.text().color(NamedTextColor.WHITE)
                                           .append(Component.text("Team "))
                                           .append(winnerTeam.component)
                                           .append(Component.text(" wins the game: " + nameString)));
                        player.showTitle(Title.title(winnerTeam.component,
                                                     Component.text("Wins the Game!", winnerTeam.color)));
                    }
                    getLogger().info(winnerTeam + " wins the game: " + nameString);
                } else {
                    for (Player player : world.getPlayers()) {
                        player.sendMessage(Component.text("Draw! Nobody wins", NamedTextColor.RED));
                        player.showTitle(Title.title(Component.text("Draw!", NamedTextColor.RED),
                                                     Component.text("Nobody wins", NamedTextColor.RED)));
                    }
                    getLogger().info("The game ends in a draw");
                }
            } else {
                if (winnerName != null) {
                    for (Player player : world.getPlayers()) {
                        player.sendMessage(Component.text(winnerName + " wins the game!", NamedTextColor.GREEN));
                        player.showTitle(Title.title(Component.text(winnerName, NamedTextColor.GREEN),
                                                     Component.text("Wins the Game!", NamedTextColor.GREEN)));
                    }
                    getLogger().info(winnerName + " wins the game");
                } else {
                    for (Player player : world.getPlayers()) {
                        player.sendMessage(Component.text("Draw! Nobody wins", NamedTextColor.RED));
                        player.showTitle(Title.title(Component.text("Draw!", NamedTextColor.RED),
                                                     Component.text("Nobody wins", NamedTextColor.RED)));
                    }
                    getLogger().info("The game ends in a draw");
                }
            }
        default: break;
        }
    }

    private State tickState() {
        long ticks = this.stateTicks++;
        switch (state) {
        case IDLE: return null;
        case COUNTDOWN: return tickCountdown(ticks);
        case LOOTING: return tickLooting(ticks);
        case FREE_FOR_ALL: return tickFreeForAll(ticks);
        case COUNTDOWN_SUDDEN_DEATH: return tickCountdownSuddenDeath(ticks);
        case SUDDEN_DEATH: return tickSuddenDeath(ticks);
        case END: return tickEnd(ticks);
        default: throw new IllegalStateException(state.name());
        }
    }

    State tickCountdown(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            long seconds = timeLeft / 20;
            secondsLeft = seconds;
            double progress = (double) seconds / (double) state.seconds;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            for (Player player : world.getPlayers()) {
                if (seconds == 0) {
                    player.showTitle(Title.title(Component.text("GO!", NamedTextColor.GREEN, TextDecoration.ITALIC),
                                                 Component.empty()));
                    player.sendMessage(Component.text("GO!", NamedTextColor.GREEN, TextDecoration.ITALIC));
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
                } else if (seconds == state.seconds) {
                    player.showTitle(Title.title(Component.text("Get Ready!", NamedTextColor.GREEN),
                                                 Component.text("Game starts in " + state.seconds + " seconds",
                                                                NamedTextColor.GREEN)));
                    player.sendMessage(Component.text("Game starts in " + seconds + " seconds", NamedTextColor.GREEN));
                } else if (seconds <= 10) {
                    player.showTitle(Title.title(Component.text(seconds, NamedTextColor.GREEN, TextDecoration.BOLD),
                                                 Component.text("Game Start", NamedTextColor.GREEN)));
                    player.sendMessage("" + ChatColor.GREEN + seconds);
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int) ticks / 20));
                }
                SurvivalPlayer sp = getSurvivalPlayer(player);
                if (sp.isPlayer()) {
                    Location loc1 = player.getLocation();
                    Location loc2 = sp.getSpawnLocation();
                    double dist = loc1.getWorld().equals(loc2.getWorld())
                        ? loc1.distanceSquared(loc2)
                        : Double.MAX_VALUE;
                    if (dist >= 1.0) {
                        makeImmobile(player, sp.getSpawnLocation());
                    }
                }
            }
        }
        if (timeLeft <= 0) return State.LOOTING;
        return null;
    }

    State tickLooting(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft <= 0) return State.FREE_FOR_ALL;
        if (timeLeft % 20 == 0) {
            long seconds = timeLeft / 20;
            secondsLeft = seconds;
            double progress = (double) seconds / (double) state.seconds;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
        }
        return null;
    }

    State tickFreeForAll(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        long limit = state.seconds * 10L;
        if (timeLeft % 20 == 0) {
            long seconds = timeLeft / 20;
            secondsLeft = seconds;
            double progress = (double) seconds / (double) state.seconds;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            if (timeLeft < limit) {
                for (Player player : new ArrayList<>(world.getPlayers())) {
                    if (getSurvivalPlayer(player).isPlayer()) {
                        tryToSpawnMob(player);
                    }
                }
            }
        }
        if (timeLeft < limit && world.isDayTime()) {
            world.setTime(world.getTime() + 100L);
        }
        if (restockTicks <= 0) {
            restockTicks = (RESTOCK_SECONDS + (random.nextLong() % RESTOCK_VARIANCE) - (random.nextLong() % RESTOCK_VARIANCE)) * 20;
        } else {
            if (--restockTicks <= 0) {
                restockPhase += 1;
                restockAllChests();
            }
        }
        if (timeLeft <= 0) return State.COUNTDOWN_SUDDEN_DEATH;
        return null;
    }

    void tryToSpawnMob(Player player) {
        if (spawnedMonsters.size() > 100) return;
        Location loc = player.getLocation();
        Vector vec = new Vector(random.nextDouble(), 0.0, random.nextDouble());
        double distance = 24.0 + random.nextDouble() * 24.0;
        vec = vec.normalize().multiply(distance);
        loc = loc.add(vec);
        Block block = loc.getBlock();
        for (int i = 0; i < 8; i += 1) {
            if (!block.isEmpty()) break;
            block = block.getRelative(0, -1, 0);
        }
        for (int i = 0; i < 8; i += 1) {
            if (block.isEmpty()) break;
            block = block.getRelative(0, 1, 0);
        }
        if (!block.isEmpty() || !block.getRelative(0, 1, 0).isEmpty()) return;
        if (!block.getRelative(0, -1, 0).isSolid()) return;
        loc = block.getLocation().add(0.5, 0.0, 0.5);
        boolean tooClose = false;
        double exclusionRadiusSq = 24.0 * 24.0;
        for (Player nearby : world.getPlayers()) {
            if (!getSurvivalPlayer(player).isPlayer()) continue;
            if (loc.distanceSquared(nearby.getLocation()) < exclusionRadiusSq) {
                return;
            }
        }
        int nearbyMobCount = 0;
        for (Mob nearby : spawnedMonsters) {
            if (loc.distanceSquared(nearby.getLocation()) < exclusionRadiusSq) {
                nearbyMobCount += 1;
                if (nearbyMobCount > 4) return;
            }
        }
        List<EntityType> entityTypes = Arrays.asList(EntityType.ZOMBIE,
                                                     EntityType.CREEPER,
                                                     EntityType.SKELETON,
                                                     EntityType.SPIDER,
                                                     EntityType.BLAZE,
                                                     EntityType.DROWNED,
                                                     EntityType.HOGLIN,
                                                     EntityType.HUSK,
                                                     EntityType.STRAY,
                                                     EntityType.VINDICATOR,
                                                     EntityType.WITCH,
                                                     EntityType.WITHER_SKELETON,
                                                     EntityType.WITHER_SKELETON,
                                                     EntityType.ZOGLIN,
                                                     EntityType.ZOMBIE_VILLAGER,
                                                     EntityType.RAVAGER);
        EntityType entityType = entityTypes.get(random.nextInt(entityTypes.size()));
        Mob mob = (Mob) loc.getWorld().spawn(loc, entityType.getEntityClass(), e -> {
                e.setPersistent(false);
                if (e instanceof Mob) ((Mob) e).setRemoveWhenFarAway(true);
                if (e instanceof Zombie) ((Zombie) e).setShouldBurnInDay(false);
                if (e instanceof Skeleton) ((Skeleton) e).setShouldBurnInDay(false);
            });
        spawnedMonsters.add(mob);
    }

    State tickCountdownSuddenDeath(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            long seconds = timeLeft / 20;
            secondsLeft = seconds;
            double progress = (double) seconds / (double) state.seconds;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            for (Player player : world.getPlayers()) {
                if (seconds == 0) {
                    player.showTitle(Title.title(Component.empty(),
                                                 Component.text("KILL!", NamedTextColor.RED, TextDecoration.BOLD)));
                    player.sendMessage(Component.text("KILL!", NamedTextColor.RED, TextDecoration.BOLD));
                } else if (seconds == state.seconds) {
                    player.showTitle(Title.title(Component.text("Get Ready!", NamedTextColor.RED),
                                                 Component.text("Sudden death in " + seconds + " seconds", NamedTextColor.RED)));
                    player.sendMessage(Component.text("Sudden death in " + state.seconds + " seconds", NamedTextColor.RED));
                } else {
                    player.showTitle(Title.title(Component.text(seconds, NamedTextColor.RED, TextDecoration.BOLD),
                                                 Component.text("Sudden Death", NamedTextColor.RED)));
                    player.sendMessage(Component.text("Sudden Death " + seconds, NamedTextColor.RED));
                }
            }
        }
        if (timeLeft <= 0) return State.SUDDEN_DEATH;
        return null;
    }

    State tickSuddenDeath(long ticks) {
        if (ticks > 0 && (ticks % 200) == 0) {
            for (Player player : world.getPlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    player.setFoodLevel(Math.max(2, player.getFoodLevel() - 2));
                    player.damage(1.0);
                }
            }
        }
        return null;
    }

    State tickEnd(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20 == 0) {
            long seconds = timeLeft / 20;
            secondsLeft = seconds;
            double progress = (double) seconds / (double) state.seconds;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
        }
        if (timeLeft <= 0) {
            stopGame();
            return State.IDLE;
        }
        return null;
    }

    ItemStack itemForKey(String key) {
        int amount = 0;
        if (key.contains(";")) {
            String[] s = key.split(";", 2);
            try {
                amount = Integer.parseInt(s[1]);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Invalid amount: " + key);
            }
            key = s[0];
        }
        ItemStack result = stockItems.get(key);
        if (result != null) {
            result = result.clone();
            if (amount > 0) result.setAmount(amount);
            return result;
        }
        Material material = Material.getMaterial(key.toUpperCase());
        if (material != null) return new ItemStack(material, (amount > 0 ? amount : 1));
        throw new IllegalArgumentException("Invalid item key: " + key);
    }

    List<ItemStack> randomPhaseItems(int index) {
        List<LootItem> lootTable = phaseItems.get(Math.min(index, phaseItems.size() - 1));
        if (lootTable.isEmpty()) throw new IllegalArgumentException("Restock phase empty: " + index);
        double chance = 0;
        for (LootItem lootItem : lootTable) chance += lootItem.chance;
        double roll = random.nextDouble() * chance;
        for (LootItem lootItem : lootTable) {
            roll -= lootItem.chance;
            if (roll <= 0) return lootItem.items;
        }
        return lootTable.get(lootTable.size() - 1).items;
    }

    SurvivalPlayer getSurvivalPlayer(UUID uuid) {
        return survivalPlayers.computeIfAbsent(uuid, SurvivalPlayer::new);
    }

    SurvivalPlayer getSurvivalPlayer(Player player) {
        SurvivalPlayer result = getSurvivalPlayer(player.getUniqueId());
        result.setName(player.getName());
        return result;
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        Location location = getSpawnLocation(player);
        if (location != null) event.setSpawnLocation(location);
    }

    public Location getSpawnLocation(Player player) {
        SurvivalPlayer sp = getSurvivalPlayer(player);
        switch (state) {
        case SUDDEN_DEATH:
        case COUNTDOWN:
        case COUNTDOWN_SUDDEN_DEATH:
            return sp.isPlayer()
                ? getSurvivalPlayer(player).getSpawnLocation()
                : world.getSpawnLocation();
        case FREE_FOR_ALL:
            return sp.isPlayer()
                ? null
                : world.getSpawnLocation();
        case IDLE:
        default:
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        enter(player);
        if (state == State.IDLE) {
            Players.reset(player);
            player.setGameMode(GameMode.ADVENTURE);
            return;
        }
        final SurvivalPlayer survivalPlayer = getSurvivalPlayer(player);
        if (survivalPlayer.isSpectator()) {
            player.setGameMode(GameMode.SPECTATOR);
            return;
        }
        switch (state) {
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

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        exit(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk.getWorld().equals(world)) {
            processChunk(chunk);
        }
    }

    private void processChunk(Chunk chunk) {
        final ChunkCoord cc = new ChunkCoord(chunk.getX(), chunk.getZ());
        if (processedChunks.contains(cc)) return;
        processedChunks.add(cc);
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState instanceof Chest) {
                Block block = blockState.getBlock();
                if (restockChests.contains(block)) {
                    getLogger().warning(String.format("Duplicate chest scanned at %d,%d,%d", block.getX(), block.getY(), block.getZ()));
                } else {
                    try {
                        ((Chest) blockState).getInventory().clear();
                    } catch (NullPointerException npe) {
                        npe.printStackTrace();
                    }
                    restockChests.add(blockState.getBlock());
                    for (int i = 0; i <= restockPhase; ++i) {
                        stockBlock(blockState.getBlock());
                    }
                }
            } else if (blockState instanceof Sign) {
                final Sign sign = (Sign) blockState;
                String firstLine = PlainTextComponentSerializer.plainText().serialize(sign.line(0)).toLowerCase();
                if (firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]")) {
                    if (firstLine.equals("[credits]")) {
                        for (int i = 1; i < 4; ++i) {
                            String credit = PlainTextComponentSerializer.plainText().serialize(sign.line(i));
                            if (credit != null) credits.add(credit);
                        }
                        blockState.getBlock().setType(Material.AIR);
                    } else if (firstLine.equals("[spawn]")) {
                        Location location = blockState.getBlock().getLocation();
                        location.add(0.5, 0.5, 0.5);
                        Vector lookAt = world.getSpawnLocation().toVector().subtract(location.toVector());
                        location.setDirection(lookAt);
                        spawnLocations.add(location);
                        blockState.getBlock().setType(Material.AIR);
                    } else if (firstLine.equals("[time]")) {
                        long time = 0;
                        String arg = PlainTextComponentSerializer.plainText().serialize(sign.line(1)).toLowerCase();
                        if ("day".equals(arg)) {
                            time = 1000;
                        } else if ("night".equals(arg)) {
                            time = 13000;
                        } else if ("noon".equals(arg)) {
                            time = 6000;
                        } else if ("midnight".equals(arg)) {
                            time = 18000;
                        } else {
                            try {
                                time = Long.parseLong(PlainTextComponentSerializer.plainText().serialize(sign.line(1)));
                            } catch (NumberFormatException nfe) { }
                        }
                        world.setTime(time);
                        if ("lock".equalsIgnoreCase(PlainTextComponentSerializer.plainText().serialize(sign.line(2)))) {
                            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                        } else {
                            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                        }
                        blockState.getBlock().setType(Material.AIR);
                    }
                }
            }
        }
    }

    private boolean isNearAnyPlayer(Block block) {
        final int r = 16;
        final int vr = 8;
        for (Player player : world.getPlayers()) {
            final int px;
            final int py;
            final int pz;
            do {
                final Location tmp = player.getLocation();
                px = tmp.getBlockX();
                py = tmp.getBlockY();
                pz = tmp.getBlockZ();
            } while (false);
            final int dx = Math.abs(px - block.getX());
            if (dx > r) continue;
            final int dy = Math.abs(py - block.getY());
            if (dy > vr) continue;
            final int dz = Math.abs(pz - block.getZ());
            if (dz > r) continue;
            return true;
        }
        return false;
    }

    void makeImmobile(Player player, Location location) {
        if (!player.getLocation().getWorld().equals(location.getWorld()) || player.getLocation().distanceSquared(location) > 0.5) {
            player.teleport(location);
            getLogger().info("Teleported " + player.getName() + " to their spawn location");
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0);
        player.setWalkSpeed(0);
    }

    void makeMobile(Player player) {
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

    void restockAllChests() {
        int count = 0;
        int skipped = 0;
        final double chestRadius = 20;
        final double rr = chestRadius * chestRadius;
    blockLoop:
        for (Block block : restockChests) {
            Location blockLocation = block.getLocation();
            // Near spawn?
            if (restockPhase > 0 && blockLocation.distanceSquared(world.getSpawnLocation()) < rr) {
                skipped++;
                continue blockLoop;
            }
            // See if player is nearby
            for (Player player : world.getPlayers()) {
                if (getSurvivalPlayer(player).isPlayer()) {
                    if (player.getLocation().distanceSquared(blockLocation) < rr) {
                        skipped++;
                        continue blockLoop;
                    }
                }
            }
            if (stockBlock(block)) count++;
        }
        getLogger().info("Phase " + restockPhase + ": Restocked " + count + " chests, skipped " + skipped);
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : world.getPlayers()) {
                    player.showTitle(Title.title(Component.empty(),
                                                 Component.text("Chests restocked", NamedTextColor.GREEN)));
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
            }
        }.runTaskLater(this, 20 * 3);
    }

    boolean stockBlock(Block block) {
        BlockState blockState = block.getState();
        if (!(blockState instanceof Chest)) return false;
        stockChest(((Chest) blockState).getBlockInventory());
        return false;
    }

    void stockChest(Inventory inv) {
        try {
            for (ItemStack item : randomPhaseItems(restockPhase)) {
                int i = random.nextInt(inv.getSize());
                inv.setItem(i, item.clone());
            }
        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }
    }

    // Event Handlers

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!player.getWorld().equals(world)) {
            event.setCancelled(true);
            return;
        }
        final Block block = event.getBlock();
        Material material = block.getType();
        if (material == Material.FIRE) {
            return;
        }
        ItemStack itemInHand = event.getItemInHand();
        if (Tag.PRESSURE_PLATES.isTagged(material) && itemForKey("SpecialLandMine").isSimilar(itemInHand)) {
            landMines.put(block, player.getUniqueId());
            getLogger().info(String.format("%s placed Land Mine at %d,%d,%d", player.getName(), block.getX(), block.getY(), block.getZ()));
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!player.getWorld().equals(world)) {
            event.setCancelled(true);
            return;
        }
        switch (state) {
        case IDLE:
        case COUNTDOWN:
        case COUNTDOWN_SUDDEN_DEATH:
            event.setCancelled(true);
            return;
        default: break;
        }
        Block block = event.getBlock();
        Material material = block.getType();
        if (Tag.TALL_FLOWERS.isTagged(material)) {
            return; // Allow
        }
        if (material == Material.FIRE) return;
        if (Tag.PRESSURE_PLATES.isTagged(material) && landMines.containsKey(block)) {
            event.setCancelled(true);
            landMines.remove(block);
            block.setType(Material.AIR);
            world.dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), itemForKey("SpecialLandMine"));
            return;
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
        default:
            event.setCancelled(true);
        }
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
            player.setHealth(20.0);
            return;
        }
        Players.reset(player);
        getSurvivalPlayer(player).setSpectator();
        player.setGameMode(GameMode.SPECTATOR);
        // Score
        Player killer = player.getKiller();
        if (killer != null && !killer.equals(player)) {
            getSurvivalPlayer(killer).addKills(1);
        } else {
            UUID lastDamager = getSurvivalPlayer(player).getLastDamager();
            if (lastDamager != null) {
                SurvivalPlayer spKiller = getSurvivalPlayer(lastDamager);
                if (spKiller.isPlayer()) {
                    getSurvivalPlayer(lastDamager).addKills(1);
                }
            }
        }
        // Lighting
        player.getWorld().strikeLightningEffect(player.getLocation());
        // Record end time
        getSurvivalPlayer(player).setEndTime(new Date());
    }

    void reduceItemInHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(item);
        }
    }

    void onUse(Player player, Event event, ItemStack item) {
        if (itemForKey("SpecialFirework").isSimilar(item)) {
            for (Player other : world.getPlayers()) {
                if (other.equals(player) || !getSurvivalPlayer(other).isPlayer()) continue;
                other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, true, true));
            }
            item.subtract(1);
            if (event instanceof Cancellable) ((Cancellable) event).setCancelled(true);
            player.sendMessage(ChatColor.GREEN + "Your enemies have been revealed!");
            player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        }
    }

    boolean onTrigger(Player player, Block block) {
        if (Tag.PRESSURE_PLATES.isTagged(block.getType()) && landMines.containsKey(block)) {
            UUID owner = landMines.remove(block);
            getSurvivalPlayer(player).setLastDamager(owner);
            block.setType(Material.AIR);
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), 4f, true, false);
            getLogger().info(String.format("%s triggered land mine at %d,%d,%d", player.getName(), block.getX(), block.getY(), block.getZ()));
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        switch (event.getAction()) {
        case RIGHT_CLICK_AIR:
        case RIGHT_CLICK_BLOCK:
            if (event.hasBlock() && state != State.LOOTING && state != State.FREE_FOR_ALL && state != State.SUDDEN_DEATH) {
                event.setCancelled(true);
            }
            onUse(event.getPlayer(), event, event.getItem());
            break;
        case PHYSICAL:
            if (event.hasBlock()) {
                Block block = event.getClickedBlock();
                if (block.getType() == Material.FARMLAND) {
                    event.setCancelled(true);
                    return;
                }
                if (onTrigger(event.getPlayer(), block)) {
                    event.setCancelled(true);
                }
            }
            break;
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        onUse(event.getPlayer(), event, item);
        boolean isFlint = item.getType() == Material.FLINT_AND_STEEL;
        if (isFlint && world.getPVP() && event.getRightClicked() instanceof Player) {
            item.subtract(1);
            event.getRightClicked().setFireTicks(event.getRightClicked().getFireTicks() + 20 * 20);
            event.setCancelled(true);
        }
        switch (event.getRightClicked().getType()) {
        case ITEM_FRAME:
        case ARMOR_STAND:
            event.setCancelled(true);
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        switch (event.getRightClicked().getType()) {
        case ITEM_FRAME:
        case ARMOR_STAND:
            event.setCancelled(true);
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!player.getWorld().equals(world)) return;
        SurvivalPlayer sp = getSurvivalPlayer(player);
        if (!sp.isPlayer()) return;
        switch (state) {
        case IDLE: return;
        case COUNTDOWN:
        case COUNTDOWN_SUDDEN_DEATH:
            if (event.getFrom().distanceSquared(event.getTo()) > 0.1) {
                makeImmobile(player, getSurvivalPlayer(player).getSpawnLocation());
                event.setCancelled(true);
            }
            break;
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        event.setCancelled(true);
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        switch (event.getEntity().getType()) {
        case ITEM_FRAME:
        case ARMOR_STAND:
            event.setCancelled(true);
            return;
        default: break;
        }
        if (!(event.getEntity() instanceof Player)) return;
        Player damagee = (Player) event.getEntity();
        if (!getSurvivalPlayer(damagee).isPlayer()) return;
        Entity entity = event.getDamager();
        Player damager = getPlayerDamager(entity);
        if (damager == null) return;
        if (!getSurvivalPlayer(damager).isPlayer()) {
            event.setCancelled(true);
            return;
        }
        if (saveTag.useTeams && getSurvivalPlayer(damager).team == getSurvivalPlayer(damagee).team) {
            event.setCancelled(true);
            return;
        }
        getSurvivalPlayer(damagee).setLastDamager(damager.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
        Player damager = getPlayerDamager(event.getCombuster());
        if (damager == null) return;
        if (event.getEntity() instanceof Player) {
            Player damagee = (Player) event.getEntity();
            if (saveTag.useTeams && getSurvivalPlayer(damager).team == getSurvivalPlayer(damagee).team) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileCollide(ProjectileCollideEvent event) {
        Projectile projectile = event.getEntity();
        Player shooter = getPlayerDamager(projectile);
        if (shooter == null) return;
        if (event.getCollidedWith() instanceof Player) {
            Player target = (Player) event.getCollidedWith();
            if (saveTag.useTeams && getSurvivalPlayer(shooter).team == getSurvivalPlayer(target).team) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            player.teleport(getSurvivalPlayer(player).getSpawnLocation());
            if (getSurvivalPlayer(player).isPlayer()) {
                Players.reset(player);
                player.setHealth(0);
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        event.setCancelled(true);
    }

    public void startGame(String worldName) {
        if (world != null) {
            removeWorld();
        }
        totalTicks = 0;
        stateTicks = 0;
        restockTicks = 0;
        restockPhase = 0;
        winnerName = null;
        winnerUuid = null;
        winnerTeam = null;
        spawnLocations.clear();
        spawnLocationsRandomized = false;
        spawnLocationIter = 0;
        compassesGiven = false;
        //
        loadWorld(worldName);
        survivalPlayers.clear();
        teams.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            SurvivalPlayer sp = getSurvivalPlayer(player);
            if (player.isPermissionSet("group.streamer") && player.hasPermission("group.streamer")) {
                sp.setSpectator();
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(world.getSpawnLocation());
                continue;
            }
            if (!saveTag.event && AFKPlugin.isAfk(player)) {
                sp.setSpectator();
                player.setGameMode(GameMode.SPECTATOR);
                player.teleport(world.getSpawnLocation());
                continue;
            }
            if (player.getGameMode() == GameMode.CREATIVE) continue;
            sp.setPlayer();
            sp.setSpawnLocation(dealSpawnLocation());
            sp.setSafeLocation(sp.getSpawnLocation());
            Players.reset(player);
            player.setGameMode(GameMode.SURVIVAL);
            makeImmobile(player, sp.getSpawnLocation());
        }
        if (saveTag.useTeams) {
            int teamCount = Math.max(2, Math.min(SurvivalTeam.values().length, survivalPlayers.size() / 5));
            List<SurvivalPlayer> spList = getAlivePlayers();
            Collections.shuffle(spList, random);
            for (int i = 0; i < spList.size(); i += 1) {
                SurvivalPlayer thePlayer = spList.get(i);
                SurvivalTeam theTeam = SurvivalTeam.values()[i % teamCount];
                thePlayer.team = theTeam;
                TitlePlugin.getInstance().setColor(thePlayer.getPlayer(), theTeam.color);
            }
            for (int i = 0; i < teamCount; i += 1) {
                SurvivalTeam team = SurvivalTeam.values()[i];
                teams.put(team, new TeamScore(team));
            }
        }
        state = State.COUNTDOWN;
        onStateChange(state, state);
    }

    public void stopGame() {
        if (world == null) return;
        for (Mob mob : spawnedMonsters) {
            mob.remove();
        }
        spawnedMonsters.clear();
        survivalPlayers.clear();
        for (Player player : world.getPlayers()) {
            if (!player.isPermissionSet("group.streamer") || !player.hasPermission("group.streamer")) {
                Players.reset(player);
                TitlePlugin.getInstance().setColor(player, null);
                player.setGameMode(GameMode.ADVENTURE);
            }
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        removeWorld();
        onStateChange(state, State.IDLE);
        state = State.IDLE;
    }

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        if (state == State.IDLE) return;
        List<Component> lines = new ArrayList<>();
        SurvivalPlayer theSurvivalPlayer = getSurvivalPlayer(event.getPlayer());
        if (theSurvivalPlayer.didPlay) {
            if (theSurvivalPlayer.team != null) {
                lines.add(Component.text("Your Team ", NamedTextColor.GRAY)
                          .append(theSurvivalPlayer.team.component));
                List<SurvivalPlayer> teamPlayers = getAlivePlayers(theSurvivalPlayer.team);
                Collections.sort(teamPlayers, (a, b) -> Integer.compare(b.kills, a.kills));
                for (SurvivalPlayer sp : teamPlayers) {
                    int hearts = (int) Math.round(sp.health);
                    Player player = sp.getPlayer();
                    lines.add(Component.text()
                              .append(Component.text("" + sp.kills, NamedTextColor.DARK_RED))
                              .append(Component.space())
                              .append(Component.text(hearts + "\u2665", NamedTextColor.RED))
                              .append(Component.space())
                              .append(player != null && sp.isPlayer()
                                      ? player.displayName()
                                      : Component.text(sp.name, NamedTextColor.GRAY))
                              .build());
                }
            }
        }
        if (!saveTag.useTeams && winnerName != null) {
            lines.add(Component.text()
                      .append(Component.text("Winner ", NamedTextColor.GRAY))
                      .append(Component.text(winnerName, NamedTextColor.GOLD))
                      .build());
        } else if (saveTag.useTeams && winnerTeam != null) {
            lines.add(Component.text()
                      .append(Component.text("Winner ", NamedTextColor.GRAY))
                      .append(winnerTeam.component)
                      .build());
        }
        if (secondsLeft > 0) {
            long m = secondsLeft / 60;
            long s = secondsLeft % 60;
            lines.add(Component.text()
                      .append(Component.text("Time ", NamedTextColor.GRAY))
                      .append(Component.text(m + "m " + s + "s", NamedTextColor.WHITE))
                      .build());
        }
        if (saveTag.useTeams) {
            List<TeamScore> list = new ArrayList<>(teams.values());
            Collections.sort(list, (a, b) -> Integer.compare(b.alivePlayers, a.alivePlayers));
            for (TeamScore teamScore : list) {
                if (teamScore.alivePlayers == 0) {
                    lines.add(Component.text("0p " + teamScore.team.displayName,
                                             NamedTextColor.DARK_GRAY));
                    continue;
                }
                lines.add(Component.join(JoinConfiguration.noSeparators(),
                                         Component.text(teamScore.alivePlayers + "\u2665 ", NamedTextColor.RED),
                                         Component.text(teamScore.kills + " ", NamedTextColor.DARK_RED),
                                         teamScore.team.component));
            }
        } else {
            List<SurvivalPlayer> list = new ArrayList<>(survivalPlayers.values());
            list.removeIf(i -> !i.isPlayer());
            Collections.sort(list, (b, a) -> Integer.compare(a.kills, b.kills));
            for (SurvivalPlayer sp : list) {
                int hearts = (int) Math.round(sp.health);
                Player player = sp.getPlayer();
                Component displayName = player != null
                    ? player.displayName()
                    : Component.text(sp.getName(), NamedTextColor.WHITE);
                lines.add(Component.text()
                          .append(Component.text("" + sp.kills, NamedTextColor.DARK_RED))
                          .append(Component.space())
                          .append(Component.text(hearts + "\u2665", NamedTextColor.RED))
                          .append(Component.space())
                          .append(displayName)
                          .build());
            }
        }
        if (!lines.isEmpty()) {
            event.add(this, Priority.HIGHEST, lines);
        }
    }

    @EventHandler
    void onEntityTarget(EntityTargetEvent event) {
        if (spawnedMonsters.contains(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    public List<SurvivalPlayer> getAlivePlayers() {
        List<SurvivalPlayer> result = new ArrayList<>();
        for (SurvivalPlayer sp : survivalPlayers.values()) {
            if (sp.isPlayer()) result.add(sp);
        }
        return result;
    }

    public List<SurvivalPlayer> getAlivePlayers(SurvivalTeam team) {
        List<SurvivalPlayer> result = new ArrayList<>();
        for (SurvivalPlayer sp : survivalPlayers.values()) {
            if (!sp.isPlayer()) continue;
            if (sp.team != team) continue;
            result.add(sp);
        }
        return result;
    }

    public List<SurvivalPlayer> getPlayers(SurvivalTeam team) {
        List<SurvivalPlayer> result = new ArrayList<>();
        for (SurvivalPlayer sp : survivalPlayers.values()) {
            if (sp.team != team) continue;
            result.add(sp);
        }
        return result;
    }

    public List<SurvivalPlayer> getWinningPlayers() {
        List<SurvivalPlayer> result = new ArrayList<>();
        for (SurvivalPlayer sp : survivalPlayers.values()) {
            if (!sp.didPlay) continue;
            if (!sp.winner) continue;
            result.add(sp);
        }
        return result;
    }
}
