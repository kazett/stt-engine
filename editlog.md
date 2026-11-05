# Edit Log & Audit STT v3.0.0

Tanggal audit: 2026-11-06
Dasar: `project.bigupdate.md`
Yang diperiksa: `stt-v3-flat-source.zip` (19 file .java, config.yml, plugin.yml, pom.xml, build.yml, 48 file di deploy/)

Catatan metode: audit dilakukan dengan membaca kode dan file pack. Source **belum dikompilasi dan belum dijalankan di server** (sandbox tanpa internet, dependency Maven tidak bisa diunduh). Poin bertanda "perlu dites" butuh uji in-game.

Legenda: ✅ sesuai spesifikasi | ⚠️ ada tapi menyimpang/kurang | ❌ belum ada | 🔴 kritis | 🟠 tinggi | 🟡 sedang | 🟢 kecil

---

## A. Kesesuaian dengan spesifikasi

### Bagian 1–2: Penghapusan sistem lama, resource pack
| Item | Status | Catatan |
|---|---|---|
| Relic, `/stt give relic`, flat 250 mana dihapus | ✅ | Tidak ada sisa (dicek dengan grep) |
| `Pack.java` dihapus | ✅ | Tidak ada file dan tidak ada pemanggil |
| STT tidak mengirim resource pack | ✅ | |
| Aset HUD mana, font, suara di `deploy/ItemsAdder/contents/stt/` | ✅ | Semua file ada. Font valid: 51 glyph bar (102×459 px, 9 px per baris), 10 digit (50×7 px) |
| Suara `shinra_tensei` | ⚠️ | File dan `sounds.json` ada, tapi tidak ada kode yang memainkannya |

### Bagian 3: Auto-Deploy
| Item | Status | Catatan |
|---|---|---|
| `AssetsDeployer` sesuai blueprint | ✅ | Identik dengan spesifikasi, plus log `Deployed:` |
| Scan otomatis, tanpa hardcode nama pack | ✅ | |
| Copy sekali, tidak menimpa | ✅ | |
| `mkdirs` folder induk | ✅ | |
| Skip plugin yang tidak terpasang | ✅ | Skip senyap, tanpa log info (spesifikasi minta "cukup log info") |
| Tolak file di root folder plugin (stempel lisensi) | ✅ | `parts.length < 3`; JAR juga tidak memuat file lisensi |
| Reload hanya jika ada file baru, urutan `meg` lalu `mm` | ✅ | |
| `getPluginFile()` di `Stt` | ✅ | |
| `softdepend` di plugin.yml | ✅ | Plus `depend: [voicechat]` |

### Bagian 3.3: Pembacaan pack (Meteor)
Lihat bagian C.

### Bagian 3.4: MythicLib dan cast skill
Keputusan user (sesi 3): STT hanya engine pemanggil skill lewat voice. Damage, durasi, dan efek diatur manual di file skill pack. STT tidak mengirim modifier dan tidak punya setting damage. Ini menggantikan butir 2, 3, dan 4 di spesifikasi 3.4 (cast via API MythicLib, modifier di config).

| Item | Status | Catatan |
|---|---|---|
| Cast lewat Java API, bukan `dispatchCommand` | ✅ | `BukkitAPIHelper#castSkill(Entity, String)`, signature dicek langsung dari jar MythicMobs 5.8.2 |
| Cast via API MythicLib + modifier | ➖ | Tidak dipakai, sesuai keputusan user |
| Damage per skill di config STT | ➖ | Tidak dipakai, sesuai keputusan user |
| Tidak ada method kosong (stub) | ✅ | `injectModifiers()` sudah dihapus |
| Fallback jika MythicLib/MythicMobs tidak aktif | ✅ | Kedua plugin dicek; salah satu mati = skill `MYTHIC_MOBS` dinonaktifkan + warning. Deteksi diulang saat `/stt reload` |
| Cek dulu baru potong mana | ✅ | Plugin aktif dan nama skill terdaftar di MythicMobs dicek sebelum mana dipotong |
| Pesan ke player + warning console saat gagal | ✅ | |
| Test damage in-game | ❌ | Belum dites. Lihat B18 |

