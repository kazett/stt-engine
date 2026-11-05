package id.stt.deployer;

import id.stt.Stt;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

public class AssetsDeployer {

    private static final String ROOT = "deploy/";
    private final Stt plugin;

    public AssetsDeployer(Stt plugin) {
        this.plugin = plugin;
    }

    public void deployAll() {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        Set<String> changed = new HashSet<>();

        try (JarFile jar = new JarFile(plugin.getPluginFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(ROOT)) continue;

                String rel = name.substring(ROOT.length());
                String[] parts = rel.split("/");
                if (parts.length < 3) continue; // skip license stamp files at plugin root

                String targetPlugin = parts[0];
                if (!new File(pluginsDir, targetPlugin).isDirectory()) continue;

                if (copyResource(name, new File(pluginsDir, rel))) {
                    changed.add(targetPlugin);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal scan folder deploy di JAR: " + e.getMessage());
        }

        if (!changed.isEmpty()) {
            scheduleReload(changed);
        }
    }

    private void scheduleReload(Set<String> changed) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (changed.contains("ModelEngine") && Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "meg reload");
            }
            if (changed.contains("MythicMobs") && Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm reload");
            }
            if (changed.contains("ItemsAdder")) {
                plugin.getLogger().info("Aset baru masuk ke ItemsAdder. Jalankan /iazip untuk build resource pack.");
            }
            if (changed.contains("MythicLib")) {
                plugin.getLogger().info("Skill MythicLib baru masuk. Reload MythicLib atau restart server agar terbaca.");
            }
        }, 20L);
    }

    private boolean copyResource(String resourcePath, File outFile) {
        if (outFile.exists()) return false;
        outFile.getParentFile().mkdirs();
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return false;
            Files.copy(in, outFile.toPath());
            plugin.getLogger().info("Deployed: " + resourcePath);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Gagal menyalin resource: " + resourcePath + " (" + e.getMessage() + ")");
            return false;
        }
    }
}
