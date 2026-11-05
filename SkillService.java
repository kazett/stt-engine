package id.stt.skill;

import id.stt.Stt;
import id.stt.mana.ManaService;
import id.stt.player.PlayerData;
import id.stt.player.PlayerDataService;
import id.stt.skill.native_skills.BlackFireSkill;
import id.stt.skill.native_skills.GravityPullSkill;
import id.stt.skill.native_skills.MeteorCrashSkill;
import id.stt.skill.native_skills.SwapStepSkill;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public final class SkillService {

    private final Stt plugin;
    private final ManaService manaService;
    private final PlayerDataService playerDataService;
    private final Map<String, SkillDefinition> definitions = new HashMap<>();
    private final Map<String, NativeSkill> nativeSkills = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final MythicSkillCaster mythicCaster;

    public SkillService(Stt plugin, ManaService manaService, PlayerDataService playerDataService) {
        this.plugin = plugin;
        this.manaService = manaService;
        this.playerDataService = playerDataService;
        this.mythicCaster = new MythicSkillCaster(plugin.getLogger());
        loadDefinitions();
        registerNatives();
    }

    private void loadDefinitions() {
        definitions.clear();
        var sec = plugin.getConfig().getConfigurationSection("skills");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            definitions.put(id, new SkillDefinition(id, sec.getConfigurationSection(id)));
        }
    }

    private void registerNatives() {
        List<NativeSkill> list = List.of(
                new SwapStepSkill(),
                new BlackFireSkill(plugin),
                new GravityPullSkill(),
                new MeteorCrashSkill(plugin)
        );
        for (NativeSkill s : list) {
            nativeSkills.put(s.id(), s);
            if (s instanceof Listener listener) {
                Bukkit.getPluginManager().registerEvents(listener, plugin);
            }
        }
    }

    public void start() {
        // nothing extra
    }

    public void stop() {
        for (NativeSkill s : nativeSkills.values()) {
            s.shutdown();
        }
    }

    public void reload() {
        plugin.reloadConfig();
        loadDefinitions();
        mythicCaster.detect();
    }

    public SkillDefinition getDefinition(String id) {
        return definitions.get(id);
    }

    public Map<String, SkillDefinition> allDefinitions() {
        return definitions;
    }

    public List<String> getUnlockedPhrases(UUID uuid) {
        PlayerData data = playerDataService.get(uuid);
        List<String> phrases = new ArrayList<>();
        for (String skillId : data.getUnlockedSkills()) {
            SkillDefinition def = definitions.get(skillId);
            if (def != null) {
                phrases.add(def.phrase());
            }
        }
        return phrases;
    }

    public boolean isOnCooldown(UUID uuid, String skillId) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return false;
        Long until = map.get(skillId);
        return until != null && System.currentTimeMillis() < until;
    }

    public double remainingCooldown(UUID uuid, String skillId) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) return 0;
        Long until = map.get(skillId);
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000.0);
    }

    public void setCooldown(UUID uuid, String skillId, double seconds) {
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(skillId, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    /**
     * Called after Vosk recognizes a phrase.
     * Double-checks unlocked-skills before executing.
     */
    public void tryCastByPhrase(Player player, String phrase) {
        if (phrase == null || phrase.isBlank()) return;
        String normalized = phrase.toLowerCase().trim();

        PlayerData data = playerDataService.get(player);
        SkillDefinition matched = null;
        for (String skillId : data.getUnlockedSkills()) {
            SkillDefinition def = definitions.get(skillId);
            if (def != null && def.phrase().equalsIgnoreCase(normalized)) {
                matched = def;
                break;
            }
        }
        if (matched == null) return; // not owned or unknown

        execute(player, matched);
    }

    public boolean execute(Player player, SkillDefinition def) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataService.get(player);

        if (!data.hasSkill(def.id())) {
            player.sendMessage("§cKamu belum memiliki skill ini.");
            return false;
        }

        if (isOnCooldown(uuid, def.id())) {
            double left = remainingCooldown(uuid, def.id());
            player.sendMessage("§cCooldown " + def.name() + ": §e" + String.format("%.1f", left) + "s");
            return false;
        }

        if (manaService.getMana(player) < def.cost()) {
            player.sendMessage("§cMana tidak cukup! Butuh §b" + (int) def.cost());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 0.8f);
            return false;
        }

        // Cek skill MythicMobs dulu, sebelum mana dipotong dan cooldown dimulai
        if (def.type() == SkillType.MYTHIC_MOBS) {
            if (!mythicCaster.isAvailable()) {
                player.sendMessage("§cSkill MythicMobs tidak tersedia (MythicMobs/MythicLib belum aktif).");
                plugin.getLogger().warning("Cast " + def.id() + " dibatalkan: MythicMobs/MythicLib tidak siap.");
                return false;
            }
            if (!mythicCaster.skillExists(def.mythicSkillName())) {
                player.sendMessage("§cSkill " + def.name() + " belum terdaftar di MythicMobs.");
                plugin.getLogger().warning("Cast " + def.id() + " dibatalkan: skill MythicMobs '"
                        + def.mythicSkillName() + "' tidak ditemukan. Cek folder MythicMobs/Skills dan jalankan /mm reload.");
                return false;
            }
        }

        // Native canCast
        if (def.type() == SkillType.NATIVE) {
            NativeSkill nativeSkill = nativeSkills.get(def.id());
            if (nativeSkill != null && !nativeSkill.canCast(player)) {
                return false; // silent fail like old system
            }
        }

        // Consume mana + set cooldown only after success checks
        if (!manaService.tryConsume(player, def.cost())) {
            return false;
        }

        boolean success = false;
        if (def.type() == SkillType.NATIVE) {
            NativeSkill nativeSkill = nativeSkills.get(def.id());
            if (nativeSkill != null) {
                nativeSkill.cast(player, def);
                success = true;
            }
        } else if (def.type() == SkillType.MYTHIC_MOBS) {
            success = mythicCaster.cast(player, def);
        }

        if (success) {
            setCooldown(uuid, def.id(), def.cooldown());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        } else {
            // refund mana if cast failed
            manaService.setMana(player, manaService.getMana(player) + def.cost());
            player.sendMessage("§cGagal menjalankan skill " + def.name() + ".");
            plugin.getLogger().warning("Cast gagal untuk " + def.id() + " oleh " + player.getName());
        }
        return success;
    }

    public List<SkillDefinition> unlockedSkillsOf(Player player) {
        PlayerData data = playerDataService.get(player);
        List<SkillDefinition> list = new ArrayList<>();
        for (String id : data.getUnlockedSkills()) {
            SkillDefinition def = definitions.get(id);
            if (def != null) list.add(def);
        }
        return list;
    }
}
