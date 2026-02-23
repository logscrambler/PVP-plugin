package org.mcmod.pVPpl.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class GameListener implements Listener {

    private final GameManager gameManager;
    private final JavaPlugin plugin;
    private final Random random = new Random();

    public GameListener(GameManager gameManager, JavaPlugin plugin) {
        this.gameManager = gameManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Update scoreboard for new player
        if (gameManager.getCurrentState() != GameManager.GameState.WAITING) {
            gameManager.updateScoreboard(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // No attack speed modification
    }
    
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (gameManager.getCurrentState() != GameManager.GameState.WAITING) {
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        // Record mining data
        gameManager.getMiningMonitor().recordMine(player, blockType);

        if (gameManager.getCurrentState() == GameManager.GameState.RESOURCE) {
            // Auto-smelt ores
            if (blockType == Material.IRON_ORE || blockType == Material.DEEPSLATE_IRON_ORE) {
                event.setDropItems(false);
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.IRON_INGOT));
            } else if (blockType == Material.GOLD_ORE || blockType == Material.DEEPSLATE_GOLD_ORE) {
                event.setDropItems(false);
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.GOLD_INGOT));
            } else if (blockType == Material.COPPER_ORE || blockType == Material.DEEPSLATE_COPPER_ORE) {
                event.setDropItems(false);
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.COPPER_INGOT));
            }

            // Leaves apple drop (Any tool, configurable chance)
            if (blockType.toString().contains("LEAVES")) {
                double chance = plugin.getConfig().getDouble("drops.apple-chance", 0.03); // Default 3%

                if (random.nextDouble() < chance) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.APPLE));
                }
            }
        } else if (gameManager.getCurrentState() == GameManager.GameState.KILL_TIME) {
            // Prevent building/breaking below Y=30
            if (event.getBlock().getY() < 30) {
                event.setCancelled(true);
                player.sendMessage("킬타임 시간에는 Y좌표 30 아래에서 블록을 파괴할 수 없습니다.");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getCurrentState() == GameManager.GameState.KILL_TIME) {
            if (event.getBlock().getY() < 30) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("킬타임 시간에는 Y좌표 30 아래에서 블록을 설치할 수 없습니다.");
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (gameManager.getCurrentState() == GameManager.GameState.RESOURCE) {
                event.setCancelled(true);
            }
            // KILL_TIME 상태에서는 모든 데미지를 정상적으로 받음 (자기장 포함)
        }
    }

    @EventHandler
    public void onPVP(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            if (gameManager.getCurrentState() == GameManager.GameState.RESOURCE) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameManager.getCurrentState() == GameManager.GameState.KILL_TIME) {
            Player victim = event.getEntity();
            // Check remaining players
            List<Player> alivePlayers = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getGameMode() == GameMode.SURVIVAL && !p.isDead() && !p.getUniqueId().equals(victim.getUniqueId()))
                    .collect(Collectors.toList());
            
            if (alivePlayers.size() == 1) {
                gameManager.endGame(alivePlayers.get(0));
            } else if (alivePlayers.isEmpty()) {
                gameManager.endGame(null); // Draw or everyone died
            }
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Component title = event.getView().title();
        
        if (title.equals(Component.text("PVP 설정", net.kyori.adventure.text.format.NamedTextColor.DARK_BLUE))) {
            handlePvpConfigClick(event);
        } else if (title.equals(Component.text("탭 리스트 설정", net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN))) {
            handleTabConfigClick(event);
        }
    }

    private void handlePvpConfigClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        FileConfiguration config = plugin.getConfig();
        
        if (slot == 10) { // 자원 시간
            int current = config.getInt("game.resource-time");
            int change = event.isShiftClick() ? 300 : 60;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("game.resource-time", Math.max(60, current + change));
        } else if (slot == 11) { // 초기 자기장 크기
            double current = config.getDouble("border.initial-size");
            double change = 100.0;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("border.initial-size", Math.max(100.0, current + change));
        } else if (slot == 12) { // 자기장 축소 주기
            int current = config.getInt("border.shrink-interval");
            int change = 30;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("border.shrink-interval", Math.max(30, current + change));
        } else if (slot == 13) { // 자기장 축소 양
            double current = config.getDouble("border.shrink-amount");
            double change = 50.0;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("border.shrink-amount", Math.max(10.0, current + change));
        } else if (slot == 14) { // 자기장 데미지
            double current = config.getDouble("border.damage-amount");
            double change = 0.5;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("border.damage-amount", Math.max(0.5, current + change));
        } else if (slot == 15) { // 사과 드롭 확률
            double current = config.getDouble("drops.apple-chance");
            double change = 0.01;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("drops.apple-chance", Math.max(0.0, Math.min(1.0, current + change)));
        } else {
            return;
        }
        
        plugin.saveConfig();
        gameManager.reloadConfig();
        gameManager.getConfigGUI().openGUI(player); // Refresh GUI
    }

    private void handleTabConfigClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        
        if (slot == 2) { // 탭 리스트 헤더
            player.closeInventory();
            player.sendMessage("§a채팅창에 탭 리스트 헤더 텍스트를 입력하세요. (자동으로 그라데이션 적용)");
            gameManager.addEditingPlayer(player.getUniqueId(), "tablist.header");
        } else if (slot == 6) { // 탭 리스트 푸터
            player.closeInventory();
            player.sendMessage("§a채팅창에 탭 리스트 푸터 텍스트를 입력하세요. (자동으로 그라데이션 적용)");
            gameManager.addEditingPlayer(player.getUniqueId(), "tablist.footer");
        }
    }
    
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String path = gameManager.getEditingPath(player.getUniqueId());
        
        if (path != null) {
            event.setCancelled(true);
            String input = event.getMessage();
            
            // Apply gradient if it's tablist config
            if (path.startsWith("tablist")) {
                input = "<gradient:white:aqua>" + input + "</gradient>";
            }
            
            String finalInput = input;
            
            // Run on main thread because config saving is not thread-safe usually, and we want to reopen GUI
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getConfig().set(path, finalInput);
                plugin.saveConfig();
                gameManager.reloadConfig();
                gameManager.removeEditingPlayer(player.getUniqueId());
                player.sendMessage("§a설정이 변경되었습니다.");
                
                if (path.startsWith("tablist")) {
                    gameManager.getConfigGUI().openTabGUI(player);
                } else {
                    gameManager.getConfigGUI().openGUI(player);
                }
            });
        }
    }
}