### Bagian 4: Mana dan data player
| Item | Status | Catatan |
|---|---|---|
| Max mana 300 dari `settings.max-mana` | ✅ | |
| Default mana di `PlayerData` | ⚠️ | Hardcode `300.0`, tidak baca config (lihat B12) |
| Warning jika `cost` > `max-mana` | ✅ | Hanya saat startup, tidak saat `/stt reload` |
| Format file `players/<UUID>.yml` | ✅ | Field sama dengan spesifikasi |
| Simpan atomik (`.tmp` lalu rename) | ✅ | Plus fallback non-atomik |
| Simpan async tiap `race`/`unlocked-skills` berubah | ✅ | Lihat bug B8 soal thread-safety |

### Bagian 5: Ras dan unlock
| Item | Status | Catatan |
|---|---|---|
| Pilih ras sekali, cek `race == null` | ✅ | Dicek di GUI. `PlayerData.setRace` sendiri tanpa guard |
| Urutan: set race, unlock semua skill ras, simpan, `refreshGrammar`, pesan | ✅ | |
| Pilihan ras mengikuti `races.*` di config | ⚠️ | GUI hardcode `witch`. Cukup untuk sekarang (hanya Witch), tapi menambah ras berarti ubah kode |
| Sinkron skill ras saat join, hormati `removed-skills` | ✅ | Kodenya ditulis dobel (lihat B13) |
| ID skill yang tidak ada di config diabaikan | ✅ | |
| Skill custom tidak masuk list ras | ✅ | |

### Bagian 6: Struktur `config.yml`
| Item | Status | Catatan |
|---|---|---|
| `settings`, `races`, `skills` | ✅ | |
| Ras `summoner` | ❌ | Hanya contoh di spesifikasi. Keputusan saat ini: hanya Witch |
| `settings.burnout-delay`, `itemsadder-integration` | ⚠️ | Ada di config tapi tidak dipakai kode |
| `meteor-storm`: `mythic-skill-name`, `mythiclib-skill-id` | ✅ | Cocok dengan pack |
| `meteor-storm`: `modifiers`, `mythiclib-skill-id` | ➖ | Dihapus dari config dan kode, sesuai keputusan user (damage diatur di pack) |

### Bagian 7: Command admin dan Vosk
| Item | Status | Catatan |
|---|---|---|
| `addskill` / `removeskill`, permission `stt.admin` | ✅ | |
| Bisa untuk player offline | ✅ | |
| `removeskill` mencatat skill ras di `removed-skills`; `addskill` menghapusnya dari sana | ✅ | |
| Pesan tolak: skill tidak ada, player belum pernah join, sudah/tidak dimiliki | ✅ | |
| Tab-complete (`removeskill` hanya skill milik target) | ⚠️ | Jalan, tapi lihat B9 |
| Warning phrase mirip/awalan | ✅ | Skill tetap ditambahkan |
| Model Vosk dipakai bersama, load sekali | ✅ | |
| 1 Recognizer per player, grammar phrase + `[unk]` | ✅ | |
| Player tanpa skill: tanpa Recognizer, audio diabaikan | ✅ | |
| `refreshGrammar` saat pilih ras, addskill, join; async; hanya player itu | ✅ | |
| Recognizer di-close saat quit | ✅ | Juga saat `PlayerDisconnectedEvent` |
| Swap dilindungi lock per player | ✅ | |
| Cek `unlocked-skills` lagi setelah Vosk mengenali phrase | ✅ | `tryCastByPhrase` |
| Cek model Vosk adalah `vosk-model-small-*` | ❌ | Tidak ada pengecekan atau warning |

---

## B. Daftar bug dan risiko

### 🔴 Kritis
**B1. ✅ Ditutup 2026-11-06.** Stub `injectModifiers()` dihapus. Per keputusan user, STT tidak mengirim modifier; damage diatur di skill pack (lihat B18).

**B2. ✅ Ditutup 2026-11-06.** Method diambil langsung dengan `getMethod("castSkill", Entity.class, String.class)`. Sudah dicek di jar 5.8.2 bahwa overload ini ada (ada 11 overload `castSkill` di jar itu, jadi bug sebelumnya nyata).

