package org.mcmod.pVPpl.monitor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MiningMonitor {

    private final Map<UUID, PlayerMiningData> playerData = new HashMap<>();
    private static final double SUSPICIOUS_RATIO = 0.5; // 50% 이상이면 의심 (돌 2개당 다이아 1개)
    private static final int MIN_STONE_MINED = 20; // 최소 20개 돌을 캤을 때부터 체크

    public void recordMine(Player player, Material blockType) {
        PlayerMiningData data = playerData.computeIfAbsent(player.getUniqueId(), k -> new PlayerMiningData());
        
        if (blockType == Material.STONE || blockType == Material.DEEPSLATE || blockType == Material.COBBLESTONE || blockType == Material.COBBLED_DEEPSLATE) {
            data.stoneMined++;
        } else if (blockType == Material.DIAMOND_ORE || blockType == Material.DEEPSLATE_DIAMOND_ORE) {
            data.diamondMined++;
            data.diamondLogs.add(new DiamondLog(LocalDateTime.now(), player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()));
            checkSuspicious(player, data);
        } else if (isOre(blockType)) {
            data.otherOresMined++;
        }
    }
    
    public void reset() {
        playerData.clear();
    }

    private boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE");
    }

    private void checkSuspicious(Player player, PlayerMiningData data) {
        if (data.stoneMined < MIN_STONE_MINED) return;

        double ratio = (double) data.diamondMined / data.stoneMined;
        if (ratio > SUSPICIOUS_RATIO) {
            String msg = String.format("§c[핵의심] %s님의 다이아몬드 비율이 %.2f%%입니다! (돌: %d, 다이아: %d)", 
                    player.getName(), ratio * 100, data.stoneMined, data.diamondMined);
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isOp()) {
                    p.sendMessage(msg);
                }
            }
        }
    }

    public void showInfo(Player admin, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage("§c플레이어를 찾을 수 없습니다.");
            return;
        }

        PlayerMiningData data = playerData.get(target.getUniqueId());
        if (data == null) {
            admin.sendMessage("§c해당 플레이어의 채굴 기록이 없습니다.");
            return;
        }

        double ratio = data.stoneMined > 0 ? (double) data.diamondMined / data.stoneMined * 100 : 0;

        admin.sendMessage("§6========== [ " + target.getName() + " 정보 ] ==========");
        admin.sendMessage(String.format("§f돌 채굴: §7%d개", data.stoneMined));
        admin.sendMessage(String.format("§b다이아몬드 채굴: §b%d개 (%.2f%%)", data.diamondMined, ratio));
        admin.sendMessage(String.format("§e기타 광물 채굴: §e%d개", data.otherOresMined));
        
        admin.sendMessage("§6[ 최근 다이아몬드 채굴 기록 ]");
        int count = 0;
        for (int i = data.diamondLogs.size() - 1; i >= 0; i--) {
            if (count++ >= 5) break; // 최근 5개만
            DiamondLog log = data.diamondLogs.get(i);
            admin.sendMessage(String.format("§7- %s (%d, %d, %d)", 
                    log.time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), log.x, log.y, log.z));
        }
        
        // 인벤토리 보여주기
        admin.openInventory(target.getInventory());
    }

    private static class PlayerMiningData {
        int stoneMined = 0;
        int diamondMined = 0;
        int otherOresMined = 0;
        List<DiamondLog> diamondLogs = new ArrayList<>();
    }

    private static class DiamondLog {
        LocalDateTime time;
        int x, y, z;

        public DiamondLog(LocalDateTime time, int x, int y, int z) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
