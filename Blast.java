package id.stt.skill.native_skills;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class Blast {

    private Blast() {}

    public static void explode(Player caster, Location center, double radius, double damage) {
        center.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        center.getWorld().spawnParticle(Particle.FLAME, center, 60, radius / 3, 0.4, radius / 3, 0.05);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);

        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity living && !living.equals(caster) && living.getLocation().distance(center) <= radius) {
                living.damage(damage, caster);
                Vector knock = living.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.2).setY(0.4);
                living.setVelocity(knock);
            }
        }
    }

    public static void dig(JavaPlugin plugin, Player caster, Location center, float power, double depth) {
        List<Block> toBreak = new ArrayList<>();
        int r = (int) Math.ceil(power);
        for (int x = -r; x <= r; x++) {
            for (int y = - (int) depth; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.AIR || b.getType() == Material.BEDROCK || b.isLiquid()) continue;
                    toBreak.add(b);
                }
            }
        }
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                int batch = 8;
                while (i < toBreak.size() && batch-- > 0) {
                    Block b = toBreak.get(i++);
                    if (b.getType() != Material.AIR && b.getType() != Material.BEDROCK) {
                        b.breakNaturally();
                    }
                }
                if (i >= toBreak.size()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
