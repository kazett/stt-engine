package id.stt.mana;

import id.stt.Stt;
import id.stt.player.PlayerData;
import id.stt.player.PlayerDataService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class ManaService implements Listener {

    private static final Key FONT = Key.key("stt", "mana");
    private static final char BAR_BASE = '\uE000';
    private static final char DIGIT_BASE = '\uE100';
    private static final char GAP = '\uE0F0';
    private static final char BLANK_DIGIT = '\uE0F1';

    private final Stt plugin;
    private final PlayerDataService playerDataService;
    private final Map<UUID, Long> regenBlockedUntil = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int beat;

    public ManaService(Stt plugin, PlayerDataService playerDataService) {
        this.plugin = plugin;
        this.playerDataService = playerDataService;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        regenBlockedUntil.clear();
    }

    public double maxMana() {
        return plugin.getConfig().getDouble("settings.max-mana", 300);
    }

    public double getMana(Player player) {
        return playerDataService.get(player).getCurrentMana();
    }

    public boolean tryConsume(Player player, double cost) {
        PlayerData data = playerDataService.get(player);
        if (data.getCurrentMana() < cost) return false;
        data.setCurrentMana(data.getCurrentMana() - cost);
        // block regen briefly
        int delay = plugin.getConfig().getInt("settings.mana-regen-delay", 3);
        regenBlockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + delay * 1000L);
        playerDataService.saveAsync(data);
        return true;
    }

    public void setMana(Player player, double value) {
        PlayerData data = playerDataService.get(player);
        data.setCurrentMana(Math.min(maxMana(), Math.max(0, value)));
        playerDataService.saveAsync(data);
    }

    private void tick() {
        beat++;
        double max = maxMana();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) continue;
            PlayerData data = playerDataService.get(player);
            double mana = data.getCurrentMana();

            // regen
            Long blocked = regenBlockedUntil.get(player.getUniqueId());
            if (blocked == null || System.currentTimeMillis() > blocked) {
                if (mana < max) {
                    data.setCurrentMana(Math.min(max, mana + 1.5)); // ~3 mana / 3 sec roughly
                }
            }

            // low mana debuff
            if (mana < 0.5) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false, false));
                if (beat % 20 == 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.4f, 0.5f);
                }
            }

            // HUD every few ticks
            if (beat % 2 == 0) {
                sendManaHud(player, data.getCurrentMana(), max);
            }
        }
    }

    private void sendManaHud(Player player, double mana, double max) {
        // Prefer custom font if resource pack loaded
        try {
            Component hud = renderCustomFont(mana, max);
            player.sendActionBar(hud);
        } catch (Exception e) {
            player.sendActionBar(Component.text("Mana: " + (int) mana + "/" + (int) max).color(NamedTextColor.AQUA));
        }
    }

    private Component renderCustomFont(double mana, double max) {
        int percent = (int) Math.round((mana / max) * 50);
        percent = Math.max(0, Math.min(50, percent));
        StringBuilder text = new StringBuilder();
        text.append((char) (BAR_BASE + percent));
        text.append(GAP);
        String num = String.valueOf((int) mana);
        for (int i = 0; i < 3 - num.length(); i++) text.append(BLANK_DIGIT);
        for (char c : num.toCharArray()) {
            text.append((char) (DIGIT_BASE + (c - '0')));
        }
        return Component.text(text.toString()).font(FONT).color(NamedTextColor.WHITE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // ensure mana is loaded
        playerDataService.get(event.getPlayer());
    }
}
