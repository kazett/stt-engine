package id.stt.player;

import id.stt.Stt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDataService implements Listener {

    private final Stt plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataService(Stt plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        folder.mkdirs();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    public PlayerData get(Player player) {
        PlayerData data = get(player.getUniqueId());
        data.setName(player.getName());
        return data;
    }

    public void save(PlayerData data) {
        File file = new File(folder, data.getUuid() + ".yml");
        File tmp = new File(folder, data.getUuid() + ".yml.tmp");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", data.getUuid().toString());
        yaml.set("name", data.getName());
        yaml.set("race", data.getRace());
        yaml.set("current-mana", data.getCurrentMana());
        yaml.set("unlocked-skills", data.unlockedList());
        yaml.set("removed-skills", new java.util.ArrayList<>(data.getRemovedSkills()));

        try {
            yaml.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal simpan player data " + data.getUuid() + ": " + e.getMessage());
            try {
                yaml.save(file); // fallback non-atomic
            } catch (IOException ex) {
                plugin.getLogger().severe("Gagal total simpan player data: " + ex.getMessage());
            }
        }
    }

    public void saveAsync(PlayerData data) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> save(data));
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            save(data);
        }
    }

    private PlayerData load(UUID uuid) {
        File file = new File(folder, uuid + ".yml");
        PlayerData data = new PlayerData(uuid, "Unknown");
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        data.setName(yaml.getString("name", "Unknown"));
        data.setRace(yaml.getString("race", null));
        data.setCurrentMana(yaml.getDouble("current-mana", plugin.getConfig().getDouble("settings.max-mana", 300)));
        data.getUnlockedSkills().addAll(yaml.getStringList("unlocked-skills"));
        data.getRemovedSkills().addAll(yaml.getStringList("removed-skills"));
        return data;
    }

    /** Sinkron skill ras saat join: pastikan semua skill ras ada, kecuali yang di removed-skills. */
    public void syncRaceSkills(PlayerData data) {
        if (!data.hasRace()) return;
        String race = data.getRace();
        var section = plugin.getConfig().getConfigurationSection("races." + race + ".skills");
        if (section == null) {
            // list style
            java.util.List<String> skills = plugin.getConfig().getStringList("races." + race + ".skills");
            for (String skillId : skills) {
                if (!data.getRemovedSkills().contains(skillId)) {
                    data.getUnlockedSkills().add(skillId);
                }
            }
        }
        // also handle map style just in case
        if (plugin.getConfig().isList("races." + race + ".skills")) {
            for (String skillId : plugin.getConfig().getStringList("races." + race + ".skills")) {
                if (!data.getRemovedSkills().contains(skillId)) {
                    data.getUnlockedSkills().add(skillId);
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = get(player);
        syncRaceSkills(data);
        saveAsync(data);
        // grammar refresh handled by VoiceManager listener or SkillService
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getVoiceManager() != null) {
                plugin.getVoiceManager().refreshGrammar(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = cache.get(event.getPlayer().getUniqueId());
        if (data != null) {
            save(data);
        }
        if (plugin.getVoiceManager() != null) {
            plugin.getVoiceManager().remove(event.getPlayer().getUniqueId());
        }
    }
}
