package id.stt.skill;

import org.bukkit.configuration.ConfigurationSection;

public final class SkillDefinition {

    private final String id;
    private final String name;
    private final String phrase;
    private final double cost;
    private final double cooldown;
    private final SkillType type;
    private final String mythicSkillName;
    private final ConfigurationSection raw; // untuk parameter tambahan skill native

    public SkillDefinition(String id, ConfigurationSection sec) {
        this.id = id;
        this.name = sec.getString("name", id);
        this.phrase = sec.getString("phrase", id.replace('-', ' ')).toLowerCase();
        this.cost = sec.getDouble("cost", 20);
        this.cooldown = sec.getDouble("cooldown", 8);
        String typeStr = sec.getString("type", "NATIVE").toUpperCase();
        this.type = typeStr.equals("MYTHIC_MOBS") ? SkillType.MYTHIC_MOBS : SkillType.NATIVE;
        this.mythicSkillName = sec.getString("mythic-skill-name", id);
        this.raw = sec;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String phrase() { return phrase; }
    public double cost() { return cost; }
    public double cooldown() { return cooldown; }
    public SkillType type() { return type; }
    public String mythicSkillName() { return mythicSkillName; }
    public ConfigurationSection raw() { return raw; }

    public double num(String key, double def) {
        return raw != null ? raw.getDouble(key, def) : def;
    }

    public boolean bool(String key, boolean def) {
        return raw != null ? raw.getBoolean(key, def) : def;
    }
}
