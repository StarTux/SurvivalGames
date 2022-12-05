package com.cavetale.survivalgames;

import com.cavetale.afk.AFKPlugin;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.player.PlayerTeamQuery;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.MytemsTag;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import com.winthier.spawn.Spawn;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.TriState;
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
import org.bukkit.block.Barrel;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

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
    static final List<String> WINNER_TITLES = List.of("Survivor", "Victor", "SurviveTogether", "Arrow", "SpectralArrow");
    protected List<Highscore> highscore = List.of();
    protected List<Component> sidebarHighscore = List.of();
    private static final Component TITLE = text("Survival Games", DARK_RED, BOLD);
    private String worldName;

    @Value static final class ChunkCoord {
        int x;
        int z;
    }

    private void log(String msg) {
        getLogger().info("[" + worldName + "] " + msg);
    }

    private void warn(String msg) {
        getLogger().info("[" + worldName + "] " + msg);
    }

    // Setup event handlers
    @Override @SuppressWarnings("unchecked")
    public void onEnable() {
        saveFile = new File(getDataFolder(), "save.json");
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getCommand("survivalgames").setExecutor(new SurvivalGamesCommand(this).enable());
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
        for (String winnerTitle : WINNER_TITLES) {
            if (TitlePlugin.getInstance().getTitle(winnerTitle) == null) {
                warn("Title not found: " + winnerTitle);
            }
        }
    }

    public void onDisable() {
        save();
        for (Player player : Bukkit.getOnlinePlayers()) {
            exit(player);
        }
    }

    protected void load() {
        saveTag = Json.load(saveFile, SaveTag.class, SaveTag::new);
        computeHighscore();
    }

    protected void save() {
        Json.save(saveFile, saveTag);
    }

    private void enter(Player player) {
        bossBar.addPlayer(player);
    }

    private void exit(Player player) {
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
    private void copyFolder(File src, File dst) {
        dst.mkdir();
        for (File file : src.listFiles()) {
            copyFileInternal(file, dst, 0);
        }
    }

    private void copyFileInternal(File src, File dst, int level) {
        if (debug) {
            log("Copying files: " + src + ", " + dst + ", " + level);
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
                warn("Error copying files: " + src + ", " + dst + ", " + level);
                throw new IllegalStateException(ioe);
            }
        }
    }

    private void removeFile(File file) {
        if (debug) {
            log("Removing file: " + file);
        }
        if (file.isDirectory()) {
            for (File file2 : file.listFiles()) removeFile(file2);
        }
        if (!file.delete()) {
            throw new IllegalStateException("Cannot delete: " + file);
        }
    }

    private void removeWorld() {
        if (world == null) return;
        World oldWorld = world;
        world = null;
        removeWorld(oldWorld);
    }

    private void removeWorld(World theWorld) {
        for (Player player : theWorld.getPlayers()) {
            if (!Spawn.warp(player)) {
                player.kick(text("Your world expired", RED));
            }
        }
        File file = theWorld.getWorldFolder();
        if (!Bukkit.unloadWorld(theWorld, false)) {
            throw new IllegalStateException("Cannot unload: " + theWorld.getName());
        }
        Bukkit.getScheduler().runTaskLater(this, () -> removeFile(file), 1L);
    }

    private void loadWorld(Runnable callback) {
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
            warn("World config not found: " + configFile);
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
            warn("Invalid environment: " + env);
            environment = World.Environment.NORMAL;
        }
        wc.environment(environment);
        wc.keepSpawnLoaded(TriState.FALSE);
        world = wc.createWorld();
        processedChunks.clear();
        restockChests.clear();
        landMines.clear();
        credits.clear();
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.HARD);
        world.setPVP(false);
        world.setTime(1000L);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, true);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.DO_MOB_LOOT, true);
        world.setStorm(false);
        world.setThundering(false);
        Location spawn = world.getSpawnLocation();
        final int radius = 4;
        final int cx = spawn.getBlockX() >> 4;
        final int cz = spawn.getBlockZ() >> 4;
        final int[] locks = new int[1];
        locks[0] += 1;
        for (int z = cz - radius; z <= cz + radius; z += 1) {
            for (int x = cx - radius; x <= cx + radius; x += 1) {
                locks[0] += 1;
                world.getChunkAtAsync(x, z, (Consumer<Chunk>) chunk -> {
                        processChunk(chunk);
                        locks[0] -= 1;
                        if (locks[0] == 0) callback.run();
                    });
            }
        }
        locks[0] -= 1;
        if (locks[0] == 0) callback.run();
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
                warn("Bad item key: " + key);
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
                                for (Mytems mytems : mytemsTag.getMytems()) {
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
                        if (saveTag.useTeams && getSurvivalPlayer(player).team == getSurvivalPlayer(target).team) {
                            continue;
                        }
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
                log("Ending because there is 1 team left!");
                for (SurvivalPlayer sp : survivalPlayers.values()) {
                    if (sp.team == winnerTeam) sp.winner = true;
                }
                newState = State.END;
            } else if (!debug && !saveTag.useTeams && aliveCount == 1 && winnerUuid != null) {
                winnerName = getSurvivalPlayer(winnerUuid).getName();
                getSurvivalPlayer(winnerUuid).setWinner(true);
                getSurvivalPlayer(winnerUuid).setEndTime(new Date());
                log("Ending because there is 1 survivor!");
                newState = State.END;
            } else if (aliveCount == 0) {
                winnerName = null;
                log("Ending because there are no survivors!");
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
                    log("Kicking " + sp.getName() + " because they were disconnected too long");
                    survivalPlayers.remove(info.uuid);
                }
                sp.setDiscTicks(discTicks + 1);
            }
        }
        if (newState == null) newState = tickState();
        if (newState != null && state != newState) {
            log("Entering state: " + newState);
            onStateChange(state, newState);
            state = newState;
            save();
        }
    }

    private void onStateChange(State oldState, State newState) {
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
                            if (saveTag.event) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
                            }
                        }
                        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * (int) State.COUNTDOWN.seconds,
                                                                0, true, true, true));
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
                                             text("Fight!", RED)));
                player.sendMessage(text("Fight!", RED));
                player.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f);
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
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1f);
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
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 0.5f, 1f);
            }
            if (saveTag.event) {
                for (SurvivalPlayer sp : survivalPlayers.values()) {
                    if (sp.winner) {
                        String cmd = "titles unlockset " + sp.name + " " + String.join(" ", WINNER_TITLES);
                        log("Running command: " + cmd);
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
                        player.sendMessage(text().color(WHITE)
                                           .append(Component.newline())
                                           .append(text("Team "))
                                           .append(winnerTeam.component)
                                           .append(text(" wins the game: " + nameString))
                                           .append(Component.newline()));
                        player.showTitle(Title.title(winnerTeam.component,
                                                     text("Wins the Game!", winnerTeam.color)));
                    }
                    log(winnerTeam + " wins the game: " + nameString);
                } else {
                    for (Player player : world.getPlayers()) {
                        player.sendMessage(text("Draw! Nobody wins", RED));
                        player.showTitle(Title.title(text("Draw!", RED),
                                                     text("Nobody wins", RED)));
                    }
                    log("The game ends in a draw");
                }
            } else {
                if (winnerName != null) {
                    for (Player player : world.getPlayers()) {
                        player.sendMessage(text(winnerName + " wins the game!", GREEN));
                        player.showTitle(Title.title(text(winnerName, GREEN),
                                                     text("Wins the Game!", GREEN)));
                    }
                    log(winnerName + " wins the game");
                } else {
                    for (Player player : world.getPlayers()) {
                        player.sendMessage(text("Draw! Nobody wins", RED));
                        player.showTitle(Title.title(text("Draw!", RED),
                                                     text("Nobody wins", RED)));
                    }
                    log("The game ends in a draw");
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

    private State tickCountdown(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            long seconds = timeLeft / 20;
            secondsLeft = seconds;
            double progress = (double) seconds / (double) state.seconds;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            for (Player player : world.getPlayers()) {
                if (seconds == 0) {
                    player.showTitle(Title.title(text("GO!", GREEN, ITALIC),
                                                 Component.empty()));
                    player.sendMessage(text("GO!", GREEN, ITALIC));
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.5f, 1f);
                } else if (seconds == state.seconds) {
                    player.showTitle(Title.title(text("Get Ready!", GREEN),
                                                 text("Game starts in " + state.seconds + " seconds",
                                                                GREEN)));
                    player.sendMessage(text("Game starts in " + seconds + " seconds", GREEN));
                } else if (seconds <= 10) {
                    player.showTitle(Title.title(text(seconds, GREEN, BOLD),
                                                 text("Game Start", GREEN)));
                    player.sendMessage("" + ChatColor.GREEN + seconds);
                    if (seconds >= 0 && seconds <= 24) {
                        player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note(24 - (int) seconds));
                    }
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

    private State tickLooting(long ticks) {
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

    private State tickFreeForAll(long ticks) {
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

    private void tryToSpawnMob(Player player) {
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
        double exclusionRadiusSq = 32.0 * 32.0;
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
                if (nearbyMobCount > 2) return;
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
                                                     EntityType.ZOMBIE_VILLAGER);
        EntityType entityType = entityTypes.get(random.nextInt(entityTypes.size()));
        Mob mob = (Mob) loc.getWorld().spawn(loc, entityType.getEntityClass(), e -> {
                e.setPersistent(false);
                if (e instanceof Mob) ((Mob) e).setRemoveWhenFarAway(true);
                if (e instanceof Zombie) ((Zombie) e).setShouldBurnInDay(false);
                if (e instanceof Skeleton) ((Skeleton) e).setShouldBurnInDay(false);
            });
        spawnedMonsters.add(mob);
    }

    private State tickCountdownSuddenDeath(long ticks) {
        long timeLeft = state.seconds * 20 - ticks;
        if (timeLeft % 20L == 0) {
            long seconds = timeLeft / 20;
            secondsLeft = seconds;
            double progress = (double) seconds / (double) state.seconds;
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            for (Player player : world.getPlayers()) {
                if (seconds == 0) {
                    player.showTitle(Title.title(Component.empty(),
                                                 text("KILL!", RED, BOLD)));
                    player.sendMessage(text("KILL!", RED, BOLD));
                } else if (seconds == state.seconds) {
                    player.showTitle(Title.title(text("Get Ready!", RED),
                                                 text("Sudden death in " + seconds + " seconds", RED)));
                    player.sendMessage(text("Sudden death in " + state.seconds + " seconds", RED));
                } else {
                    player.showTitle(Title.title(text(seconds, RED, BOLD),
                                                 text("Sudden Death", RED)));
                    player.sendMessage(text("Sudden Death " + seconds, RED));
                }
            }
        }
        if (timeLeft <= 0) return State.SUDDEN_DEATH;
        return null;
    }

    private State tickSuddenDeath(long ticks) {
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

    private State tickEnd(long ticks) {
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

    public ItemStack itemForKey(String key) {
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

    private List<ItemStack> randomPhaseItems(int index) {
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

    private SurvivalPlayer getSurvivalPlayer(UUID uuid) {
        return survivalPlayers.computeIfAbsent(uuid, SurvivalPlayer::new);
    }

    private SurvivalPlayer getSurvivalPlayer(Player player) {
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
            return Spawn.get();
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
            if (blockState instanceof Chest || blockState instanceof Barrel) {
                Block block = blockState.getBlock();
                if (restockChests.contains(block)) {
                    warn(String.format("Duplicate chest scanned at %d,%d,%d", block.getX(), block.getY(), block.getZ()));
                } else {
                    try {
                        ((InventoryHolder) blockState).getInventory().clear();
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

    private void makeImmobile(Player player, Location location) {
        if (!player.getLocation().getWorld().equals(location.getWorld()) || player.getLocation().distanceSquared(location) > 0.5) {
            player.teleport(location);
            log("Teleported " + player.getName() + " to their spawn location");
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0);
        player.setWalkSpeed(0);
    }

    private void makeMobile(Player player) {
        player.setWalkSpeed(.2f);
        player.setFlySpeed(.1f);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    private Location dealSpawnLocation() {
        if (spawnLocations.isEmpty()) return world.getSpawnLocation();
        if (!spawnLocationsRandomized) {
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations, random);
        }
        if (spawnLocationIter >= spawnLocations.size()) spawnLocationIter = 0;
        int i = spawnLocationIter++;
        return spawnLocations.get(i);
    }

    private void restockAllChests() {
        int count = 0;
        int skipped = 0;
        final double chestRadius = 8;
        final double rr = chestRadius * chestRadius;
    blockLoop:
        for (Block block : restockChests) {
            Location blockLocation = block.getLocation();
            // Near spawn?
            if (restockPhase > 0 && blockLocation.distanceSquared(world.getSpawnLocation()) < rr) {
                skipped++;
                continue blockLoop;
            }
            if (stockBlock(block)) count++;
        }
        log("Phase " + restockPhase + ": Restocked " + count + " chests, skipped " + skipped);
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : world.getPlayers()) {
                    player.showTitle(Title.title(Component.empty(),
                                                 text("Chests restocked", GREEN)));
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1f);
                }
            }
        }.runTaskLater(this, 20 * 3);
    }

    private boolean stockBlock(Block block) {
        BlockState blockState = block.getState();
        if (blockState instanceof Chest) {
            stockChest(((Chest) blockState).getBlockInventory());
            return true;
        } else if (blockState instanceof Barrel) {
            stockChest(((Barrel) blockState).getInventory());
            return true;
        }
        return false;
    }

    private void stockChest(Inventory inv) {
        final int rolls = 1 + Math.max(0, random.nextInt(3) - random.nextInt(2));
        try {
            for (int j = 0; j < rolls; j += 1) {
                for (ItemStack item : randomPhaseItems(restockPhase)) {
                    int i = random.nextInt(inv.getSize());
                    inv.setItem(i, item.clone());
                }
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
            log(String.format("%s placed Land Mine at %d,%d,%d", player.getName(), block.getX(), block.getY(), block.getZ()));
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
            SurvivalPlayer survivalKiller = getSurvivalPlayer(killer);
            survivalKiller.addKills(1);
            if (saveTag.event) {
                saveTag.addKills(survivalKiller.uuid, 1);
                computeHighscore();
            }
            if (survivalKiller.team != null && survivalKiller.team != getSurvivalPlayer(player).team) {
                teams.get(survivalKiller.team).kills += 1;
            }
        } else {
            UUID lastDamager = getSurvivalPlayer(player).getLastDamager();
            if (lastDamager != null) {
                SurvivalPlayer survivalKiller = getSurvivalPlayer(lastDamager);
                if (survivalKiller.isPlayer()) {
                    survivalKiller.addKills(1);
                    if (saveTag.event) {
                        saveTag.addKills(survivalKiller.uuid, 1);
                        computeHighscore();
                    }
                    if (survivalKiller.team != null && survivalKiller.team != getSurvivalPlayer(player).team) {
                        teams.get(survivalKiller.team).kills += 1;
                    }
                }
            }
        }
        // Lighting
        player.getWorld().strikeLightningEffect(player.getLocation());
        // Record end time
        getSurvivalPlayer(player).setEndTime(new Date());
    }

    private void reduceItemInHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(item);
        }
    }

    private boolean onUse(Player player, Event event, ItemStack item) {
        if (!state.canUseItem()) return false;
        SurvivalPlayer sp = getSurvivalPlayer(player);
        if (sp == null) return false;
        if (itemForKey("SpecialFirework").isSimilar(item)) {
            for (Player other : world.getPlayers()) {
                if (other.equals(player) || !getSurvivalPlayer(other).isPlayer()) continue;
                if (saveTag.useTeams && getSurvivalPlayer(player).team == getSurvivalPlayer(other).team) {
                    continue;
                }
                other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, true, true));
            }
            item.subtract(1);
            if (event instanceof Cancellable) ((Cancellable) event).setCancelled(true);
            player.sendMessage(ChatColor.GREEN + "Your enemies have been revealed!");
            player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1f);
            return true;
        } else if (itemForKey("RespawnTotem").isSimilar(item)) {
            if (!saveTag.useTeams) return false;
            SurvivalTeam team = sp.getTeam();
            if (team == null) return false;
            List<Player> options = new ArrayList<>();
            for (SurvivalPlayer other : survivalPlayers.values()) {
                if (other.getTeam() != team) continue;
                Player otherPlayer = other.getPlayer();
                if (otherPlayer == null || otherPlayer.getGameMode() != GameMode.SPECTATOR) continue;
                options.add(otherPlayer);
            }
            if (options.isEmpty()) {
                player.sendActionBar(text("No dead teammates found!", DARK_RED));
                return false;
            }
            Player revivePlayer = options.get(random.nextInt(options.size()));
            revivePlayer.teleport(player);
            Players.reset(revivePlayer);
            getSurvivalPlayer(revivePlayer).setPlayer();
            for (Player target : world.getPlayers()) {
                target.sendMessage(join(noSeparators(),
                                        revivePlayer.displayName(),
                                        text(" was revived by ", GREEN),
                                        player.displayName(),
                                        text(" for team ", GREEN),
                                        team.component));
            }
            if (event instanceof Cancellable) ((Cancellable) event).setCancelled(true);
            item.subtract(1);
            return true;
        }
        return false;
    }

    private boolean onTrigger(Player player, Block block) {
        if (Tag.PRESSURE_PLATES.isTagged(block.getType()) && landMines.containsKey(block)) {
            UUID owner = landMines.remove(block);
            getSurvivalPlayer(player).setLastDamager(owner);
            block.setType(Material.AIR);
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), 4f, true, false);
            log(String.format("%s triggered land mine at %d,%d,%d", player.getName(), block.getX(), block.getY(), block.getZ()));
            return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = false)
    private void onPlayerInteract(PlayerInteractEvent event) {
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
    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
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
    private void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        switch (event.getRightClicked().getType()) {
        case ITEM_FRAME:
        case ARMOR_STAND:
            event.setCancelled(true);
        default: break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    private void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    private void onPlayerMove(PlayerMoveEvent event) {
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
    private void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
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
    private void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
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
    private void onEntityCombustByEntity(EntityCombustByEntityEvent event) {
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
    private void onProjectileCollide(ProjectileCollideEvent event) {
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
    private void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        final Player player = (Player) event.getEntity();
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            final SurvivalPlayer sp = getSurvivalPlayer(player);
            if (state != State.IDLE && sp != null && sp.getSpawnLocation() != null) {
                Bukkit.getScheduler().runTask(this, () -> {
                        player.setFallDistance(0);
                        player.teleport(sp.getSpawnLocation());
                        if (getSurvivalPlayer(player).isPlayer()) {
                            Players.reset(player);
                            player.setHealth(0);
                        }
                    });
            } else {
                Bukkit.getScheduler().runTask(this, () -> {
                        player.setFallDistance(0);
                        Spawn.warp(player);
                    });
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onPlayerItemDamage(PlayerItemDamageEvent event) {
        event.setCancelled(true);
    }

    public void startGame(String theWorldName) {
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
        this.worldName = theWorldName;
        loadWorld(this::startGameCallback);
    }

    private void startGameCallback() {
        log("spawnLocations:" + spawnLocations.size());
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
            final int teamCount = 2;
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
            Spawn.warp(player);
        }
        removeWorld();
        onStateChange(state, State.IDLE);
        state = State.IDLE;
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (state == State.IDLE) {
            if (saveTag.event) {
                eventSidebar(event);
            }
            return;
        }
        List<Component> lines = new ArrayList<>();
        SurvivalPlayer theSurvivalPlayer = getSurvivalPlayer(event.getPlayer());
        if (theSurvivalPlayer.didPlay) {
            if (theSurvivalPlayer.team != null) {
                lines.add(text(tiny("your team "), GRAY)
                          .append(theSurvivalPlayer.team.component));
                List<SurvivalPlayer> teamPlayers = getAlivePlayers(theSurvivalPlayer.team);
                Collections.sort(teamPlayers, (a, b) -> Integer.compare(b.kills, a.kills));
                for (SurvivalPlayer sp : teamPlayers) {
                    int hearts = (int) Math.round(sp.health);
                    lines.add(join(noSeparators(),
                                   text("" + sp.kills, DARK_RED),
                                   space(),
                                   text(hearts + "\u2764", RED),
                                   space(),
                                   text(sp.name, sp.isPlayer() ? theSurvivalPlayer.team.color : GRAY)));
                }
            }
        }
        if (!saveTag.useTeams && winnerName != null) {
            lines.add(text()
                      .append(text("Winner ", GRAY))
                      .append(text(winnerName, GOLD))
                      .build());
        } else if (saveTag.useTeams && winnerTeam != null) {
            lines.add(text()
                      .append(text("Winner ", GRAY))
                      .append(winnerTeam.component)
                      .build());
        }
        if (secondsLeft > 0) {
            long m = secondsLeft / 60;
            long s = secondsLeft % 60;
            lines.add(join(noSeparators(), text(tiny("time "), GRAY), text(m + "m " + s + "s", WHITE)));
        }
        if (saveTag.useTeams) {
            lines.add(join(noSeparators(), text(tiny("teams"), GRAY)));
            List<TeamScore> list = new ArrayList<>(teams.values());
            Collections.sort(list, (a, b) -> Integer.compare(b.alivePlayers, a.alivePlayers));
            for (TeamScore teamScore : list) {
                if (teamScore.alivePlayers == 0) {
                    lines.add(text("0\u2764 " + teamScore.team.displayName,
                                             DARK_GRAY));
                    continue;
                }
                Component teamDisplayName = null;
                if (teamScore.alivePlayers == 1) {
                    List<SurvivalPlayer> teamPlayers = getAlivePlayers(teamScore.team);
                    if (teamPlayers.size() >= 1) {
                        Player player = teamPlayers.get(0).getPlayer();
                        if (player != null) {
                            teamDisplayName = player.displayName().color(teamScore.team.color);
                        }
                    }
                }
                if (teamDisplayName == null) {
                    teamDisplayName = teamScore.team.component;
                }
                lines.add(join(noSeparators(),
                               text(teamScore.alivePlayers + "\u2764 ", RED),
                               text(teamScore.kills + " ", DARK_RED),
                               teamDisplayName));
            }
        } else {
            lines.add(join(noSeparators(), text(tiny("players"), GRAY)));
            List<SurvivalPlayer> list = new ArrayList<>(survivalPlayers.values());
            list.removeIf(i -> !i.isPlayer());
            Collections.sort(list, (b, a) -> Integer.compare(a.kills, b.kills));
            for (SurvivalPlayer sp : list) {
                int hearts = (int) Math.round(sp.health);
                Player player = sp.getPlayer();
                Component displayName = player != null
                    ? player.displayName()
                    : text(sp.getName(), WHITE);
                lines.add(join(noSeparators(),
                               text("" + sp.kills, DARK_RED),
                               space(),
                               text(hearts + "\u2764", RED),
                               space(),
                               displayName));
            }
        }
        if (!lines.isEmpty()) {
            event.sidebar(PlayerHudPriority.HIGHEST, lines);
        }
    }

    private void eventSidebar(PlayerHudEvent event) {
        List<Component> lines = new ArrayList<>(11);
        lines.add(join(noSeparators(), VanillaItems.GOLDEN_SWORD.component, TITLE, VanillaItems.BOW.component));
        lines.addAll(sidebarHighscore);
        event.sidebar(PlayerHudPriority.HIGHEST, lines);
    }

    @EventHandler
    private void onEntityTarget(EntityTargetEvent event) {
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

    @EventHandler
    void onPlayerTeam(PlayerTeamQuery query) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SurvivalPlayer sp = getSurvivalPlayer(player);
            if (sp == null || sp.team == null) continue;
            query.setTeam(player, sp.team.queryTeam);
        }
    }

    protected void computeHighscore() {
        this.highscore = Highscore.of(saveTag.kills);
        this.sidebarHighscore = Highscore.sidebar(highscore, TrophyCategory.MEDAL);
    }

    protected int rewardHighscore() {
        return Highscore.reward(saveTag.kills,
                                "survival_games",
                                TrophyCategory.MEDAL,
                                TITLE,
                                hi -> (saveTag.useTeams
                                       ? "Survival Teams with " + hi.score + " kill" + (hi.score == 1 ? "" : "s")
                                       : "Survival Games with " + hi.score + " kill" + (hi.score == 1 ? "" : "s")));
    }
}
