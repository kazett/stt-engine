# STT Engine v3.0.0 — Catatan Proyek

Plugin Paper 1.20.1 (Java 21). Player mengucapkan mantra lewat Simple Voice Chat, dikenali Vosk, lalu skill jalan.
Spesifikasi lengkap (sumber kebenaran): `project.bigupdate.md`. Status audit, bug, dan riwayat: `editlog.md`.

Terakhir diperbarui: 2026-11-06 (sesi 3)

## Ringkasan v3.0.0

- Tidak ada relic. Player memilih ras sekali (permanen) lewat `/stt menu`, semua skill ras langsung terbuka.
- Saat ini hanya ada ras **Witch**: `swap-step`, `black-fire`, `gravity-pull`, `meteor-crash` (semua NATIVE).
- Max mana flat **300** (`settings.max-mana`).
- Vosk: Model dipakai bersama, Recognizer + grammar **per player** (hanya phrase skill miliknya + `[unk]`).
- Skill custom (contoh `meteor-storm`, tipe `MYTHIC_MOBS`) tidak masuk list ras, hanya lewat `/stt addskill`.
  Upgrade = `/stt removeskill` skill lama lalu `/stt addskill` skill baru.
- Resource pack tidak dikirim STT. Aset STT dan pack skill dideploy otomatis ke `plugins/` dan disatukan ItemsAdder lewat `/iazip`.

## Command

| Command | Izin | Fungsi |
|---|---|---|
| `/stt` / `/stt menu` | stt.use | Pilih ras (sekali) / lihat skill |
| `/stt mana` | stt.use | Cek mana |
| `/stt addskill <player> <skill_id>` | stt.admin | Tambah skill (bisa player offline) |
| `/stt removeskill <player> <skill_id>` | stt.admin | Cabut skill (skill ras dicatat di `removed-skills`) |
| `/stt reload` | stt.admin | Reload config dan definisi skill |

## Dependency server

- Wajib: Simple Voice Chat (`voicechat`), model Vosk di `plugins/stt/model/` (harus model small agar grammar dinamis jalan).
- Untuk skill `MYTHIC_MOBS`: MythicMobs + MythicLib (tanpa MMOItems/MMOCore). Keduanya harus aktif; STT memanggil skill lewat MythicMobs API.
- Untuk pack dan HUD: ItemsAdder (dan ModelEngine jika pack memakainya).
- Folder plugin tujuan harus sudah ada di `plugins/` (pernah jalan sekali). Kalau belum ada, deployer skip dan hanya log.

## Struktur repo (flat, cocok untuk upload dari HP)

Semua file ada di root repo, tidak ada folder kecuali `.github/workflows/`.

| File | Isi |
|---|---|
| `*.java` (19 file) | Source. Package dibaca dari baris `package` saat build |
| `java-files.txt` | Daftar `.java` yang dikompilasi. Class baru wajib ditambah di sini |
| `plugin.yml`, `config.yml`, `pom.xml` | Konfigurasi plugin dan Maven |
| `deploy__<Plugin>__<path>__<file>` | Pack skill dan aset STT. Tanda `__` = pemisah folder |
| `.github/workflows/build.yml` | Menyusun source flat jadi struktur Maven, lalu build |
| `project.md` | Catatan ini |
| `project.bigupdate.md` | Spesifikasi v3.0.0 |
| `editlog.md` | Audit, bug, pertanyaan terbuka, riwayat |

### Aturan nama file `deploy__...`

`deploy/MythicMobs/Skills/meteor_skills_mmocore.yml` ditulis `deploy__MythicMobs__Skills__meteor_skills_mmocore.yml`.
Saat build, workflow mengubah `__` kembali menjadi `/` dan menaruhnya di `src/main/resources/deploy/`, jadi isi JAR sama seperti folder asli.
Jangan memakai `__` (dua underscore) di nama file atau folder aslinya. Satu underscore aman.
Menambah pack baru: ubah nama file sesuai aturan ini, upload ke root repo, push. Tidak perlu mengubah kode.

## Build

Otomatis lewat GitHub Actions setiap push (hasil di Artifacts, nama `stt`). Build lokal ada di `.github/workflows/build.yml`.

## Auto-Deploy pack (`AssetsDeployer`)

- Scan seluruh `deploy/` di JAR, mirror ke `plugins/`, tanpa hardcode nama pack.
- Copy hanya sekali: file yang sudah ada tidak ditimpa (edit admin aman).
- Plugin tujuan belum terpasang: skip. File langsung di root folder plugin (stempel lisensi): ditolak.
- Reload hanya jika ada file baru: `meg reload`, lalu `mm reload`. ItemsAdder tetap manual (`/iazip`).

## Pack yang ada saat ini: Meteor

- ItemsAdder namespace `mage` (11 model, 22 texture, 4 suara, `mage_sounds/sounds.json`), MythicMobs (Skills, Mobs, Items), MythicLib (`METEOR`).
- Tidak ada ModelEngine (memang tidak dipakai pack ini).
- Varian yang dideploy: **MMOCORE** (keputusan user). Varian MMOITEMS tidak ikut.
- Skill: MythicMobs `Meteor` ↔ config `meteor-storm` (`mythic-skill-name`). STT hanya memanggil skill; damage, durasi, dan efek diatur di file skill pack (manual).
- Pack memakai `<modifier.damage>` dan `<modifier.duration>` yang tidak terisi jika dipanggil lewat MythicMobs. Ganti dengan angka di `meteor_skills_mmocore.yml` (lihat B18 di `editlog.md`).

## Aset STT sendiri

`deploy/ItemsAdder/contents/stt/...`: font HUD mana (`mana.json`, `mana_bar.png`, `mana_digits.png`) dan `shinra_tensei.ogg` + `sounds.json`.

## Status roadmap (detail di `editlog.md`)

| Phase | Isi | Status |
|---|---|---|
| 1 | Hapus sistem lama + Assets Deployer + baca pack | Selesai |
| 2 | Data player atomik, mana 300, pilih ras, unlock skill | Selesai (ada catatan kecil) |
| 3 | GUI `/stt menu` | Selesai (ras masih hardcode Witch) |
| 4 | Admin command + Vosk per player | Selesai (ada bug urutan paket audio dan kemungkinan masalah sample rate) |
| 5 | Cast skill MythicMobs | Kode selesai (via MythicMobs API, tanpa modifier). **Belum dites in-game**, dan `<modifier.*>` di pack harus diganti angka manual |

## Cara memakai catatan ini

Kalau memberi source baru ke AI: kirim zip source, `project.md`, `project.bigupdate.md`, dan `editlog.md`. Minta AI menambah entri baru di bagian Riwayat `editlog.md` setiap ada perubahan.