**B3. Sample rate audio (perlu dites).**
Recognizer dibuat dengan 16000 Hz, tapi `OpusDecoder` Simple Voice Chat setahu gue menghasilkan PCM 48000 Hz dan kode langsung mengirimnya ke Vosk tanpa resample. Kalau benar, pengenalan suara akan sangat buruk. Belum bisa gue pastikan tanpa menjalankan server. Tes: ucapkan mantra, lihat apakah terdeteksi.

### 🟠 Tinggi
**B4. Urutan paket audio bisa acak.**
`VoiceManager` memakai thread pool 4 thread dan men-submit satu task per paket mikrofon. Paket dari player yang sama bisa jalan paralel dan berebut lock tanpa jaminan urutan, padahal decoder Opus dan Vosk bersifat stateful. Perbaikan: eksekusi berurutan per player (single-thread executor per player, atau proses di thread SVC).

**B5. Baris `Aura` di skill `Meteor` varian MMOCORE.**
`Aura{auraName=Arcane_Missile_Aura;duration=99999;maxStacks=5} @self` menempelkan aura ber-durasi 99999 tick ke caster setiap cast, dan tidak ada skill yang menghapusnya. Menurut spesifikasi, aura justru ciri varian MMOITEMS. Mungkin sisa dari file asli. Spesifikasi melarang mengubah pack diam-diam, jadi belum disentuh. Lihat D4.

**B6. Skill native tidak cek proteksi (di luar spesifikasi, tapi berisiko).**
`Blast.dig` memanggil `breakNaturally` pada radius 5 / kedalaman 10 tanpa cek WorldGuard/claim, dan `Blast.explode`/`GravityPull` melukai semua `LivingEntity` tanpa cek zona no-PvP. Di server publik ini bisa jadi griefing.

**B18. Placeholder `<modifier.*>` di pack tidak akan terisi (tindakan user).**
Di `meteor_skills_mmocore.yml`, `<modifier.damage>` (skill `Meteor_HIT_D`) dan `<modifier.duration>` (skill `Meteor_HIT_D` dan `Meteor_BURN`) hanya terisi bila skill dipanggil lewat MythicLib. Karena STT memanggil lewat MythicMobs, ganti placeholder itu dengan angka di file skill. Contoh: `damage{a=10}` dan `ignite{ticks=100}`. Belum diubah karena angkanya keputusan user.

### 🟡 Sedang
**B7. HUD cooldown dan HUD mana saling timpa.**
Keduanya memakai action bar. Cooldown HUD update tiap 10 tick, mana HUD tiap 20 tick, sehingga saat ada cooldown tampilan berganti-ganti (kedip).

**B8. Thread-safety `PlayerData`.**
`saveAsync` membaca `HashSet` (`unlockedSkills`) dari thread async, sementara thread utama bisa mengubahnya. Potensi `ConcurrentModificationException`. Perbaikan: ambil snapshot di thread utama sebelum async.

**B9. Tab-complete `removeskill`.**
Memanggil `Bukkit.getOfflinePlayer(String)` di thread utama (bisa lookup blocking) dan membuat entri cache untuk nama sembarang yang diketik.

**B10. `/stt reload` tidak lengkap.**
Tidak merefresh grammar player online (phrase yang diubah baru berlaku setelah rejoin), tidak mendeteksi ulang MythicLib/MythicMobs, tidak memvalidasi ulang `cost` vs `max-mana`.

### 🟢 Kecil
- **B11.** GUI tidak membatalkan `InventoryDragEvent` (hanya klik).
- **B12.** `PlayerData` memakai `300.0` hardcode untuk mana awal, bukan `settings.max-mana`.
- **B13.** `syncRaceSkills` menjalankan logika yang sama dua kali (cabang `section == null` lalu `isList`). Hasil benar, kode berlebih.
- **B14.** `Stt.onEnable`: `getConfigurationSection("skills")` bisa NPE jika section hilang; `SttCommand` dibuat dua kali (executor dan tab completer).
- **B15.** Debuff mana habis (Slowness + Weakness saat mana < 0.5) dan regen 1.5 mana per 0.5 detik (= 3 mana/detik) ada di kode tapi tidak ada di spesifikasi. Komentar di kode ("3 mana / 3 sec") salah. Regen tidak bisa diatur lewat config.
- **B16.** HUD mana memakai font custom tanpa fallback sungguhan. `try/catch` tidak pernah kena, jadi player yang belum menerima pack melihat kotak-kotak.
- **B17.** Import tidak terpakai di beberapa file.

