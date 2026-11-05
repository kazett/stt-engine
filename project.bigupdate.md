# STT Engine - Project Update & Blueprint System (v3.0.0)

Dokumen ini adalah spesifikasi FINAL arsitektur baru plugin **STT (Speech-To-Text Skill Engine)**. Semua sistem lama (v2.3.6) yang berbasis relic item DIHAPUS dan digantikan oleh sistem di dokumen ini. Fokus: **Roleplay Race System**, **Dynamic Voice Engine**, **Single Master Resource Pack via ItemsAdder**, serta **Auto-Inject MythicMobs Skill Pack**.

> **Instruksi untuk AI builder:** Ikuti dokumen ini sebagai satu-satunya sumber kebenaran. Hapus semua kode relic, `/stt give relic`, flat 250 mana, dan `Pack.java`. Baca file skill yang dikirim bersama source, cocokkan nama/ID skill dari isi file itu sendiri, lalu daftarkan ke `config.yml`. Jangan ada method kosong (stub) di hasil akhir.
>
> **PENTING - Pack skill berbeda-beda:** Struktur folder semua pack SAMA (mirror folder `plugins/`), tetapi **nama file YML, nama skill, dan isi folder BERBEDA di tiap pack** (ada pack yang punya folder ModelEngine, ada yang tidak). Dokumen ini sengaja TIDAK menyebut nama file pack. **Baca dan cek teliti nama serta isi setiap file di pack yang dikirim user. Jangan menyamakan satu pack dengan pack lain dan jangan menebak nama.** Lihat Bagian 3.3.

---

## 1. Ringkasan Perubahan Konsep

| Fitur | Sistem Lama (v2.3.6) - DIHAPUS | Sistem Baru (v3.0.0) |
|---|---|---|
| **Pemicu Akses Skill** | Relic Item di Inventory | Player memilih ras (permanen), **semua skill ras langsung terbuka**. Tanpa level dan tanpa quest |
| **Menu** | - | `/stt menu`: pilih ras (sekali) dan lihat daftar skill |
| **Max Mana** | Flat 250 untuk semua player | **Flat 300** untuk semua player dari awal (`settings.max-mana`) |
| **Ganti Ras** | - | **Tidak bisa.** Pilih 1x, permanen |
| **Pengiriman Resource Pack** | Kirim URL GitHub dari `Pack.java` | **Dihapus dari STT.** Di-export ke ItemsAdder (`/iazip`) sebagai Master Pack |
| **Penyedia Skill** | Hardcoded Java Class (`Thunder.java`, dll) | Hybrid: Native Skill + MythicMobs Skill (RP Custom Skill) |
| **Instalasi Skill Pack** | Pasang manual ke MythicMobs | **Auto-Deploy dari JAR** saat server start (scan otomatis, mirror folder `plugins/`, hanya sekali) |
| **Fitur Admin RP** | Command `/stt give relic` | `/stt addskill <player> <skill_id>` dan `/stt removeskill <player> <skill_id>` |
| **Vosk Grammar** | Satu grammar untuk semua | **Per player**: hanya skill yang dimiliki player. Auto-refresh saat pilih ras atau `/stt addskill`, tanpa restart |

---

## 2. Master Resource Pack System

### A. Non-Aktifkan Internal Resource Pack Sender (`Pack.java`)
Minecraft Client hanya bisa mengaktifkan **1 Resource Pack** dari server. Pengiriman pack bawaan STT dinonaktifkan agar tidak menimpa pack plugin lain. Hapus `Pack.java` dan semua pemanggilnya.

### B. Single Master Resource Pack via ItemsAdder
ItemsAdder adalah **Master Controller** pengirim Resource Pack.
* STT tidak mengirim download prompt sendiri.
* Saat `onEnable`, STT mengeluarkan file HUD Mana (`.png`), font metadata, dan audio (`.ogg`) ke `plugins/ItemsAdder/contents/stt/`.
* Admin cukup menjalankan `/iazip` (`meg reload` dan `mm reload` jalan otomatis jika ada file baru). ItemsAdder menyatukan: asset ModelEngine, asset Custom Skill Pack, dan asset HUD Mana + Voice Sound STT.

---

## 3. Sistem Auto-Deploy Pack (Mirror Folder `plugins/`)

