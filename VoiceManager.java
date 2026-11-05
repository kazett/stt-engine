package id.stt.voice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import id.stt.Stt;
import id.stt.player.PlayerDataService;
import id.stt.skill.SkillService;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.vosk.Model;
import org.vosk.Recognizer;

public class VoiceManager implements VoicechatPlugin {

    private static final float SAMPLE_RATE = 16000f;
    private static final Gson GSON = new Gson();

    private final Stt plugin;
    private final Model model;
    private final SkillService skillService;
    private final PlayerDataService playerDataService;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "stt-voice");
        t.setDaemon(true);
        return t;
    });
    private VoicechatApi api;

    private static class PlayerSession {
        final ReentrantLock lock = new ReentrantLock();
        Recognizer recognizer;
        OpusDecoder decoder;
    }

    public VoiceManager(Stt plugin, Model model, SkillService skillService, PlayerDataService playerDataService) {
        this.plugin = plugin;
        this.model = model;
        this.skillService = skillService;
        this.playerDataService = playerDataService;
    }

    @Override
    public String getPluginId() {
        return "stt";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(PlayerDisconnectedEvent.class, e -> remove(e.getPlayerUuid()));
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null || api == null) return;
        UUID id = event.getSenderConnection().getPlayer().getUuid();
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;

        // Only process if player has at least one skill
        if (playerDataService.get(id).getUnlockedSkills().isEmpty()) return;

        byte[] opus = event.getPacket().getOpusEncodedData();
        PlayerSession session = sessions.get(id);
        if (session == null) return; // grammar not ready yet

        pool.execute(() -> {
            session.lock.lock();
            try {
                if (session.recognizer == null || session.decoder == null) return;
                if (opus == null || opus.length == 0) {
                    session.decoder.resetState();
                    handleResult(id, session.recognizer.getFinalResult());
                    return;
                }
                short[] decoded = session.decoder.decode(opus);
                if (decoded == null || decoded.length == 0) return;
                byte[] pcm = toPcm(decoded);
                if (session.recognizer.acceptWaveForm(pcm, pcm.length)) {
                    handleResult(id, session.recognizer.getResult());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Voice process error: " + e.getMessage());
            } finally {
                session.lock.unlock();
            }
        });
    }

    private void handleResult(UUID id, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("text")) return;
            String text = obj.get("text").getAsString();
            if (text == null || text.isBlank()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline() && !player.isDead()) {
                    skillService.tryCastByPhrase(player, text.trim());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Parse Vosk result: " + e.getMessage());
        }
    }

    private static byte[] toPcm(short[] samples) {
        ByteBuffer buf = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : samples) buf.putShort(s);
        return buf.array();
    }

    /** Panggil saat join, unlock skill, atau /stt addskill. */
    public void refreshGrammar(Player player) {
        UUID id = player.getUniqueId();
        List<String> phrases = skillService.getUnlockedPhrases(id);

        if (phrases.isEmpty()) {
            remove(id);
            return;
        }

        List<String> words = new ArrayList<>(phrases);
        words.add("[unk]");
        String grammar = GSON.toJson(words);

        pool.execute(() -> {
            try {
                Recognizer fresh = new Recognizer(model, SAMPLE_RATE, grammar);
                OpusDecoder decoder = api != null ? api.createDecoder() : null;

                PlayerSession session = sessions.computeIfAbsent(id, k -> new PlayerSession());
                session.lock.lock();
                try {
                    if (session.recognizer != null) {
                        try { session.recognizer.close(); } catch (Exception ignored) {}
                    }
                    if (session.decoder != null) {
                        try { session.decoder.close(); } catch (Exception ignored) {}
                    }
                    session.recognizer = fresh;
                    session.decoder = decoder;
                } finally {
                    session.lock.unlock();
                }
                plugin.getLogger().info("Grammar refreshed for " + player.getName() + " (" + phrases.size() + " skills)");
            } catch (Exception e) {
                plugin.getLogger().warning("Gagal build grammar untuk " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    public void remove(UUID id) {
        PlayerSession session = sessions.remove(id);
        if (session == null) return;
        session.lock.lock();
        try {
            if (session.recognizer != null) {
                try { session.recognizer.close(); } catch (Exception ignored) {}
                session.recognizer = null;
            }
            if (session.decoder != null) {
                try { session.decoder.close(); } catch (Exception ignored) {}
                session.decoder = null;
            }
        } finally {
            session.lock.unlock();
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            remove(id);
        }
        pool.shutdownNow();
    }
}
