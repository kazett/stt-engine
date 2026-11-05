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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class GravityPullSkill implements NativeSkill {

    @Override
    public String id() {
        return "gravity-pull";
    }

    @Override
    public void cast(Player player, SkillDefinition def) {
        double radius = def.num("radius", 12);
        double power = def.num("power", 1.5);
        int slowLevel = (int) def.num("slow-level", 2);
        double slowSec = def.num("slow-duration", 1.5);
        int slowTicks = (int) (slowSec * 20);
        double front = def.num("front-distance", 3);

        Location casterLoc = player.getLocation();
        Vector facing = casterLoc.getDirection().setY(0);
        if (facing.lengthSquared() < 0.0001) {
            facing = new Vector(0, 0, 1);
        }
        Location pullPoint = casterLoc.clone().add(facing.normalize().multiply(front));

        player.getWorld().playSound(casterLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        player.getWorld().playSound(casterLoc, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.6f); // 1.20.1 safe

        for (Entity entity : player.getWorld().getNearbyEntities(casterLoc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity instanceof ArmorStand || entity.equals(player)) {
                continue;
            }
            Location targetLoc = target.getLocation();
            if (targetLoc.distance(casterLoc) > radius || targetLoc.distance(pullPoint) < 0.5) continue;

            Vector dir = pullPoint.toVector().subtract(targetLoc.toVector()).normalize();
            dir.multiply(power).setY(Math.min(0.6, Math.max(0.2, dir.getY() + 0.2)));
            target.setVelocity(dir);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slowTicks, Math.max(0, slowLevel - 1), false, true, true));
            target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, targetLoc.clone().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.1);
        }

        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2 * i / 36;
            casterLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL,
                    casterLoc.getX() + Math.cos(angle) * 2,
                    casterLoc.getY() + 0.5,
                    casterLoc.getZ() + Math.sin(angle) * 2, 1, 0, 0, 0, 0);
        }
    }
}