Semua pack skill punya struktur folder yang SAMA, yaitu persis mirror isi folder `plugins/` server. Contoh bentuknya (nama di bawah hanya placeholder, nama asli beda tiap pack):

```text
ItemsAdder/contents/<namespace>/configs/...yml
ItemsAdder/contents/<namespace>/resourcepack/assets/...   (models, textures, sounds, font)
ModelEngine/blueprints/<nama_pack>/*.bbmodel              (tidak semua pack punya)
MythicMobs/Mobs/*.yml
MythicMobs/Skills/*.yml
MythicLib/skill/*.yml                                     (tidak semua pack punya)
```

### 3.1 Layout di dalam JAR

Pack ditaruh apa adanya di folder `deploy/` pada JAR. Isinya otomatis di-mirror ke `plugins/`. Aset STT sendiri (HUD mana, suara) memakai format yang sama, yaitu `deploy/ItemsAdder/contents/stt/...`.

```text
[stt.jar]
 └── deploy/
      ├── ItemsAdder/...   ──► plugins/ItemsAdder/...
      ├── ModelEngine/...  ──► plugins/ModelEngine/...
      ├── MythicMobs/...   ──► plugins/MythicMobs/...
      └── MythicLib/...    ──► plugins/MythicLib/...
```

### 3.2 Aturan Wajib

1. **Scan otomatis:** seluruh isi `deploy/` dibaca otomatis. **Dilarang** hardcode nama file atau folder pack. Menambah pack = taruh di `deploy/` lalu build ulang.
2. **Copy hanya sekali:** jika file tujuan sudah ada, **SKIP** (jangan timpa). Edit admin aman. File baru dari update plugin tetap masuk karena belum ada.
3. **Buat folder induk otomatis** (`mkdirs`) sebelum copy.
4. **Hanya deploy ke plugin yang terpasang:** jika `plugins/<NamaPlugin>` tidak ada, skip folder itu dan cukup log info (bukan error).
5. **Jangan copy file yang langsung berada di root folder plugin** (contoh: file berekstensi hex di `ItemsAdder/`). Itu stempel lisensi pembeli (berisi nama dan ID user), bukan bagian pack. Jangan dimasukkan ke JAR, dan deployer menolaknya sebagai pengaman.
6. **Reload hanya jika ada file baru**, bukan tiap server start. Urutan: `meg reload` lalu `mm reload`. ItemsAdder tetap manual: admin menjalankan `/iazip`.
7. Tambahkan di `plugin.yml`: `softdepend: [ItemsAdder, ModelEngine, MythicMobs, MythicLib]` supaya deploy jalan setelah plugin-plugin itu siap.

### Implementasi `AssetsDeployer.java`

```java
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

                String rel = name.substring(ROOT.length());   // contoh: MythicMobs/Skills/x.yml
                String[] parts = rel.split("/");
                if (parts.length < 3) continue;               // file di root plugin (stempel lisensi): skip

                String targetPlugin = parts[0];
                if (!new File(pluginsDir, targetPlugin).isDirectory()) continue; // plugin tidak terpasang

                if (copyResource(name, new File(pluginsDir, rel))) changed.add(targetPlugin);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Gagal scan folder deploy di JAR: " + e.getMessage());
        }

        if (!changed.isEmpty()) scheduleReload(changed);
    }

    private void scheduleReload(Set<String> changed) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (changed.contains("ModelEngine") && Bukkit.getPluginManager().isPluginEnabled("ModelEngine"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "meg reload");
            if (changed.contains("MythicMobs") && Bukkit.getPluginManager().isPluginEnabled("MythicMobs"))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm reload");
            if (changed.contains("ItemsAdder"))
                plugin.getLogger().info("Aset baru masuk ke ItemsAdder. Jalankan /iazip untuk build resource pack.");
            if (changed.contains("MythicLib"))
                plugin.getLogger().info("Skill MythicLib baru masuk. Reload MythicLib atau restart server agar terbaca.");
        }, 20L);
    }

    /** Copy 1 file. Skip jika sudah ada. Return true jika benar-benar dicopy. */
    private boolean copyResource(String resourcePath, File outFile) {
        if (outFile.exists()) return false;          // jangan timpa edit admin
        outFile.getParentFile().mkdirs();            // buat folder induk
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return false;
            Files.copy(in, outFile.toPath());
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Gagal menyalin resource: " + resourcePath + " (" + e.getMessage() + ")");
            return false;
        }
    }
}
```

