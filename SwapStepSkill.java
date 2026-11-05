package id.stt.skill.native_skills;

import id.stt.skill.NativeSkill;
import id.stt.skill.SkillDefinition;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class SwapStepSkill implements NativeSkill {

    @Override
    public String id() {
        return "swap-step";
    }

    @Override
    public boolean canCast(Player player) {
        return findTarget(player, 8) != null;
    }

    @Override
    public void cast(Player player, SkillDefinition def) {
        double range = def.num("range", 8);
        Player target = findTarget(player, range);
        if (target == null) return;

        Location from = player.getLocation();
        Location to = target.getLocation();
        Location playerDest = to.clone();
        playerDest.setYaw(from.getYaw());
        playerDest.setPitch(from.getPitch());
        Location targetDest = from.clone();
        targetDest.setYaw(to.getYaw());
        targetDest.setPitch(to.getPitch());

        from.getWorld().spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        to.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        player.teleport(playerDest);
        target.teleport(targetDest);
        player.setFallDistance(0);
        target.setFallDistance(0);
    }

    private Player findTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult ray = player.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.8,
                entity -> isValid(entity, player));
        if (ray == null || !(ray.getHitEntity() instanceof Player target) || ray.getHitPosition() == null) {
            return null;
        }
        Vector delta = ray.getHitPosition().clone().subtract(eye.toVector());
        double distance = delta.length();
        if (distance > 0.2) {
            RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, delta.normalize(), distance - 0.1,
                    FluidCollisionMode.NEVER, true);
            if (wall != null) return null;
        }
        return target;
    }

    private boolean isValid(Entity entity, Player caster) {
        return entity instanceof Player other
                && !other.equals(caster)
                && other.getGameMode() != GameMode.SPECTATOR
                && caster.hasLineOfSight(other);
    }
}
