package id.stt.skill.native_skills;

import id.stt.skill.NativeSkill;
import id.stt.skill.SkillDefinition;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class MeteorCrashSkill implements NativeSkill, Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> active = new HashSet<>();

    public MeteorCrashSkill(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "meteor-crash";
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && active.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void cast(Player player, SkillDefinition def) {
        double height = def.num("height", 20);
        double up = def.num("up-speed", 1.0);
        double down = def.num("down-speed", 2.5);
        double radius = def.num("radius", 6);
        double damage = def.num("damage", 16);
        double power = def.num("power", 5);
        double depth = def.num("depth", 10);
        UUID id = player.getUniqueId();
        double startY = player.getLocation().getY();

        active.add(id);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, up, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);

        new BukkitRunnable() {
            private boolean rising = true;
            private double lastY = startY;
            private int stuck;
            private int ticks;
            private int slamTicks;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || player.isDead() || ticks > 120) {
                    finish();
                    return;
                }
                player.setFallDistance(0);
                Location loc = player.getLocation();
                double y = loc.getY();
                if (ticks > 3) {
                    stuck = (rising ? y - lastY : lastY - y) < 0.05 ? stuck + 1 : 0;
                }
                lastY = y;
                player.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 8, 0.3, 0.4, 0.3, 0.02);

                if (rising) {
                    if (y >= startY + height || stuck >= 2) {
                        rising = false;
                        stuck = 0;
                        player.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
                    } else {
                        player.setVelocity(new Vector(0, up, 0));
                        return;
                    }
                }

                slamTicks++;
                if (slamTicks > 2 && (player.isOnGround() || stuck >= 15)) {
                    Blast.explode(player, player.getLocation(), radius, damage);
                    Blast.dig(plugin, player, player.getLocation(), (float) power, depth);
                    finish();
                    return;
                }
                player.setVelocity(new Vector(0, -down, 0));
            }

            private void finish() {
                cancel();
                if (player.isOnline()) player.setFallDistance(0);
                new BukkitRunnable() {
                    private int delayTicks = 0;
                    @Override
                    public void run() {
                        delayTicks++;
                        if (!player.isOnline() || player.isOnGround() || delayTicks >= 100) {
                            if (player.isOnline()) player.setFallDistance(0);
                            active.remove(id);
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 1L, 2L);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void shutdown() {
        active.clear();
    }
}
