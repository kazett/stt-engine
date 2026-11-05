package id.stt.skill.native_skills;

import id.stt.skill.NativeSkill;
import id.stt.skill.SkillDefinition;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class BlackFireSkill implements NativeSkill {

    private static final double CONE_COS = 0.85;
    private final JavaPlugin plugin;

    public BlackFireSkill(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "black-fire";
    }

    @Override
    public void cast(Player player, SkillDefinition def) {
        double range = def.num("range", 15);
        double durationSec = def.num("duration", 5);
        double damagePerHit = def.num("damage-per-sec", 4.0) / 2.0;
        boolean hitMobs = def.bool("hit-mobs", false);
        int totalTicks = (int) (durationSec * 20);

        LivingEntity target = findTarget(player, range, hitMobs);
        if (target == null) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 0.5f);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.6f);

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                ticks += 10;
                if (!target.isValid() || target.isDead() || ticks > totalTicks) {
                    cancel();
                    return;
                }
                target.damage(damagePerHit, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 1, false, false, false));

                Location loc = target.getLocation().add(0, target.getHeight() / 2.0, 0);
                target.getWorld().spawnParticle(Particle.SQUID_INK, loc, 15, 0.3, 0.5, 0.3, 0.02);
                target.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 10, 0.2, 0.4, 0.2, 0.01);
                target.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 8, 0.2, 0.4, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 10L);
    }

    private LivingEntity findTarget(Player player, double range, boolean hitMobs) {
        RayTraceResult ray = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range, 0.8,
                entity -> isValidTarget(entity, player, hitMobs));
        if (ray != null && ray.getHitEntity() instanceof LivingEntity living) {
            return living;
        }
        LivingEntity nearest = null;
        double bestDist = range * range;
        Location eye = player.getEyeLocation();
        Vector facing = eye.getDirection();
        for (Entity e : player.getNearbyEntities(range, range, range)) {
            if (isValidTarget(e, player, hitMobs)) {
                Vector toTarget = e.getLocation().add(0, e.getHeight() / 2.0, 0).toVector().subtract(eye.toVector());
                if (toTarget.lengthSquared() == 0 || toTarget.normalize().dot(facing) < CONE_COS) continue;
                double dist = player.getLocation().distanceSquared(e.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = (LivingEntity) e;
                }
            }
        }
        return nearest;
    }

    private boolean isValidTarget(Entity entity, Player caster, boolean hitMobs) {
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity.equals(caster)) {
            return false;
        }
        if (!hitMobs && !(entity instanceof Player)) return false;
        return caster.hasLineOfSight(entity);
    }
}