Tambahkan di class utama `Stt`:

```java
public File getPluginFile() { return getFile(); } // getFile() bersifat protected di JavaPlugin
```

### 3.3 Checklist Wajib AI Builder Saat Membaca Pack

Pack yang dikirim user bisa berubah-ubah. Sebelum membuat atau mengubah kode atau `config.yml`, lakukan ini untuk TIAP pack:

1. **Inventaris yang benar-benar ada.** Daftar semua folder dan file di pack. Folder yang tidak ada (misal `ModelEngine/`) berarti pack itu tidak memakainya. Jangan dibuat atau diasumsikan.
2. **Cocokkan ID lintas file** (jika file-nya ada di pack itu):
   * ID skill di `MythicMobs/Skills/*.yml` harus sama dengan `mythic-skill-name` di `config.yml` STT dan `mythicmobs-skill-id` di `MythicLib/skill/*.yml`.
   * Nama model yang dipanggil di `MythicMobs/Mobs/*.yml` harus ada sebagai file di `ModelEngine/blueprints/`.
   * Nama sound di `sounds.json` dan di skill harus cocok dengan file `.ogg`. Namespace item ItemsAdder harus cocok dengan folder `resourcepack/assets/`.
3. **Varian file dengan ID skill yang sama.** Satu pack bisa punya beberapa varian file skill yang isinya memakai nama skill yang sama (contoh: varian `MMOCORE` dan `MMOITEMS`). **Jangan deploy keduanya**, karena ID duplikat saling menimpa atau bikin error saat load. Baca isi tiap varian dan pilih satu. **Keputusan user: pakai varian `MMOCORE`** (satu mantra = satu skill langsung jalan; varian `MMOITEMS` memakai aura, cek `crouching`, dan `<modifier.skillcooldown>` yang cocok untuk item, bukan voice). Hanya varian itu yang ditaruh di `deploy/`. Varian ini TIDAK butuh plugin MMOCore atau MMOItems, tapi tetap butuh MythicLib (lihat 3.4). Aturan yang sama berlaku untuk pack lain: baca isinya, jangan menebak dari nama varian.
4. **Bentrok antar pack.** Cek `assets/minecraft/font/default.json`, `model_id` (custom model data) yang sama pada material yang sama, dan namespace atau nama sound yang sama. Kalau bentrok, laporkan ke user. Jangan timpa diam-diam.
5. **Daftarkan ke `config.yml`** hanya skill yang benar-benar ada di pack (type `MYTHIC_MOBS`, `phrase`, `cost`, `cooldown`, `modifiers`). Jika `phrase` mantra belum ditentukan user, tanya, jangan dikarang.

### 3.4 Dependency MythicLib & Cara Cast Skill MythicMobs

User **tidak** memakai plugin MMOItems/MMOCore. Namun skill pack ini memakai fitur MythicLib: kondisi `mmoCanTarget` (12 kali) dan placeholder `<modifier.damage>` (8 kali), plus file `MythicLib/skill/*.yml` yang mendefinisikan `modifiers`. Karena itu:

1. **Server wajib memasang MythicLib saja** (tanpa MMOItems dan tanpa MMOCore). Asumsi user: semua pack sudah membawa komponen MythicLib (folder `MythicLib/skill/*.yml`). **Tetap wajib dicek per pack:** apakah folder itu benar-benar ada, apakah isinya cocok dengan ID skill di `MythicMobs/Skills/*.yml`, dan apakah skill pack memakai `mmo*` atau `<modifier.*>`. Jangan menganggap semua pack sama. Jika ada pack yang tidak lengkap, laporkan ke user, jangan ditebak.
2. **Cast lewat Java API MythicLib, BUKAN `dispatchCommand("mm ...")`.** Command console lebih rapuh (nama command beda antar versi, tidak membawa modifier). Dengan MythicLib, `damage` dikirim sebagai modifier sehingga `<modifier.damage>` terisi.
3. **Damage per skill diatur di `config.yml` STT** lewat `modifiers` (lihat Bagian 6), bukan diedit di YML pack.
4. **AI builder wajib membaca API MythicLib versi yang terpasang** (javadoc atau jar) sebelum menulis kode cast. Jangan menebak nama method.
5. **Fallback aman:** jika MythicLib atau MythicMobs tidak aktif saat startup, skill bertipe `MYTHIC_MOBS` dinonaktifkan dengan log warning yang jelas. Skill `NATIVE` tetap jalan, plugin tidak crash.
6. **Cek dulu, baru potong mana.** Sebelum memotong mana dan memulai cooldown, pastikan skill benar-benar bisa dicast (MythicLib aktif dan ID skill ditemukan). Jika gagal, kirim pesan singkat ke player, log warning ke console, dan **mana serta cooldown tidak berubah**.
7. **Test wajib sebelum rilis:** cast satu skill pack lewat voice dan pastikan damage benar-benar masuk (bukan 0) dan target yang dilarang (misal zona no-PvP) tidak kena.

