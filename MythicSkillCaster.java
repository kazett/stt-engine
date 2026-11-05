package id.stt.skill;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Memanggil skill MythicMobs lewat BukkitAPIHelper#castSkill(Entity, String).
 * STT hanya memanggil skill. Damage, durasi, dan efek diatur sepenuhnya di file skill pack.
 * Memakai reflection supaya tidak ada compile dependency ke MythicMobs.
 */
public final class MythicSkillCaster {

    private final Logger logger;
    private boolean available;
    private Object mythicBukkit;
    private Object apiHelper;
    private Method castMethod;        // BukkitAPIHelper#castSkill(Entity, String) -> boolean
    private Method getSkillMethod;    // SkillManager#getSkill(String) -> Optional
    private Method getSkillManager;   // MythicBukkit#getSkillManager()

    public MythicSkillCaster(Logger logger) {
        this.logger = logger;
        detect();
    }

    /** Deteksi ulang plugin dan API. Dipanggil saat startup dan /stt reload. */
    public void detect() {
        available = false;
        mythicBukkit = null;
        apiHelper = null;
        castMethod = null;
        getSkillMethod = null;
        getSkillManager = null;

        Plugin ml = Bukkit.getPluginManager().getPlugin("MythicLib");
        Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
        boolean mlOn = ml != null && ml.isEnabled();
        boolean mmOn = mm != null && mm.isEnabled();

        if (!mmOn) {
            logger.warning("MythicMobs tidak aktif — skill bertipe MYTHIC_MOBS dinonaktifkan.");
        }
        if (!mlOn) {
            logger.warning("MythicLib tidak aktif — skill bertipe MYTHIC_MOBS dinonaktifkan (pack memakai kondisi MythicLib).");
        }
        if (!mmOn || !mlOn) return;

        try {
            Class<?> bukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            mythicBukkit = bukkitClass.getMethod("inst").invoke(null);
            apiHelper = bukkitClass.getMethod("getAPIHelper").invoke(mythicBukkit);

            Class<?> helperClass = Class.forName("io.lumine.mythic.bukkit.BukkitAPIHelper");
            castMethod = helperClass.getMethod("castSkill", Entity.class, String.class);

            getSkillManager = bukkitClass.getMethod("getSkillManager");
            Class<?> managerClass = Class.forName("io.lumine.mythic.api.skills.SkillManager");
            getSkillMethod = managerClass.getMethod("getSkill", String.class);

            available = true;
        } catch (Exception e) {
            logger.warning("API MythicMobs tidak bisa dipakai (" + e + ") — skill MYTHIC_MOBS dinonaktifkan.");
        }
    }

    /** True jika MythicMobs dan MythicLib aktif dan API siap dipakai. */
    public boolean isAvailable() {
        return available;
    }

    /** Cek apakah nama skill terdaftar di MythicMobs. Dipakai sebelum mana dipotong. */
    public boolean skillExists(String skillName) {
        if (!available) return false;
        try {
            Object manager = getSkillManager.invoke(mythicBukkit);
            Object result = getSkillMethod.invoke(manager, skillName);
            return result instanceof Optional<?> opt && opt.isPresent();
        } catch (Exception e) {
            logger.warning("Gagal cek skill MythicMobs '" + skillName + "': " + e);
            return false;
        }
    }

    /** Cast skill dengan player sebagai caster. Return true jika MythicMobs menerima cast. */
    public boolean cast(Player player, SkillDefinition def) {
        if (!available) return false;
        String skillName = def.mythicSkillName();
        try {
            return (boolean) castMethod.invoke(apiHelper, player, skillName);
        } catch (Exception e) {
            logger.warning("Gagal cast skill MythicMobs '" + skillName + "' untuk " + player.getName() + ": " + e);
            return false;
        }
    }
}
