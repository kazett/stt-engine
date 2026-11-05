package id.stt;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import id.stt.command.SttCommand;
import id.stt.deployer.AssetsDeployer;
import id.stt.gui.SttGui;
import id.stt.hud.CooldownHud;
import id.stt.mana.ManaService;
import id.stt.player.PlayerDataService;
import id.stt.skill.SkillService;
import id.stt.voice.VoiceManager;
import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.vosk.Model;

public final class Stt extends JavaPlugin {

    private Model model;
    private VoiceManager voiceManager;
    private ManaService manaService;
    private PlayerDataService playerDataService;
    private SkillService skillService;
    private SttGui gui;
    private CooldownHud cooldownHud;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        // 1. Deploy assets (mirror deploy/ -> plugins/)
        new AssetsDeployer(this).deployAll();

        // 2. Vosk model
        File modelDir = new File(getDataFolder(), "model");
        if (!modelDir.isDirectory()) {
            getLogger().severe("Folder model Vosk tidak ada: " + modelDir.getPath());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            model = new Model(modelDir.getPath());
        } catch (IOException e) {
            getLogger().severe("Gagal load model Vosk: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Voice chat
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().severe("Simple Voice Chat tidak ditemukan");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Core services
        playerDataService = new PlayerDataService(this);
        manaService = new ManaService(this, playerDataService);
        skillService = new SkillService(this, manaService, playerDataService);
        voiceManager = new VoiceManager(this, model, skillService, playerDataService);
        cooldownHud = new CooldownHud(this, skillService, playerDataService);
        gui = new SttGui(this, playerDataService, skillService, manaService);

        // 5. Register
        service.registerPlugin(voiceManager);
        getCommand("stt").setExecutor(new SttCommand(this, gui, playerDataService, skillService, manaService, voiceManager));
        getCommand("stt").setTabCompleter(new SttCommand(this, gui, playerDataService, skillService, manaService, voiceManager));

        manaService.start();
        skillService.start();
        cooldownHud.start();

        // Validate config costs
        double maxMana = getConfig().getDouble("settings.max-mana", 300);
        for (String id : getConfig().getConfigurationSection("skills").getKeys(false)) {
            double cost = getConfig().getDouble("skills." + id + ".cost", 0);
            if (cost > maxMana) {
                getLogger().warning("Skill '" + id + "' cost=" + cost + " > max-mana=" + maxMana + " — tidak ada player yang bisa cast!");
            }
        }

        getLogger().info("STT Engine v3.0.0 enabled (Race system + Dynamic Voice + MythicMobs)");
    }

    @Override
    public void onDisable() {
        if (cooldownHud != null) cooldownHud.stop();
        if (skillService != null) skillService.stop();
        if (manaService != null) manaService.stop();
        if (voiceManager != null) voiceManager.shutdown();
        if (playerDataService != null) playerDataService.saveAll();
        if (model != null) model.close();
    }

    public File getPluginFile() {
        return getFile();
    }

    public VoiceManager getVoiceManager() {
        return voiceManager;
    }

    public ManaService getManaService() {
        return manaService;
    }

    public PlayerDataService getPlayerDataService() {
        return playerDataService;
    }

    public SkillService getSkillService() {
        return skillService;
    }

    public CooldownHud getCooldownHud() {
        return cooldownHud;
    }
}