---

## 4. Sistem Mana

* **Max Mana flat 300 untuk semua player dari awal.** Tidak ada level, tidak ada quest, tidak ada kenaikan bertahap. Nilainya di `settings.max-mana`.
* Skill custom/admin tidak mengubah mana sama sekali.
* Hapus semua kode flat 250 lama dan kalkulasi mana berbasis jumlah skill (jangan dibuat).
* Saat `config.yml` di-load, beri **warning di console** jika ada skill dengan `cost` lebih besar dari `max-mana` (tidak akan ada player yang bisa cast skill itu).

### Data Player Storage (`plugins/stt/players/<UUID>.yml`)

```yaml
uuid: "12345678-abcd-1234-abcd-123456789abc"
name: "Player1"
race: "witch"        # PERMANEN. Hanya boleh diset jika masih null.
current-mana: 125.0
unlocked-skills:     # skill ras (otomatis saat pilih ras) + skill custom dari /stt addskill
  - fireball
  - shock-bind
  - black-fire
  - meteor            # skill custom hasil upgrade, diberikan admin lewat /stt addskill
removed-skills:       # skill ras yang dicabut admin lewat /stt removeskill (jangan di-grant ulang)
  - meteor-crash
```

Penyimpanan harus aman dari crash: tulis ke file sementara (`<UUID>.yml.tmp`) lalu rename menimpa file asli (atomik), dan simpan langsung (async) setiap kali `race` atau `unlocked-skills` berubah, jangan hanya menunggu autosave.

---

## 5. Aturan Ras & Unlock Skill

* Player baru **belum punya ras dan belum punya skill apa pun** (disebut "human"). Voice belum memproses apa-apa untuk player ini.
* Pilih ras lewat `/stt menu`. Setelah dipilih, ras **terkunci permanen**. Tidak ada command ganti ras untuk player.
* Sebelum menyimpan pilihan: cek `race == null`. Jika sudah terisi, tolak.
* **Saat ras dipilih, otomatis berurutan:**
  1. Set `race`.
  2. Tambahkan **semua skill di list ras itu** ke `unlocked-skills`.
  3. Simpan file player (atomik).
  4. Panggil `VoiceManager.refreshGrammar(player)` supaya Vosk langsung memuat mantra skill ras itu, tanpa restart.
  5. Kirim pesan ke player.
* **Sinkron saat join:** setelah data player dimuat, pastikan `unlocked-skills` memuat semua skill dari list ras player, **kecuali skill yang tercatat di `removed-skills`**. Jika ada skill baru yang ditambahkan admin ke list ras, player lama otomatis mendapatkannya, lalu panggil `refreshGrammar`. ID skill di `unlocked-skills` yang sudah tidak ada di `config.yml` diabaikan, jangan error.
* Skill custom/admin **tidak** dimasukkan ke list `skills` ras manapun. Hanya diberikan lewat `/stt addskill`.

---

## 6. Struktur `config.yml`

