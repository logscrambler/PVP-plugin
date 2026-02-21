package org.mcmod.pVPpl.game;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class GameListener implements Listener {

    private final GameManager gameManager;
    private final Random random = new Random();

    public GameListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

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

            // Leaves apple drop
            if (blockType.toString().contains("LEAVES")) {
                double chance = 0.02; // 2%
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType() == Material.SHEARS) {
                    chance = 0.05; // 5%
                }

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
            Player player = (Player) event.getEntity();
            
            if (gameManager.getCurrentState() == GameManager.GameState.RESOURCE) {
                event.setCancelled(true);
            } else if (gameManager.getCurrentState() == GameManager.GameState.KILL_TIME) {
                // 자기장 데미지 처리 (최종적으로 1칸 남김)
                if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION || event.getCause() == EntityDamageEvent.DamageCause.WORLD_BORDER) {
                     // WorldBorder damage cause might be SUFFOCATION in some versions or custom
                     // But usually it's WORLD_BORDER in newer APIs. Let's check if it's outside border.
                     // Actually, Spigot API has DamageCause.WORLD_BORDER.
                }
                
                if (event.getCause() == EntityDamageEvent.DamageCause.WORLD_BORDER) {
                    if (player.getHealth() - event.getFinalDamage() < 2.0) {
                        event.setCancelled(true);
                        if (player.getHealth() > 2.0) {
                            player.setHealth(2.0);
                        }
                    }
                }
            }
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
}
