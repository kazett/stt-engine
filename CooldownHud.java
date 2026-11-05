package id.stt.hud;

import id.stt.Stt;
import id.stt.player.PlayerDataService;
import id.stt.skill.SkillDefinition;
import id.stt.skill.SkillService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Center-top cooldown HUD using Title (subtitle area is naturally center-top).
 * Layout example:
 *   Swap Step  ●  0.0s    Black Fire  ●  Ready
 * Green bullet = ready, Red bullet = on cooldown.
 * Works on all resolutions because Title is resolution-independent.
 */
public final class CooldownHud {

    private final Stt plugin;
    private final SkillService skillService;
    private final PlayerDataService playerDataService;
    private BukkitTask task;

    public CooldownHud(Stt plugin, SkillService skillService, PlayerDataService playerDataService) {
        this.plugin = plugin;
        this.skillService = skillService;
        this.playerDataService = playerDataService;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("settings.cooldown-hud-enabled", true)) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<SkillDefinition> skills = skillService.unlockedSkillsOf(player);
            if (skills.isEmpty()) continue;

            // Build compact one-line HUD (max ~4 skills shown to avoid overflow)
            Component line = Component.empty();
            int shown = 0;
            for (SkillDefinition def : skills) {
                if (shown >= 4) break;
                double left = skillService.remainingCooldown(player.getUniqueId(), def.id());
                boolean ready = left <= 0.05;

                if (shown > 0) {
                    line = line.append(Component.text("  │  ").color(NamedTextColor.DARK_GRAY));
                }

                // skill name
                line = line.append(Component.text(def.name()).color(NamedTextColor.WHITE));
                line = line.append(Component.text(" "));

                // colored bullet
                TextColor bulletColor = ready ? NamedTextColor.GREEN : NamedTextColor.RED;
                line = line.append(Component.text("●").color(bulletColor));

                // timer or Ready
                if (ready) {
                    line = line.append(Component.text(" Ready").color(NamedTextColor.GREEN));
                } else {
                    line = line.append(Component.text(" " + String.format("%.0f", Math.ceil(left)) + "s")
                            .color(NamedTextColor.RED));
                }
                shown++;
            }

            // Use actionbar for clean center display (doesn't block view like title)
            // Combined with mana actionbar? Mana uses actionbar too.
            // To avoid conflict, only show cooldown HUD when any skill is on cooldown,
            // otherwise let mana HUD take the actionbar.
            boolean anyCd = skills.stream()
                    .anyMatch(d -> skillService.remainingCooldown(player.getUniqueId(), d.id()) > 0.05);
            if (anyCd) {
                player.sendActionBar(line);
            }
            // When no CD, ManaService continues to own the actionbar.
        }
    }
}