```yaml
# Pengaturan Plugin STT v3.0.0

settings:
  max-mana: 300          # Max Mana semua player
  mana-regen-delay: 3
  burnout-delay: 8
  itemsadder-integration: true

# Definisi Ras. Semua skill di list otomatis terbuka saat player memilih ras tsb
races:
  witch:
    display-name: "&dRace Witch"
    skills:
      - fireball
      - shock-bind
      - meteor-crash
      - black-fire

  summoner:
    display-name: "&aRace Summoner"
    skills:
      - summon-zombie
      - summon-skeleton
      - summon-golem
      - summon-warden

# Definisi Seluruh Skill (nilai di bawah hanya contoh, sesuaikan dengan isi pack/engine)
skills:
  fireball:
    name: "Fire Ball"
    phrase: "fire ball"
    cost: 20
    cooldown: 6
    type: NATIVE

  meteor-crash:
    name: "Meteor Crash"
    phrase: "meteor crash"
    cost: 80
    cooldown: 20
    type: NATIVE

  # Skill custom: TIDAK dimasukkan ke list skills ras manapun, hanya via /stt addskill
  meteor_awakened:
    name: "Awakened Meteor"
    phrase: "meteor awakened"
    cost: 150          # contoh saja, bukan nilai final
    cooldown: 30
    type: MYTHIC_MOBS
    mythic-skill-name: "MeteorAwakenedVfx"
    mythiclib-skill-id: "METEOR_AWAKENED"   # ID di MythicLib/skill/*.yml (contoh, sesuaikan dengan isi pack)
    modifiers:
      damage: 25                            # mengisi <modifier.damage> di skill MythicMobs
```

---

## 7. Alur Command RP Admin & Vosk Grammar Dinamis

### A. Alur Otomatis Pilih Ras dan `/stt addskill`

```
[Player human] ──► /stt menu ──► pilih ras (race == null?)          [Admin] ──► /stt addskill <Player> <SkillID>
                                   │                                                     │
                                   ▼                                                     ▼
              [Set race + unlock semua skill ras]                          [Tambah skill ke unlocked-skills]
                                   │                                                     │
                                   └──────────────────────┬──────────────────────────────┘
                                                          ▼
                                       [Simpan Player Data YAML (atomik)]
                                                          │
                                                          ▼
                              [VoiceManager.refreshGrammar(player), async, hanya player ini]
                                                          │
                                                          ▼
                       [Player Langsung Bisa Ucapkan Mantra, tanpa restart server]
```

### B. Aturan Vosk Per Player

* `Model` Vosk di-load **1x** dan dipakai bersama semua player (mahal di RAM, jangan di-load per player).
* Tiap player yang punya minimal 1 skill (setelah pilih ras) punya 1 `Recognizer` sendiri. Grammar-nya **hanya berisi phrase skill milik player itu** ditambah `"[unk]"` (supaya kata lain ditolak).
* Player human (belum pilih ras dan belum punya skill): **tidak ada Recognizer**, audio tidak diproses.
* Saat player **memilih ras**, saat `/stt addskill`, dan saat player join (setelah sinkron skill ras), panggil `refreshGrammar(player)`. Recognizer baru dibuat async, lalu ditukar. **Hanya player itu yang terpengaruh, tanpa restart.**
* Saat player quit, `Recognizer` di-close agar tidak bocor memori.
* Grammar dinamis hanya jalan di model Vosk kecil (`vosk-model-small-*`). Model besar dengan rescoring tidak mendukung grammar, jadi cek model yang dipakai.

### C. Kerangka `VoiceManager.java`

Recognizer Vosk adalah native object. Menutup recognizer saat thread audio masih memakainya bisa crash JVM, jadi swap dilindungi lock per player.

