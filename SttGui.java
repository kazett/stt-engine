package id.stt.gui;

import id.stt.Stt;
import id.stt.mana.ManaService;
import id.stt.player.PlayerData;
import id.stt.player.PlayerDataService;
import id.stt.skill.SkillDefinition;
import id.stt.skill.SkillService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SttGui implements Listener {

    private final Stt plugin;
    private final PlayerDataService playerDataService;
    private final SkillService skillService;
    private final ManaService manaService;

    public SttGui(Stt plugin, PlayerDataService playerDataService, SkillService skillService, ManaService manaService) {
        this.plugin = plugin;
        this.playerDataService = playerDataService;
        this.skillService = skillService;
        this.manaService = manaService;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        PlayerData data = playerDataService.get(player);
        if (!data.hasRace()) {
            openRaceSelect(player);
        } else {
            openSkillList(player);
        }
    }

    private void openRaceSelect(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text("Pilih Ras (Permanen)").color(NamedTextColor.DARK_PURPLE));
        // Only Witch
        ItemStack witch = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = witch.getItemMeta();
        meta.displayName(Component.text("Race Witch").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Klik untuk memilih. Tidak bisa diganti!").color(NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Skill yang didapat:").color(NamedTextColor.YELLOW));
        for (String id : plugin.getConfig().getStringList("races.witch.skills")) {
            SkillDefinition def = skillService.getDefinition(id);
            lore.add(Component.text(" • " + (def != null ? def.name() : id)).color(NamedTextColor.WHITE));
        }
        meta.lore(lore);
        witch.setItemMeta(meta);
        inv.setItem(4, witch);
        player.openInventory(inv);
    }

    private void openSkillList(Player player) {
        PlayerData data = playerDataService.get(player);
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Skill - " + data.getRace()).color(NamedTextColor.DARK_AQUA));

        int slot = 0;
        for (SkillDefinition def : skillService.unlockedSkillsOf(player)) {
            if (slot >= 27) break;
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(def.name()).color(NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Phrase: §f" + def.phrase()).color(NamedTextColor.GRAY));
            lore.add(Component.text("Cost: §b" + (int) def.cost() + " mana").color(NamedTextColor.GRAY));
            lore.add(Component.text("Cooldown: §e" + (int) def.cooldown() + "s").color(NamedTextColor.GRAY));
            double left = skillService.remainingCooldown(player.getUniqueId(), def.id());
            if (left > 0) {
                lore.add(Component.text("Cooldown: §c" + String.format("%.1f", left) + "s").color(NamedTextColor.RED));
            } else {
                lore.add(Component.text("Ready").color(NamedTextColor.GREEN));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Component title = event.getView().title();
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title);

        if (plain.contains("Pilih Ras")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
            PlayerData data = playerDataService.get(player);
            if (data.hasRace()) {
                player.sendMessage("§cKamu sudah memilih ras!");
                player.closeInventory();
                return;
            }
            // Only witch available
            data.setRace("witch");
            for (String skillId : plugin.getConfig().getStringList("races.witch.skills")) {
                data.addSkill(skillId);
            }
            playerDataService.saveAsync(data);
            plugin.getVoiceManager().refreshGrammar(player);
            player.closeInventory();
            player.sendMessage("§d§lRace Witch dipilih! Semua skill Witch sudah terbuka.");
            player.sendMessage("§7Ucapkan mantra skill untuk menggunakannya.");
            return;
        }

        if (plain.startsWith("Skill -")) {
            event.setCancelled(true);
        }
    }
}
