package id.stt.command;

import id.stt.Stt;
import id.stt.gui.SttGui;
import id.stt.mana.ManaService;
import id.stt.player.PlayerData;
import id.stt.player.PlayerDataService;
import id.stt.skill.SkillDefinition;
import id.stt.skill.SkillService;
import id.stt.voice.VoiceManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class SttCommand implements CommandExecutor, TabCompleter {

    private final Stt plugin;
    private final SttGui gui;
    private final PlayerDataService playerDataService;
    private final SkillService skillService;
    private final ManaService manaService;
    private final VoiceManager voiceManager;

    public SttCommand(Stt plugin, SttGui gui, PlayerDataService playerDataService,
                      SkillService skillService, ManaService manaService, VoiceManager voiceManager) {
        this.plugin = plugin;
        this.gui = gui;
        this.playerDataService = playerDataService;
        this.skillService = skillService;
        this.manaService = manaService;
        this.voiceManager = voiceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Hanya player.");
                return true;
            }
            gui.open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Hanya player.");
                    return true;
                }
                gui.open(player);
            }
            case "reload" -> {
                if (!sender.hasPermission("stt.admin")) {
                    sender.sendMessage("§cTidak punya permission.");
                    return true;
                }
                skillService.reload();
                sender.sendMessage("§aConfig & skill definitions reloaded.");
            }
            case "addskill" -> {
                if (!sender.hasPermission("stt.admin")) {
                    sender.sendMessage("§cTidak punya permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /stt addskill <player> <skill_id>");
                    return true;
                }
                handleAddSkill(sender, args[1], args[2]);
            }
            case "removeskill" -> {
                if (!sender.hasPermission("stt.admin")) {
                    sender.sendMessage("§cTidak punya permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /stt removeskill <player> <skill_id>");
                    return true;
                }
                handleRemoveSkill(sender, args[1], args[2]);
            }
            case "mana" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Hanya player.");
                    return true;
                }
                double mana = manaService.getMana(player);
                player.sendMessage("§bMana: §f" + (int) mana + " / " + (int) manaService.maxMana());
            }
            default -> sender.sendMessage("§cUnknown subcommand. /stt [menu|reload|addskill|removeskill|mana]");
        }
        return true;
    }

    private void handleAddSkill(CommandSender sender, String playerName, String skillId) {
        SkillDefinition def = skillService.getDefinition(skillId);
        if (def == null) {
            sender.sendMessage("§cSkill '" + skillId + "' tidak ada di config.yml");
            return;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
        if (!off.hasPlayedBefore() && !off.isOnline()) {
            sender.sendMessage("§cPlayer belum pernah join server.");
            return;
        }
        UUID uuid = off.getUniqueId();
        PlayerData data = playerDataService.get(uuid);
        data.setName(off.getName() != null ? off.getName() : playerName);

        if (data.hasSkill(skillId)) {
            sender.sendMessage("§ePlayer sudah memiliki skill " + skillId);
            return;
        }

        // Phrase collision warning
        String newPhrase = def.phrase();
        for (String owned : data.getUnlockedSkills()) {
            SkillDefinition other = skillService.getDefinition(owned);
            if (other != null) {
                String p = other.phrase();
                if (p.equals(newPhrase) || p.startsWith(newPhrase + " ") || newPhrase.startsWith(p + " ")) {
                    sender.sendMessage("§e§lWARNING: Phrase '" + newPhrase + "' mirip/bentrok dengan skill '" + owned + "' (" + p + ")");
                }
            }
        }

        data.addSkill(skillId);
        playerDataService.saveAsync(data);

        Player online = off.getPlayer();
        if (online != null && online.isOnline()) {
            voiceManager.refreshGrammar(online);
            online.sendMessage("§aKamu mendapat skill §b" + def.name() + "§a!");
        }
        sender.sendMessage("§aSkill §b" + skillId + " §aditambahkan ke " + playerName);
    }

    private void handleRemoveSkill(CommandSender sender, String playerName, String skillId) {
        SkillDefinition def = skillService.getDefinition(skillId);
        if (def == null) {
            sender.sendMessage("§cSkill '" + skillId + "' tidak ada di config.yml");
            return;
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
        if (!off.hasPlayedBefore() && !off.isOnline()) {
            sender.sendMessage("§cPlayer belum pernah join server.");
            return;
        }
        UUID uuid = off.getUniqueId();
        PlayerData data = playerDataService.get(uuid);

        if (!data.hasSkill(skillId)) {
            sender.sendMessage("§ePlayer tidak memiliki skill " + skillId);
            return;
        }

        // If skill belongs to race, mark as removed so join sync won't re-grant
        if (data.hasRace()) {
            List<String> raceSkills = plugin.getConfig().getStringList("races." + data.getRace() + ".skills");
            if (raceSkills.contains(skillId)) {
                data.markRemoved(skillId);
            } else {
                data.removeSkill(skillId);
            }
        } else {
            data.removeSkill(skillId);
        }
        playerDataService.saveAsync(data);

        Player online = off.getPlayer();
        if (online != null && online.isOnline()) {
            voiceManager.refreshGrammar(online);
            online.sendMessage("§cSkill §b" + (def != null ? def.name() : skillId) + " §cdicabut.");
        }
        sender.sendMessage("§aSkill §b" + skillId + " §adihapus dari " + playerName);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("menu", "mana"));
            if (sender.hasPermission("stt.admin")) {
                subs.addAll(Arrays.asList("reload", "addskill", "removeskill"));
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("addskill") || args[0].equalsIgnoreCase("removeskill"))) {
            return null; // player names
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addskill")) {
            return filter(new ArrayList<>(skillService.allDefinitions().keySet()), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("removeskill")) {
            // only owned skills if target is online
            OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
            if (off.getUniqueId() != null) {
                PlayerData data = playerDataService.get(off.getUniqueId());
                return filter(new ArrayList<>(data.getUnlockedSkills()), args[2]);
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
