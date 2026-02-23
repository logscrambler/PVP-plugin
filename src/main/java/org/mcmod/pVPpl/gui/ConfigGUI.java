package org.mcmod.pVPpl.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcmod.pVPpl.game.GameManager;

import java.util.Arrays;
import java.util.List;

public class ConfigGUI {

    private final JavaPlugin plugin;
    private final GameManager gameManager;

    public ConfigGUI(JavaPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("PVP 설정", NamedTextColor.DARK_BLUE));
        FileConfiguration config = plugin.getConfig();

        // 1. 자원 시간
        gui.setItem(10, createItem(Material.CLOCK, "자원 시간", 
                Arrays.asList("§7현재: §e" + config.getInt("game.resource-time") + "초", 
                        "§a좌클릭: +60초", "§c우클릭: -60초", "§eShift+클릭: +/- 300초")));

        // 2. 초기 자기장 크기
        gui.setItem(11, createItem(Material.MAP, "초기 자기장 크기", 
                Arrays.asList("§7현재: §e" + config.getDouble("border.initial-size"), 
                        "§a좌클릭: +100", "§c우클릭: -100")));

        // 3. 자기장 축소 주기
        gui.setItem(12, createItem(Material.COMPASS, "자기장 축소 주기", 
                Arrays.asList("§7현재: §e" + config.getInt("border.shrink-interval") + "초", 
                        "§a좌클릭: +30초", "§c우클릭: -30초")));

        // 4. 자기장 축소 양
        gui.setItem(13, createItem(Material.FILLED_MAP, "자기장 축소 양", 
                Arrays.asList("§7현재: §e" + config.getDouble("border.shrink-amount"), 
                        "§a좌클릭: +50", "§c우클릭: -50")));

        // 5. 자기장 데미지
        gui.setItem(14, createItem(Material.IRON_SWORD, "자기장 데미지", 
                Arrays.asList("§7현재: §e" + config.getDouble("border.damage-amount"), 
                        "§a좌클릭: +0.5", "§c우클릭: -0.5")));

        // 6. 사과 드롭 확률
        gui.setItem(15, createItem(Material.APPLE, "사과 드롭 확률", 
                Arrays.asList("§7현재: §e" + (config.getDouble("drops.apple-chance") * 100) + "%", 
                        "§a좌클릭: +1%", "§c우클릭: -1%")));

        player.openInventory(gui);
    }

    public void openTabGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, Component.text("탭 리스트 설정", NamedTextColor.DARK_GREEN));
        FileConfiguration config = plugin.getConfig();
        MiniMessage mm = MiniMessage.miniMessage();

        // 1. 탭 리스트 헤더
        String headerRaw = config.getString("tablist.header", "");
        // Strip tags for display if possible, or just show raw
        // Showing raw is better so they know what's there, but we will overwrite it with gradient
        
        gui.setItem(2, createItem(Material.NAME_TAG, "탭 리스트 헤더 설정", 
                Arrays.asList("§7현재 설정값(Raw):", "§f" + headerRaw, 
                        "§e클릭하여 텍스트 입력", "§b(자동으로 흰색->하늘색 그라데이션 적용)")));

        // 2. 탭 리스트 푸터
        String footerRaw = config.getString("tablist.footer", "");
        
        gui.setItem(6, createItem(Material.OAK_SIGN, "탭 리스트 푸터 설정", 
                Arrays.asList("§7현재 설정값(Raw):", "§f" + footerRaw, 
                        "§e클릭하여 텍스트 입력", "§b(자동으로 흰색->하늘색 그라데이션 적용)")));

        player.openInventory(gui);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
