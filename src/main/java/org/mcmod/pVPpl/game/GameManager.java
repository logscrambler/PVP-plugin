package org.mcmod.pVPpl.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import org.mcmod.pVPpl.gui.ConfigGUI;
import org.mcmod.pVPpl.monitor.MiningMonitor;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class GameManager {

    private final JavaPlugin plugin;
    private final MiningMonitor miningMonitor;
    private final GameHistoryManager historyManager;
    private final ConfigGUI configGUI;
    private GameState currentState = GameState.WAITING;
    private int gameTime = 0;
    private int resourceTimeLeft;
    
    // Config variables
    private int configResourceTime;
    private double configInitialBorderSize;
    private double configFinalBorderSize;
    private int configShrinkDuration;
    private double configDamageAmount;
    private double configDamageBuffer;
    private String configTabFooter; // Header is now fixed
    private int configYMinLimit;
    private int configYMaxLimit;
    private PVPGameMode configGameMode;
    private int configTeamSize;
    private String configTeamAssignment;
    private boolean configAutoSmelt;
    
    // Players editing config via chat
    private final Map<UUID, String> editingPlayers = new HashMap<>();
    
    // Top command usage
    private final Set<UUID> usedTopCommand = new HashSet<>();
    
    // Team data
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();

    public GameManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miningMonitor = new MiningMonitor();
        this.historyManager = new GameHistoryManager(plugin);
        this.configGUI = new ConfigGUI(plugin, this);
        loadConfig();
        // Register Listener
        plugin.getServer().getPluginManager().registerEvents(new GameListener(this, plugin), plugin);
        startLoop();
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }
    
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        configResourceTime = config.getInt("game.resource-time", 1800);
        configAutoSmelt = config.getBoolean("game.auto-smelt", true);
        configInitialBorderSize = config.getDouble("border.initial-size", 1000.0);
        configFinalBorderSize = config.getDouble("border.final-size", 1.0);
        configShrinkDuration = config.getInt("border.shrink-duration", 20) * 60; // minutes to seconds
        configDamageAmount = config.getDouble("border.damage-amount", 1.0);
        configDamageBuffer = config.getDouble("border.damage-buffer", 0.0);
        configTabFooter = config.getString("tablist.footer", "<gradient:white:aqua>spoiler.mcv.kr</gradient>");
        configYMinLimit = config.getInt("kill-time.y-min-limit", 30);
        configYMaxLimit = config.getInt("kill-time.y-max-limit", 150);
        
        String mode = config.getString("game.mode", "solo");
        configGameMode = "team".equalsIgnoreCase(mode) ? PVPGameMode.TEAM : PVPGameMode.SOLO;
        configTeamSize = Math.max(2, config.getInt("team.size", 2));
        configTeamAssignment = config.getString("team.assignment", "random");
    }

    public void startGame() {
        if (currentState != GameState.WAITING) {
            return;
        }
        // Reload config on start to apply changes
        reloadConfig();
        
        currentState = GameState.RESOURCE;
        gameTime = 0;
        resourceTimeLeft = configResourceTime;
        
        // Reset mining monitor & top command usage
        miningMonitor.reset();
        usedTopCommand.clear();
        
        // Clear teams only if random assignment or solo mode
        if (configGameMode == PVPGameMode.SOLO || configTeamAssignment.equalsIgnoreCase("random")) {
            playerTeams.clear();
            teams.clear();
        }
        
        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(configInitialBorderSize);
            world.getWorldBorder().setDamageAmount(0); // 자원 시간에는 데미지 없음
            world.getWorldBorder().setDamageBuffer(configDamageBuffer);
            
            // Set spawn to 0, 0 and time to day
            world.setSpawnLocation(0, world.getHighestBlockYAt(0, 0) + 1, 0);
            world.setTime(6000);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }
        
        // Assign teams if team mode and random assignment
        if (configGameMode == PVPGameMode.TEAM && configTeamAssignment.equalsIgnoreCase("random")) {
            assignTeamsRandomly();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(ChatColor.GREEN + "게임이 시작되었습니다! 자원 시간입니다.");
            player.setHealth(20);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            
            // Reset kills
            player.setStatistic(Statistic.PLAYER_KILLS, 0);
            
            // Remove OP
            if (player.isOp()) {
                player.setOp(false);
                player.sendMessage(ChatColor.RED + "게임 시작으로 인해 OP 권한이 제거되었습니다.");
            }
            
            // Give starter items
            player.setLevel(10); // Changed to 10
            player.getInventory().addItem(new ItemStack(Material.BREAD, 16));
            player.getInventory().addItem(new ItemStack(Material.ENCHANTING_TABLE));
            player.getInventory().addItem(new ItemStack(Material.BOOKSHELF, 64));
            player.getInventory().addItem(new ItemStack(Material.LAPIS_LAZULI, 64));
            
            // Teleport to random location within initial world border
            if (world != null) {
                teleportPlayerRandomlyInBorder(player, world, configInitialBorderSize);
            }
            
            if (configGameMode == PVPGameMode.TEAM) {
                String teamName = playerTeams.get(player.getUniqueId());
                if (teamName != null) {
                    player.sendMessage(ChatColor.AQUA + "당신은 " + teamName + "팀입니다.");
                } else {
                    player.sendMessage(ChatColor.GRAY + "당신은 팀이 없습니다 (깍두기).");
                }
            } else {
                player.sendMessage("Solo game started.");
            }
        }
    }
    
    private void teleportPlayerRandomlyInBorder(Player player, World world, double borderSize) {
        Random random = new Random();
        double halfSize = borderSize / 2.0;
        
        int attempts = 0;
        while (attempts < 100) { // Try up to 100 times to find a safe spot
            double x = (random.nextDouble() * borderSize) - halfSize;
            double z = (random.nextDouble() * borderSize) - halfSize;
            
            // Ensure chunk is loaded before getting highest block
            int chunkX = (int) x >> 4;
            int chunkZ = (int) z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true); // Load chunk synchronously
            }

            int y = world.getHighestBlockYAt((int) x, (int) z);
            Location spawnLoc = new Location(world, x + 0.5, y + 1, z + 0.5); // +0.5 for center of block, +1 to be above ground
            
            // Check if the spawn location is safe (not inside a block, not in lava/water)
            if (spawnLoc.getBlock().getType().isSolid() || spawnLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                attempts++;
                continue; // Try again if not safe
            }
            
            player.teleport(spawnLoc);
            return;
        }
        // If after 100 attempts, no safe spot found, teleport to world spawn
        player.teleport(world.getSpawnLocation());
        player.sendMessage(ChatColor.RED + "안전한 스폰 지점을 찾지 못해 월드 스폰으로 이동되었습니다.");
    }
    
    private void assignTeamsRandomly() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        
        int teamId = 1;
        int indexInTeam = 0;
        Team currentTeam = null;
        
        for (Player player : players) {
            if (indexInTeam == 0) {
                String name = "Team" + teamId;
                currentTeam = new Team(name, player.getUniqueId());
                teams.put(name, currentTeam);
                teamId++;
            } else {
                currentTeam.addMember(player.getUniqueId());
            }
            
            playerTeams.put(player.getUniqueId(), currentTeam.getName());
            indexInTeam++;
            
            if (indexInTeam >= configTeamSize) {
                indexInTeam = 0;
            }
        }
    }
    
    public boolean createTeam(Player player, String teamName) {
        if (playerTeams.containsKey(player.getUniqueId())) return false; // Already in a team
        if (teams.containsKey(teamName)) return false; // Team name exists
        
        Team team = new Team(teamName, player.getUniqueId());
        teams.put(teamName, team);
        playerTeams.put(player.getUniqueId(), teamName);
        return true;
    }
    
    public boolean deleteTeam(String teamName) {
        Team team = teams.remove(teamName);
        if (team != null) {
            for (UUID member : team.getMembers()) {
                playerTeams.remove(member);
                Player p = Bukkit.getPlayer(member);
                if (p != null) p.sendMessage(ChatColor.RED + "팀이 해체되었습니다.");
            }
            return true;
        }
        return false;
    }
    
    public boolean inviteToTeam(Player leader, Player target) {
        String teamName = playerTeams.get(leader.getUniqueId());
        if (teamName == null) return false;
        
        Team team = teams.get(teamName);
        if (!team.getLeader().equals(leader.getUniqueId())) return false; // Not leader
        if (team.getSize() >= configTeamSize) return false; // Team full
        if (playerTeams.containsKey(target.getUniqueId())) return false; // Target already in team
        
        team.invite(target.getUniqueId());
        return true;
    }
    
    public boolean acceptInvite(Player player) {
        if (playerTeams.containsKey(player.getUniqueId())) return false;
        
        for (Team team : teams.values()) {
            if (team.isInvited(player.getUniqueId())) {
                if (team.getSize() >= configTeamSize) return false;
                
                team.addMember(player.getUniqueId());
                playerTeams.put(player.getUniqueId(), team.getName());
                return true;
            }
        }
        return false;
    }
    
    public boolean leaveTeam(Player player) {
        String teamName = playerTeams.remove(player.getUniqueId());
        if (teamName == null) return false;
        
        Team team = teams.get(teamName);
        team.removeMember(player.getUniqueId());
        
        if (team.getSize() == 0) {
            teams.remove(teamName);
        } else if (team.getLeader().equals(player.getUniqueId())) {
            // Assign new leader
            team.setLeader(team.getMembers().iterator().next());
            Player newLeader = Bukkit.getPlayer(team.getLeader());
            if (newLeader != null) newLeader.sendMessage(ChatColor.GREEN + "당신이 새로운 팀장이 되었습니다.");
        }
        return true;
    }
    
    public boolean promoteLeader(Player leader, Player target) {
        String teamName = playerTeams.get(leader.getUniqueId());
        if (teamName == null) return false;
        
        Team team = teams.get(teamName);
        if (!team.getLeader().equals(leader.getUniqueId())) return false;
        if (!team.getMembers().contains(target.getUniqueId())) return false;
        
        team.setLeader(target.getUniqueId());
        return true;
    }
    
    public Map<String, List<String>> getTeamList() {
        Map<String, List<String>> list = new HashMap<>();
        for (Team team : teams.values()) {
            List<String> members = new ArrayList<>();
            for (UUID uuid : team.getMembers()) {
                Player p = Bukkit.getPlayer(uuid);
                members.add(p != null ? p.getName() : "Unknown");
            }
            list.put(team.getName(), members);
        }
        return list;
    }

    public void stopGame() {
        if (currentState == GameState.WAITING) {
            return;
        }
        currentState = GameState.WAITING;
        // Do not clear teams if manual mode, so they persist for next game unless changed
        if (configGameMode == PVPGameMode.SOLO || configTeamAssignment.equalsIgnoreCase("random")) {
            playerTeams.clear();
            teams.clear();
        }
        
        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            world.getWorldBorder().setSize(configInitialBorderSize);
            world.getWorldBorder().setDamageAmount(0);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(ChatColor.RED + "게임이 강제 종료되었습니다.");
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        }
    }
    
    public void endGame(Player winner) {
        String winnerName = winner == null ? "none" : winner.getName();
        endGameByWinnerName(winnerName);
    }
    
    public void endGameByWinnerName(String winnerName) {
        if (currentState == GameState.WAITING) return;
        
        currentState = GameState.END;
        Bukkit.broadcast(Component.text(ChatColor.GOLD + "게임 종료! 승자: " + winnerName));
        
        // Record history
        int totalKills = 0; // This should be calculated properly if needed
        historyManager.recordGame(winnerName, gameTime, totalKills);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                stopGame();
            }
        }.runTaskLater(plugin, 200L); // 10 seconds delay before reset
    }
    
    public boolean useTopCommand(Player player) {
        if (currentState != GameState.RESOURCE && currentState != GameState.KILL_TIME) {
            player.sendMessage(ChatColor.RED + "게임 진행 중에만 사용할 수 있습니다.");
            return false;
        }
        
        if (usedTopCommand.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "이미 /top 명령어를 사용했습니다.");
            return false;
        }
        
        World world = player.getWorld();
        Location loc = player.getLocation();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                player.sendMessage(ChatColor.RED + "청크가 로드되지 않아 이동할 수 없습니다. 잠시 후 다시 시도해주세요.");
                return;
            }
            Location surface = world.getHighestBlockAt(loc).getLocation().add(0, 1, 0);
            player.teleport(surface);
            player.sendMessage(ChatColor.GREEN + "지상으로 이동했습니다.");
            usedTopCommand.add(player.getUniqueId());
        });
        
        return true;
    }

    private void startLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentState == GameState.WAITING) return;

                gameTime++;
                updateScoreboard();
                updateTabList();

                if (currentState == GameState.RESOURCE) {
                    resourceTimeLeft--;
                    applyResourceEffects();
                    if (resourceTimeLeft <= 0) {
                        startKillTime();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyResourceEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 30, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 30, 0, false, false));
        }
    }

    private void startKillTime() {
        currentState = GameState.KILL_TIME;

        Title title = Title.title(
                Component.text(ChatColor.RED + "킬타임 시작"),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1000), Duration.ofMillis(200))
        );
        
        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            world.getWorldBorder().setDamageAmount(configDamageAmount);
            world.getWorldBorder().setSize(configFinalBorderSize, configShrinkDuration);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            if (player.getLocation().getY() < 62) { // Changed to 62 (Sea level)
                Location surface = world.getHighestBlockAt(player.getLocation()).getLocation().add(0, 2, 0); // 2칸 위로
                player.teleport(surface);
                player.sendMessage(ChatColor.YELLOW + "지하에 있어 지상으로 이동되었습니다.");
            }
        }
    }
    
    public void instantShrinkBorder(Player sender) {
        if (currentState != GameState.KILL_TIME) {
            sender.sendMessage(ChatColor.RED + "킬타임 중에만 자기장을 즉시 축소할 수 있습니다.");
            return;
        }

        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            world.getWorldBorder().setSize(configFinalBorderSize, 1L); // Shrink instantly (1 tick)
            Bukkit.broadcast(Component.text(ChatColor.RED + "관리자에 의해 자기장이 최종 크기까지 즉시 축소됩니다!"));
        }
    }

    private void updateScoreboard() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }
    
    public void updateScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("pvp", Criteria.DUMMY, Component.text("§6§l시참 PVP"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = 15;
        obj.getScore("§e>----------------<").setScore(score--);
        obj.getScore("§f게임시간 §7> §a" + formatTime(gameTime)).setScore(score--);
        
        if (currentState == GameState.RESOURCE) {
            obj.getScore("§f남은 자원시간 §7> §b" + formatTime(resourceTimeLeft)).setScore(score--);
        } else {
            World world = Bukkit.getWorlds().get(0);
            if (world != null) {
                double remainingSize = world.getWorldBorder().getSize() - configFinalBorderSize;
                double totalShrink = configInitialBorderSize - configFinalBorderSize;
                if (totalShrink > 0) {
                    double remainingTime = (remainingSize / totalShrink) * configShrinkDuration;
                    obj.getScore("§f자기장 축소 §7> §c" + formatTime((int)remainingTime)).setScore(score--);
                } else {
                    obj.getScore("§f자기장 축소 §7> §c" + formatTime(0)).setScore(score--); // Already shrunk
                }
            }
        }
        
        obj.getScore("§1 ").setScore(score--);
        obj.getScore("§f현재 킬 §7> §c" + player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS)).setScore(score--);
        obj.getScore("§f남은 플레이어 §7> §e" + Bukkit.getOnlinePlayers().size()).setScore(score--);
        obj.getScore("§2  ").setScore(score--);
        
        World world = Bukkit.getWorlds().get(0);
        double size = world != null ? world.getWorldBorder().getSize() : configInitialBorderSize;
        obj.getScore("§f자기장 크기 §7> §d" + (int)size).setScore(score--);
        obj.getScore("§e>----------------< ").setScore(score--);
        
        obj.getScore("§f본인의 Y 좌표 : §a" + player.getLocation().getBlockY()).setScore(score--);
        
        if (configGameMode == PVPGameMode.TEAM) {
            String teamName = playerTeams.get(player.getUniqueId());
            String teamStr = teamName != null ? teamName : "없음";
            obj.getScore("§f팀 : §b" + teamStr).setScore(score--);
        }

        player.setScoreboard(board);
    }
    
    private void updateTabList() {
        MiniMessage mm = MiniMessage.miniMessage();
        Component header = mm.deserialize(configTabHeader);
        Component footer = mm.deserialize(configTabFooter);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
    
    public void handleDeathCheck(Player victim) {
        if (currentState != GameState.KILL_TIME) return;
        
        List<Player> alivePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() == org.bukkit.GameMode.SURVIVAL && !p.isDead() && !p.getUniqueId().equals(victim.getUniqueId()))
                .collect(Collectors.toList());
        
        if (configGameMode == PVPGameMode.SOLO) {
            if (alivePlayers.size() == 1) {
                endGame(alivePlayers.get(0));
            } else if (alivePlayers.isEmpty()) {
                endGameByWinnerName("none");
            }
        } else {
            // Team mode check
            Set<String> aliveTeams = new HashSet<>();
            for (Player p : alivePlayers) {
                String teamName = playerTeams.get(p.getUniqueId());
                if (teamName != null) aliveTeams.add(teamName);
            }
            
            if (aliveTeams.size() == 1) {
                String winningTeam = aliveTeams.iterator().next();
                endGameByWinnerName("Team " + winningTeam);
            } else if (aliveTeams.isEmpty()) {
                endGameByWinnerName("none");
            }
        }
    }
    
    public boolean isSameTeam(Player p1, Player p2) {
        if (configGameMode != PVPGameMode.TEAM) return false;
        
        String t1 = playerTeams.get(p1.getUniqueId());
        String t2 = playerTeams.get(p2.getUniqueId());
        
        return t1 != null && t2 != null && t1.equals(t2);
    }

    public GameState getCurrentState() {
        return currentState;
    }
    
    public MiningMonitor getMiningMonitor() {
        return miningMonitor;
    }
    
    public GameHistoryManager getHistoryManager() {
        return historyManager;
    }
    
    public ConfigGUI getConfigGUI() {
        return configGUI;
    }
    
    public void addEditingPlayer(UUID uuid, String configPath) {
        editingPlayers.put(uuid, configPath);
    }
    
    public String getEditingPath(UUID uuid) {
        return editingPlayers.get(uuid);
    }
    
    public void removeEditingPlayer(UUID uuid) {
        editingPlayers.remove(uuid);
    }
    
    public int getYMinLimit() {
        return configYMinLimit;
    }
    
    public int getYMaxLimit() {
        return configYMaxLimit;
    }
    
    public void setGameMode(String mode) {
        if ("team".equalsIgnoreCase(mode)) {
            plugin.getConfig().set("game.mode", "team");
            configGameMode = PVPGameMode.TEAM;
        } else {
            plugin.getConfig().set("game.mode", "solo");
            configGameMode = PVPGameMode.SOLO;
        }
        plugin.saveConfig();
        reloadConfig();
    }
    
    public PVPGameMode getGameMode() {
        return configGameMode;
    }
    
    public void setTeamSize(int size) {
        configTeamSize = Math.max(2, size);
        plugin.getConfig().set("team.size", configTeamSize);
        plugin.saveConfig();
    }
    
    public int getTeamSize() {
        return configTeamSize;
    }
    
    public void setTeamAssignment(String assignment) {
        if ("manual".equalsIgnoreCase(assignment)) {
            plugin.getConfig().set("team.assignment", "manual");
            configTeamAssignment = "manual";
        } else {
            plugin.getConfig().set("team.assignment", "random");
            configTeamAssignment = "random";
        }
        plugin.saveConfig();
    }
    
    public String getTeamAssignment() {
        return configTeamAssignment;
    }

    public boolean isAutoSmeltEnabled() {
        return configAutoSmelt;
    }

    public enum GameState {
        WAITING, RESOURCE, KILL_TIME, END
    }
    
    public enum PVPGameMode {
        SOLO, TEAM
    }
}
