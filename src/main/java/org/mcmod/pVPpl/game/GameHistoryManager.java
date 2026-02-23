package org.mcmod.pVPpl.game;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GameHistoryManager {

    private final JavaPlugin plugin;
    private final File historyFile;
    private final FileConfiguration historyConfig;

    public GameHistoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.historyFile = new File(plugin.getDataFolder(), "history.yml");
        if (!historyFile.exists()) {
            try {
                historyFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create history.yml!");
            }
        }
        this.historyConfig = YamlConfiguration.loadConfiguration(historyFile);
    }

    public void recordGame(String winnerName, int durationSeconds, int totalKills) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String key = "games." + System.currentTimeMillis();
        
        historyConfig.set(key + ".timestamp", timestamp);
        historyConfig.set(key + ".winner", winnerName);
        historyConfig.set(key + ".duration", durationSeconds);
        historyConfig.set(key + ".totalKills", totalKills);
        
        saveHistory();
    }

    public List<String> getRecentHistory(int limit) {
        List<String> history = new ArrayList<>();
        if (historyConfig.getConfigurationSection("games") == null) return history;

        List<String> keys = new ArrayList<>(historyConfig.getConfigurationSection("games").getKeys(false));
        // Sort by timestamp descending (keys are timestamps)
        keys.sort((a, b) -> Long.compare(Long.parseLong(b), Long.parseLong(a)));

        for (int i = 0; i < Math.min(keys.size(), limit); i++) {
            String key = "games." + keys.get(i);
            String timestamp = historyConfig.getString(key + ".timestamp");
            String winner = historyConfig.getString(key + ".winner");
            int duration = historyConfig.getInt(key + ".duration");
            int kills = historyConfig.getInt(key + ".totalKills");
            
            history.add(String.format("§7[%s] §6승자: %s §f(시간: %dm %ds, 총 킬: %d)", 
                    timestamp, winner, duration / 60, duration % 60, kills));
        }
        return history;
    }

    private void saveHistory() {
        try {
            historyConfig.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save history.yml!");
        }
    }
}