---

## C. Audit pack Meteor (checklist 3.3)

**Inventaris:**
- ItemsAdder: `contents/mage/configs/mage/meteor.yml`, 11 model, 22 texture (termasuk varian `_e`), 4 file `.ogg`, `assets/mage_sounds/sounds.json`
- MythicMobs: `Skills/meteor_skills_mmocore.yml`, `Mobs/meteor_mobs.yml`, `Items/meteor_items.yml`
- MythicLib: `skill/meteor.yml`
- ModelEngine: **tidak ada** (dan memang tidak dipakai: tidak ada `Model:` di Mobs)

**Cocok lintas file ✅**
- Skill `Meteor` (MythicMobs) ↔ `METEOR` → `mythicmobs-skill-id: Meteor` (MythicLib) ↔ `mythic-skill-name: "Meteor"` dan `mythiclib-skill-id: "METEOR"` (config STT)
- `projectile mob=Meteor` ada di Mobs; `summon Ground_Crack` ada; `onEnd=Meteor_HIT`, `Meteor_HIT_D`, `Meteor_BURN`, `Meteor_BURN_FX`, `CRACK_REMOVE` semuanya terdefinisi
- Item head Mobs: `meteor`, `groundd_crack_1..6`, `ground_crack_explode_1..3` ada di `Items`. Nama `groundd_` (dua "d") konsisten di Mobs dan Items, jadi bukan error
- Custom model data MythicMobs Items (2258–2266, 2274) sama dengan `model_id` di ItemsAdder
- Sound `mage_sounds:samus.meteor`, `samus.meteor_explosion`, `samus.fire_burn` ada di `sounds.json` dan menunjuk ke 4 `.ogg` yang ada
- 11 item ItemsAdder ↔ 11 model ✅; semua texture yang dirujuk model ada ✅
- Modifier MythicLib `damage` dan `duration` sesuai pemakaian `<modifier.damage>` dan `<modifier.duration>` di skill ✅
- Varian: hanya MMOCORE di `deploy/` ✅

**Temuan**
1. Baris `Aura` di varian MMOCORE (lihat B5). Keputusan user: itu urusan pack, STT tidak peduli. Dibiarkan apa adanya.
2. ✅ `ground_crack_6.json` memakai texture `ground_crack_5` padahal `ground_crack_6.png` ada. Sudah diperbaiki 2026-11-06 (2 baris: `0` dan `particle`) supaya nama model dan texture sama.
3. Skill `Meteor_Tick` tidak dipanggil siapa pun (partikel sudah dari mob `Meteor`). Tidak berbahaya.
4. Bentrok antar pack: hanya ada 1 pack, tidak ada `minecraft/font/default.json`, namespace `mage`, `mage_sounds`, `stt` unik. `model_id` PAPER 2258–2266 dan 2274, COAL 2069 belum bisa dibandingkan karena belum ada pack lain.
5. Pack punya folder `MythicMobs/Items/` yang tidak ada di contoh layout spesifikasi. Deployer generik, jadi tetap ikut ter-deploy.
6. `contents/stt/` tidak punya folder `configs/` (hanya `resourcepack/`). Perlu dites apakah `/iazip` menerima namespace asset-only tanpa warning.

---

## D. Keputusan dan pertanyaan

