package id.stt.player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerData {

    private final UUID uuid;
    private String name;
    private String race; // null = human
    private double currentMana;
    private final Set<String> unlockedSkills = new HashSet<>();
    private final Set<String> removedSkills = new HashSet<>();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.currentMana = 300.0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = race;
    }

    public boolean hasRace() {
        return race != null && !race.isBlank();
    }

    public double getCurrentMana() {
        return currentMana;
    }

    public void setCurrentMana(double currentMana) {
        this.currentMana = Math.max(0, currentMana);
    }

    public Set<String> getUnlockedSkills() {
        return unlockedSkills;
    }

    public Set<String> getRemovedSkills() {
        return removedSkills;
    }

    public boolean hasSkill(String skillId) {
        return unlockedSkills.contains(skillId);
    }

    public void addSkill(String skillId) {
        unlockedSkills.add(skillId);
        removedSkills.remove(skillId);
    }

    public void removeSkill(String skillId) {
        unlockedSkills.remove(skillId);
    }

    public void markRemoved(String skillId) {
        removedSkills.add(skillId);
        unlockedSkills.remove(skillId);
    }

    public List<String> unlockedList() {
        return new ArrayList<>(unlockedSkills);
    }
}
