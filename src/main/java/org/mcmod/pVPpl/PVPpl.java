package org.mcmod.pVPpl;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
                handleTeamCommand(sender, args);
                return true;
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
    
    private void handleTeamCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /pvp team <create|delete|invite|accept|leave|promote|list>");
            return;
        }
        
        String action = args[1].toLowerCase();
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return;
        }
        
        Player player = (Player) sender;
        
        switch (action) {
            case "create":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /pvp team create <teamName>");
                    return;
                }
                if (gameManager.createTeam(player, args[2])) {
                    player.sendMessage("§aTeam '" + args[2] + "' created.");
                } else {
                    player.sendMessage("§cFailed to create team (already in a team or name exists).");
                }
                break;
            case "delete":
                if (!player.hasPermission("pvppl.admin")) {
                    player.sendMessage("You do not have permission.");
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /pvp team delete <teamName>");
                    return;
                }
                if (gameManager.deleteTeam(args[2])) {
                    player.sendMessage("§aTeam '" + args[2] + "' deleted.");
                } else {
                    player.sendMessage("§cTeam not found.");
                }
                break;
            case "invite":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /pvp team invite <player>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found.");
                    return;
                }
                if (gameManager.inviteToTeam(player, target)) {
                    player.sendMessage("§aInvited " + target.getName() + " to your team.");
                    target.sendMessage("§aYou have been invited to " + player.getName() + "'s team. Use /pvp team accept to join.");
                } else {
                    player.sendMessage("§cFailed to invite player (not leader, team full, or player in team).");
                }
                break;
            case "accept":
                if (gameManager.acceptInvite(player)) {
                    player.sendMessage("§aYou have joined the team.");
                } else {
                    player.sendMessage("§cNo pending invitation or team is full.");
                }
                break;
            case "leave":
                if (gameManager.leaveTeam(player)) {
                    player.sendMessage("§aYou have left the team.");
                } else {
                    player.sendMessage("§cYou are not in a team.");
                }
                break;
            case "promote":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /pvp team promote <player>");
                    return;
                }
                Player newLeader = Bukkit.getPlayer(args[2]);
                if (newLeader == null) {
                    player.sendMessage("§cPlayer not found.");
                    return;
                }
                if (gameManager.promoteLeader(player, newLeader)) {
                    player.sendMessage("§a" + newLeader.getName() + " is now the team leader.");
                    newLeader.sendMessage("§aYou are now the team leader.");
                } else {
                    player.sendMessage("§cFailed to promote player (not leader or player not in team).");
                }
                break;
            case "list":
                Map<String, List<String>> teams = gameManager.getTeamList();
                sender.sendMessage("§6========== [ Team List ] ==========");
                for (Map.Entry<String, List<String>> entry : teams.entrySet()) {
                    sender.sendMessage("§e" + entry.getKey() + ": §f" + String.join(", ", entry.getValue()));
                }
                sender.sendMessage("§6===================================");
                break;
            default:
                sender.sendMessage("§cUnknown team command.");
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== [ PVP Plugin Help ] ==========");
        sender.sendMessage("§e/pvp start §f- Starts the game.");
        sender.sendMessage("§e/pvp stop §f- Stops the current game.");
        sender.sendMessage("§e/pvp config §f- Open configuration GUI.");
        sender.sendMessage("§e/pvp info <player> §f- Shows mining info and inventory.");
        sender.sendMessage("§e/pvp history §f- Shows recent game history.");
        sender.sendMessage("§e/pvp team <...> §f- Manage teams.");
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
                completions.add("create");
                completions.add("delete");
                completions.add("invite");
                completions.add("accept");
                completions.add("leave");
                completions.add("promote");
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