```java
public class VoiceManager {

    private static final float SAMPLE_RATE = 16000f;

    private final Model model; // shared, load sekali
    private final Map<UUID, PlayerRecognizer> recognizers = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    private static class PlayerRecognizer {
        final ReentrantLock lock = new ReentrantLock();
        Recognizer recognizer;
    }

    /** Panggil saat join, unlock skill, atau /stt addskill. */
    public void refreshGrammar(Player player) {
        UUID id = player.getUniqueId();
        List<String> phrases = skillService.getUnlockedPhrases(id); // phrase skill milik player saja

        if (phrases.isEmpty()) { remove(id); return; }

        List<String> words = new ArrayList<>(phrases);
        words.add("[unk]");
        String grammar = new Gson().toJson(words);

        pool.submit(() -> {
            try {
                Recognizer fresh = new Recognizer(model, SAMPLE_RATE, grammar);
                PlayerRecognizer pr = recognizers.computeIfAbsent(id, k -> new PlayerRecognizer());
                Recognizer old;
                pr.lock.lock();
                try { old = pr.recognizer; pr.recognizer = fresh; }
                finally { pr.lock.unlock(); }
                if (old != null) old.close();
            } catch (IOException e) {
                plugin.getLogger().warning("Gagal build grammar untuk " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /** Dipanggil thread audio. Return teks hasil, atau null jika player belum punya recognizer. */
    public String process(UUID id, byte[] pcm) {
        PlayerRecognizer pr = recognizers.get(id);
        if (pr == null) return null;
        pr.lock.lock();
        try {
            if (pr.recognizer == null) return null;
            return pr.recognizer.acceptWaveForm(pcm, pcm.length) ? pr.recognizer.getResult() : null;
        } finally { pr.lock.unlock(); }
    }

    public void remove(UUID id) {
        PlayerRecognizer pr = recognizers.remove(id);
        if (pr == null) return;
        pr.lock.lock();
        try { if (pr.recognizer != null) pr.recognizer.close(); pr.recognizer = null; }
        finally { pr.lock.unlock(); }
    }
}
```

Setelah Vosk mengenali phrase, STT **tetap cek** apakah skill itu ada di `unlocked-skills` player sebelum dieksekusi (pengaman kedua).

### D. Command Admin (permission `stt.admin`)

| Command | Fungsi |
|---|---|
| `/stt addskill <player> <skill_id>` | Tambah skill ke `unlocked-skills`. Jika skill itu ada di `removed-skills`, hapus dari sana. Lalu simpan dan `refreshGrammar`. |
| `/stt removeskill <player> <skill_id>` | Hapus skill dari `unlocked-skills`. **Jika skill itu bagian dari list ras player, catat di `removed-skills`** supaya sinkron saat join tidak memberikannya lagi. Lalu simpan dan `refreshGrammar`. |

* Contoh alur upgrade: admin menjalankan `/stt removeskill Player1 meteor-crash`, lalu `/stt addskill Player1 meteor`.
* Kedua command harus bisa dipakai pada **player offline** (ubah file player). Jika player sedang online, perubahan langsung berlaku dan grammar dibangun ulang saat itu juga. Jika offline, grammar dibangun saat player join.
* Tolak dengan pesan jelas jika `skill_id` tidak ada di `config.yml`, jika player belum pernah join, atau jika skill sudah dimiliki (add) atau tidak dimiliki (remove).
* Tab-complete: `addskill` menampilkan semua skill di config, `removeskill` hanya skill yang dimiliki player itu.
* Karena grammar dibuat per player, dua skill dengan phrase mirip (misal `meteor` dan `meteor crash`) hanya bentrok jika dimiliki player yang sama. Jika admin menjalankan `addskill` dan phrase-nya sama persis atau menjadi awalan dari phrase skill lain milik player itu, tampilkan **warning ke admin** (skill tetap ditambahkan).

---

## 8. Roadmap Pengerjaan

1. **Phase 1: Hapus sistem lama & Assets Deployer**
   * Hapus `Pack.java`, relic item, `/stt give relic`, flat 250 mana.
   * Buat `AssetsDeployer.java` (scan `deploy/` di JAR, mirror ke `plugins/`, copy sekali, tanpa timpa).
   * Baca tiap pack sesuai checklist Bagian 3.3 dan dependency Bagian 3.4.
2. **Phase 2: Data Player, Ras, Mana**
   * `PlayerData.java` dengan penyimpanan atomik, max mana flat 300, pilih ras permanen dan unlock semua skill ras (Bagian 4 dan 5).
3. **Phase 3: GUI `/stt menu`**
   * Pemilihan ras (sekali, terkunci) dan daftar skill milik player.
4. **Phase 4: Admin RP Skill Command & Vosk per Player**
   * `/stt addskill <player> <skill_id>` dan `/stt removeskill <player> <skill_id>` (Bagian 7D).
   * `VoiceManager` per player: grammar hanya skill milik player, auto-refresh saat pilih ras, `/stt addskill`, dan join, tanpa restart (Bagian 7).
5. **Phase 5: Cast skill MythicMobs via MythicLib** sesuai Bagian 3.4, lalu test damage.
