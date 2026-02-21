package org.mcmod.pVPpl;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcmod.pVPpl.game.GameManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PVPpl extends JavaPlugin implements CommandExecutor, TabCompleter {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        this.gameManager = new GameManager(this);
        
        // Register command
        if (getCommand("pvp") != null) {
            getCommand("pvp").setExecutor(this);
            getCommand("pvp").setTabCompleter(this);
        } else {
            getLogger().severe("명령어를 찾을 수 없습니다. plugin.yml을 확인해주세요.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("PVP 플러그인이 켜졌습니다!");
    }

    @Override
    public void onDisable() {
        getLogger().info("PVP플러그인이 꺼집니다!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("pvp")) {
            if (!sender.hasPermission("pvppl.admin")) {
                sender.sendMessage("권한이 없습니다.");
                return true;
            }

            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "start":
                    if (gameManager.getCurrentState() != GameManager.GameState.WAITING) {
                        sender.sendMessage("이미 게임이 진행 중입니다.");
                    } else {
                        gameManager.startGame();
                        sender.sendMessage("게임을 시작합니다.");
                    }
                    break;
                case "stop":
                    if (gameManager.getCurrentState() == GameManager.GameState.WAITING) {
                        sender.sendMessage("진행 중인 게임이 없습니다.");
                    } else {
                        gameManager.stopGame();
                        sender.sendMessage("게임을 강제 종료했습니다.");
                    }
                    break;
                case "help":
                    sendHelp(sender);
                    break;
                default:
                    sender.sendMessage("알 수 없는 명령어입니다. /pvp help를 입력하세요.");
                    break;
            }
            return true;
        }
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== [ PVP 플러그인 도움말 ] ==========");
        sender.sendMessage("§e/pvp start §f- 게임을 시작합니다.");
        sender.sendMessage("§e/pvp stop §f- 진행 중인 게임을 강제 종료합니다.");
        sender.sendMessage("§e/pvp help §f- 도움말을 표시합니다.");
        sender.sendMessage("§6===========================================");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("pvp")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("start");
                completions.add("stop");
                completions.add("help");
                return completions;
            }
        }
        return null;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
