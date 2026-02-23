package org.mcmod.pVPpl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcmod.pVPpl.game.GameManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

public final class PVPpl extends JavaPlugin implements CommandExecutor, TabCompleter {

    private GameManager gameManager;
    private FileHandler fileHandler;

    @Override
    public void onEnable() {
        // Setup logger
        try {
            fileHandler = new FileHandler(getDataFolder() + "/logs.txt", true);
            fileHandler.setFormatter(new SimpleFormatter());
            getLogger().addHandler(fileHandler);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to setup log file handler", e);
        }
        
        // Save default config
        saveDefaultConfig();
        
        this.gameManager = new GameManager(this);
        
        // Register pvp command
        try {
            Objects.requireNonNull(getCommand("pvp"), "Command 'pvp' not found in plugin.yml")
                   .setExecutor(this);
            Objects.requireNonNull(getCommand("pvp"))
                   .setTabCompleter(this);
                   
            Objects.requireNonNull(getCommand("tab"), "Command 'tab' not found in plugin.yml")
                   .setExecutor(this);
            Objects.requireNonNull(getCommand("tab"))
                   .setTabCompleter(this);
        } catch (NullPointerException e) {
            getLogger().severe("Failed to register command: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("PVP Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (fileHandler != null) {
            fileHandler.close();
        }
        getLogger().info("PVP Plugin has been disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("pvp")) {
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            String subCommand = args[0].toLowerCase();
            
            // Player commands (no permission needed for team join/list if manual mode)
            if (subCommand.equals("team")) {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /pvp team <join|list>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("join")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Only players can join teams.");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /pvp team join <teamId>");
                        return true;
                    }
                    try {
                        int teamId = Integer.parseInt(args[2]);
                        if (gameManager.joinTeam((Player) sender, teamId)) {
                            sender.sendMessage("§aJoined team " + teamId);
                        } else {
                            sender.sendMessage("§cFailed to join team (Full or Random mode).");
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid team ID.");
                    }
                    return true;
                } else if (args[1].equalsIgnoreCase("list")) {
                    Map<Integer, List<String>> teams = gameManager.getTeamMembers();
                    sender.sendMessage("§6========== [ Team List ] ==========");
                    for (Map.Entry<Integer, List<String>> entry : teams.entrySet()) {
                        sender.sendMessage("§eTeam " + entry.getKey() + ": §f" + String.join(", ", entry.getValue()));
                    }
                    sender.sendMessage("§6===================================");
                    return true;
                }
            }

            // Admin commands
            if (!sender.hasPermission("pvppl.admin")) {
                sender.sendMessage("You do not have permission.");
                return true;
            }

            switch (subCommand) {
                case "start":
                    if (gameManager.getCurrentState() != GameManager.GameState.WAITING) {
                        sender.sendMessage("The game is already in progress.");
                    } else {
                        gameManager.startGame();
                        sender.sendMessage("The game has started.");
                    }
                    break;
                case "stop":
                    if (gameManager.getCurrentState() == GameManager.GameState.WAITING) {
                        sender.sendMessage("No game is currently in progress.");
                    } else {
                        gameManager.stopGame();
                        sender.sendMessage("The game has been stopped.");
                    }
                    break;
                case "config":
                    if (sender instanceof Player) {
                        gameManager.getConfigGUI().openGUI((Player) sender);
                    } else {
                        sender.sendMessage("This command can only be used by players.");
                    }
                    break;
                case "info":
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /pvp info <player>");
                    } else {
                        if (sender instanceof Player) {
                            gameManager.getMiningMonitor().showInfo((Player) sender, args[1]);
                        } else {
                            sender.sendMessage("This command can only be used by players.");
                        }
                    }
                    break;
                case "history":
                    List<String> history = gameManager.getHistoryManager().getRecentHistory(10);
                    if (history.isEmpty()) {
                        sender.sendMessage("§cNo game history found.");
                    } else {
                        sender.sendMessage("§6========== [ Recent Game History ] ==========");
                        for (String record : history) {
                            sender.sendMessage(record);
                        }
                        sender.sendMessage("§6===========================================");
                    }
                    break;
                case "help":
                    sendHelp(sender);
                    break;
                default:
                    sender.sendMessage("Unknown command. Use /pvp help for assistance.");
                    break;
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("tab")) {
            if (!sender.hasPermission("pvppl.admin")) {
                sender.sendMessage("You do not have permission.");
                return true;
            }
            
            if (args.length > 0 && args[0].equalsIgnoreCase("config")) {
                if (sender instanceof Player) {
                    gameManager.getConfigGUI().openTabGUI((Player) sender);
                } else {
                    sender.sendMessage("This command can only be used by players.");
                }
                return true;
            } else {
                sender.sendMessage("§cUsage: /tab config");
                return true;
            }
        }
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== [ PVP Plugin Help ] ==========");
        sender.sendMessage("§e/pvp start §f- Starts the game.");
        sender.sendMessage("§e/pvp stop §f- Stops the current game.");
        sender.sendMessage("§e/pvp config §f- Open configuration GUI.");
        sender.sendMessage("§e/pvp info <player> §f- Shows mining info and inventory.");
        sender.sendMessage("§e/pvp history §f- Shows recent game history.");
        sender.sendMessage("§e/pvp team <join|list> §f- Manage teams.");
        sender.sendMessage("§e/tab config §f- Open tab list configuration GUI.");
        sender.sendMessage("§e/pvp help §f- Shows this help message.");
        sender.sendMessage("§6========================================");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("pvp")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("start");
                completions.add("stop");
                completions.add("config");
                completions.add("info");
                completions.add("history");
                completions.add("team");
                completions.add("help");
                return completions;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
                List<String> completions = new ArrayList<>();
                completions.add("join");
                completions.add("list");
                return completions;
            }
        } else if (command.getName().equalsIgnoreCase("tab")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();
                completions.add("config");
                return completions;
            }
        }
        return null;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}
