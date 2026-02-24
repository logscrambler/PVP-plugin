package org.mcmod.pVPpl.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.event.player.PlayerPortalEvent;
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
        if (gameManager.getCurrentState() != GameManager.GameState.WAITING) {
            gameManager.updateScoreboard(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // No attack speed modification
    }
    
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (gameManager.getCurrentState() != GameManager.GameState.WAITING) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("게임 중에는 네더로 이동할 수 없습니다.", NamedTextColor.RED));
        }
    }
    
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (gameManager.getCurrentState() != GameManager.GameState.WAITING) {
            // Block all natural spawns and spawner spawns
            if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL || 
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        gameManager.getMiningMonitor().recordMine(player, blockType);

        if (gameManager.getCurrentState() == GameManager.GameState.RESOURCE) {
            if (blockType == Material.IRON_ORE || blockType == Material.DEEPSLATE_IRON_ORE) {
                event.setDropItems(false);
                handleFortune(player, event.getBlock().getLocation(), Material.IRON_INGOT);
            } else if (blockType == Material.GOLD_ORE || blockType == Material.DEEPSLATE_GOLD_ORE) {
                event.setDropItems(false);
                handleFortune(player, event.getBlock().getLocation(), Material.GOLD_INGOT);
            } else if (blockType == Material.COPPER_ORE || blockType == Material.DEEPSLATE_COPPER_ORE) {
                event.setDropItems(false);
                handleFortune(player, event.getBlock().getLocation(), Material.COPPER_INGOT);
            }

            if (blockType.toString().contains("LEAVES")) {
                event.setDropItems(false); // Prevent leaves from dropping
                double chance = plugin.getConfig().getDouble("drops.apple-chance", 0.03);
                if (chance > 0 && random.nextDouble() < chance) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.APPLE));
                }
            }
        } else if (gameManager.getCurrentState() == GameManager.GameState.KILL_TIME) {
            if (event.getBlock().getY() < gameManager.getYMinLimit()) {
                event.setCancelled(true);
                player.sendMessage("킬타임 시간에는 Y좌표 " + gameManager.getYMinLimit() + " 미만에서 블록을 파괴할 수 없습니다.");
            }
        }
    }
    
    private void handleFortune(Player player, org.bukkit.Location loc, Material drop) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        
        // Base drop
        loc.getWorld().dropItemNaturally(loc, new ItemStack(drop));
        
        // Fortune drop
        if (fortuneLevel > 0) {
            double chance = plugin.getConfig().getDouble("drops.fortune.level-" + fortuneLevel, 0.0);
            if (random.nextDouble() < chance) {
                loc.getWorld().dropItemNaturally(loc, new ItemStack(drop));
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameManager.getCurrentState() == GameManager.GameState.KILL_TIME) {
            if (event.getBlock().getY() > gameManager.getYMaxLimit()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("킬타임 시간에는 Y좌표 " + gameManager.getYMaxLimit() + " 초과에서 블록을 설치할 수 없습니다.");
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            if (gameManager.getCurrentState() == GameManager.GameState.RESOURCE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPVP(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            if (gameManager.getCurrentState() == GameManager.GameState.RESOURCE) {
                event.setCancelled(true);
            } else if (gameManager.isSameTeam((Player) event.getEntity(), (Player) event.getDamager())) {
                event.setCancelled(true);
                event.getDamager().sendMessage(Component.text("같은 팀은 공격할 수 없습니다.", NamedTextColor.RED));
            }
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null) {
            event.deathMessage(Component.text()
                    .append(Component.text(killer.getName(), NamedTextColor.RED))
                    .append(Component.text(" 님이 ", NamedTextColor.WHITE))
                    .append(Component.text(victim.getName(), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" 님을 영원히 잠들게 했습니다.", NamedTextColor.WHITE))
                    .build());
        }

        if (gameManager.getCurrentState() == GameManager.GameState.KILL_TIME) {
            gameManager.handleDeathCheck(victim);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Component title = event.getView().title();
        
        if (title.equals(Component.text("PVP 설정", NamedTextColor.DARK_BLUE))) {
            handlePvpConfigClick(event);
        } else if (title.equals(Component.text("탭 리스트 설정", NamedTextColor.DARK_GREEN))) {
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
        } else if (slot == 16) { // 게임 모드
            String current = config.getString("game.mode", "solo");
            String newMode = current.equalsIgnoreCase("solo") ? "team" : "solo";
            gameManager.setGameMode(newMode);
        } else if (slot == 17) { // Y좌표 파괴 제한 (Min)
            int current = config.getInt("kill-time.y-min-limit");
            int change = 10;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("kill-time.y-min-limit", Math.max(0, current + change));
        } else if (slot == 18) { // Y좌표 설치 제한 (Max)
            int current = config.getInt("kill-time.y-max-limit");
            int change = 10;
            if (event.getClick() == ClickType.RIGHT) change = -change;
            config.set("kill-time.y-max-limit", Math.max(0, current + change));
        } else if (slot == 24) { // 팀 인원수
            int current = config.getInt("team.size", 2);
            int change = 1;
            if (event.getClick() == ClickType.RIGHT) change = -1;
            gameManager.setTeamSize(Math.max(2, current + change));
        } else if (slot == 25) { // 팀 배정 방식
            String current = config.getString("team.assignment", "random");
            String newAssignment = current.equalsIgnoreCase("random") ? "manual" : "random";
            gameManager.setTeamAssignment(newAssignment);
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
