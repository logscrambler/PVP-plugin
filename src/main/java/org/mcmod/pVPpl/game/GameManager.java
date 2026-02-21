package org.mcmod.pVPpl.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.time.Duration;

@SuppressWarnings("deprecation")
public class GameManager {

    private final JavaPlugin plugin;
    private GameState currentState = GameState.WAITING;
    private int gameTime = 0;
    private int resourceTimeLeft = 20 * 60; // 20 minutes default
    private int killTimePhaseTime = 0; // Time since kill time started
    private int borderShrinkTimer = 3 * 60; // 3 minutes
    private final double initialBorderSize = 1000;

    public GameManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // Register Listener
        plugin.getServer().getPluginManager().registerEvents(new GameListener(this), plugin);
        startLoop();
    }

    public void startGame() {
        if (currentState != GameState.WAITING) {
            return;
        }
        currentState = GameState.RESOURCE;
        gameTime = 0;
        resourceTimeLeft = 1200; // 20 minutes
        
        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            world.getWorldBorder().setCenter(0, 0);
            world.getWorldBorder().setSize(initialBorderSize);
            world.getWorldBorder().setDamageAmount(1.0); // 0.5 hearts damage per second per block outside
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.GREEN + "게임이 시작되었습니다! 자원 시간입니다.");
            player.setHealth(20);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        }
    }

    public void stopGame() {
        if (currentState == GameState.WAITING) {
            return;
        }
        currentState = GameState.WAITING;
        
        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            world.getWorldBorder().setSize(initialBorderSize);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.RED + "게임이 강제 종료되었습니다.");
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        }
    }

    private void startLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentState == GameState.WAITING) return;

                gameTime++;
                updateScoreboard();

                if (currentState == GameState.RESOURCE) {
                    resourceTimeLeft--;
                    applyResourceEffects();
                    if (resourceTimeLeft <= 0) {
                        startKillTime();
                    }
                } else if (currentState == GameState.KILL_TIME) {
                    handleKillTimeLogic();
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
        killTimePhaseTime = 0;
        borderShrinkTimer = 3 * 60;

        Title title = Title.title(
                Component.text(ChatColor.RED + "킬타임 시작"),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1000), Duration.ofMillis(200))
        );
        
        World world = Bukkit.getWorlds().get(0);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            if (player.getLocation().getY() < 30) {
                Location surface = world.getHighestBlockAt(player.getLocation()).getLocation().add(0, 1, 0);
                player.teleport(surface);
                player.sendMessage(ChatColor.YELLOW + "지하에 있어 지상으로 이동되었습니다.");
            }
        }
    }

    private void handleKillTimeLogic() {
        killTimePhaseTime++;
        borderShrinkTimer--;

        if (borderShrinkTimer <= 0) {
            borderShrinkTimer = 3 * 60; // Reset for next shrink

            World world = Bukkit.getWorlds().get(0);
            if (world != null) {
                double currentSize = world.getWorldBorder().getSize();
                double newSize = currentSize - 100;
                if (newSize < 20) newSize = 20;

                if (currentSize > newSize) {
                    world.getWorldBorder().setSize(newSize, 60L); // Shrink over 60 seconds
                    Bukkit.broadcast(Component.text(ChatColor.RED + "자기장이 줄어듭니다!"));
                }
            }
        }
    }

    private void updateScoreboard() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("pvp", Criteria.DUMMY, Component.text("시참 PVP"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            obj.getScore(">----------------<").setScore(15);
            obj.getScore("게임시간 > " + formatTime(gameTime)).setScore(14);
            
            if (currentState == GameState.RESOURCE) {
                obj.getScore("남은 자원시간 > " + formatTime(resourceTimeLeft)).setScore(13);
            } else {
                obj.getScore("자기장 축소 > " + formatTime(borderShrinkTimer)).setScore(13);
            }
            
            obj.getScore(" ").setScore(12);
            obj.getScore("현재 킬 > " + player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS)).setScore(11);
            obj.getScore("남은 플레이어 > " + Bukkit.getOnlinePlayers().size()).setScore(10);
            obj.getScore("  ").setScore(9);
            
            World world = Bukkit.getWorlds().get(0);
            double size = world != null ? world.getWorldBorder().getSize() : initialBorderSize;
            obj.getScore("자기장 크기> " + (int)size).setScore(8);
            obj.getScore(">----------------< ").setScore(7);
            
            if (currentState == GameState.RESOURCE) {
                obj.getScore("(본인의 Y좌표 : " + player.getLocation().getBlockY() + " )").setScore(6);
            } else {
                Location loc = player.getLocation();
                obj.getScore("( " + loc.getBlockZ() + " , " + loc.getBlockY() + " , " + loc.getBlockX() + ")").setScore(6);
            }

            player.setScoreboard(board);
        }
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public enum GameState {
        WAITING, RESOURCE, KILL_TIME, END
    }
}
