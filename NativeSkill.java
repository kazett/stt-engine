package id.stt.skill;

import org.bukkit.entity.Player;

public interface NativeSkill {
    String id();
    void cast(Player player, SkillDefinition def);
    default boolean canCast(Player player) { return true; }
    default void shutdown() {}
}