**Sudah dijawab user (2026-11-06)**
1. Jar yang dikirim adalah MythicMobs Premium 5.8.2-SNAPSHOT (bukan MythicLib). Tidak dibutuhkan lagi karena cast lewat MythicLib tidak dipakai.
2. Tidak ada setting damage di STT. Damage manual di skill MythicMobs.
3. ID skill custom bebas asal tidak bentrok. Dipakai tetap `meteor-storm` (tidak bentrok dengan `meteor-crash`). Boleh diganti kapan saja.
4. STT hanya engine pemanggil skill. Isi pack (damage, durasi, aura) bukan urusan STT.
5. Nama model dan texture harus sama. Diperbaiki.

**Masih terbuka**
- Debuff mana habis dan regen 3 mana/detik (B15): dipertahankan, dijadikan config, atau dihapus?
- Model Vosk yang dipakai (small atau bukan), dan hasil tes suara (B3).
- Mau lanjut perbaiki B4 (urutan paket audio) dan B3 (resample 48 kHz ke 16 kHz)?

---

## E. Riwayat (Edit Log)

### 2026-11-06 — Sesi 4: build gagal karena file lama v2 masih di repo
- Log GitHub Actions: `COMPILATION ERROR`, 43 file dikompilasi padahal source v3 hanya 19. Sisa file v2 masih ada di repo (contoh: `Mana`, `Relic`, `Pack`, `Summons`, `Amaterasu`, `ShinraTense`, `ShockBind`, `BanshoTenin`, `BansoTein`, `FireballSkill`, `BlackFire`, `GravityPull`, `MeteorCrash`). Semua error berasal dari file lama itu, tidak ada error di 19 file v3.
- Perbaikan: `java-files.txt` (daftar 19 file v3) dan `build.yml` hanya mengompilasi file yang ada di daftar itu. File lama di repo diabaikan. Class baru: tambahkan satu baris nama file di `java-files.txt`.
- Opsional: hapus file `.java` lama di repo supaya bersih (tidak wajib untuk build).

### 2026-11-06 — Sesi 3: cast MythicMobs diperbaiki
- `MythicSkillCaster.java`: ditulis ulang. Memanggil `castSkill(Entity, String)` secara eksplisit, `injectModifiers` (stub) dihapus, tambah `skillExists()` dan `detect()` publik. MythicMobs dan MythicLib keduanya harus aktif.
- `SkillService.java`: sebelum mana dipotong, cek MythicMobs aktif dan nama skill terdaftar. `reload()` memanggil `detect()` ulang.
- `SkillDefinition.java`: field `modifiers` dan `mythiclib-skill-id` dihapus.
- `config.yml`: blok `meteor-storm` disederhanakan (tanpa `mythiclib-skill-id`), komentar diperbarui.
- `deploy__ItemsAdder__contents__mage__resourcepack__assets__mage__models__mage_model__ground_crack_6.json`: texture diganti ke `ground_crack_6`.
- Bug ditutup: B1, B2. Temuan pack C2 ditutup.
- Perlu dites: skill `Meteor` benar-benar berjalan lewat voice; ganti `<modifier.*>` di pack dulu (B18). Catatan: file diperiksa dengan membaca kode, belum dikompilasi (tidak ada javac/dependency Paper di sandbox), jadi build GitHub Actions adalah kompilasi pertamanya.

### 2026-11-06 — Sesi 2: audit
- Membaca seluruh source dan pack, dibandingkan dengan `project.bigupdate.md`.
- Menulis `project.md` baru, menyimpan spesifikasi sebagai `project.bigupdate.md`, membuat `editlog.md` ini.
- **Tidak ada perubahan pada kode Java, config.yml, atau isi pack.**

### 2026-11-06 — Sesi 1: source dibuat flat
- Folder `deploy/` diratakan menjadi file `deploy__<Plugin>__<path>__<file>` (48 file) agar bisa diupload dari HP.
- `.github/workflows/build.yml` diubah: menyusun kembali `deploy__*` menjadi `deploy/...` sebelum build. Folder `deploy/` asli (jika ada) tetap ikut. Hasil rekonstruksi sudah dicek identik dengan folder asli. `AssetsDeployer.java` tidak diubah.

### Format entri berikutnya
```
### YYYY-MM-DD — judul singkat
- Diubah: file/fungsi
- Alasan:
- Bug yang ditutup: (B-nomor)
- Perlu dites:
```
