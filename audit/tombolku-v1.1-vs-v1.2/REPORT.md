# FORENSIC APK AUDIT — TOMBOLKU v1.1 vs v1.2
Investigasi Google Play Protect Blocking
Tanggal audit: 2026-11-15 · Auditor: AI Security Agent (static analysis, tanpa perangkat fisik)

## 0. Lingkup, Metode, dan Keterbatasan Lingkungan
- Objek: APK rilis resmi GitHub Releases v1.1 (Build #42) dan v1.2 (Build #44), bukan build ulang dari source.
- Lingkungan audit: sandbox Linux ephemeral. Tool tersedia: python3, curl, unzip, sha256sum, diff, git, cryptography, androguard 3.3.5. Tool TIDAK tersedia: Java/JDK, Android SDK Build Tools, apksigner, aapt/aapt2, apkanalyzer, apktool, jadx, dexdump. MCP Context7 tidak tersedia di lingkungan agent; dokumentasi resmi diambil langsung dari developers.google.com / developer.android.com / support.google.com via web.
- Substitusi tool (hasil diverifikasi silang dengan source code rilis):
  - Signing: parser APK Signing Block custom (pure Python) + cryptography (X.509). Setara apksigner verify --print-certs untuk skema v2.
  - Manifest/resource: decoder AXML custom + androguard (pass pertama).
  - DEX: parser DEX custom (class_defs, method_ids, code_item, invoke-*/const-string) — diff per class/method, bukan hanya ukuran.
  - resources.arsc: parser string pool custom.
- Keterbatasan: (1) log CI tidak dapat diunduh tanpa autentikasi (API 403) — provenance dibuktikan lewat cara lain (§2); (2) tidak ada uji perangkat fisik/emulator oleh agent; (3) klasifikasi internal Play Protect tidak dapat diamati langsung.
- Tidak ada perubahan source code, commit, release, atau signing key selama audit.

## 1. Executive Summary
1. Kedua APK terverifikasi SHA-256/ukuran sesuai rilis resmi, dan BYTE-IDENTICAL dengan artifact CI Build #42/#44 (provenance terbukti, §2).
2. Signing identik: skema v2 saja, sertifikat sama (fingerprint de72532d…a55b, RSA-4096, self-signed CN=saputr). Tidak ada perubahan signing.
3. TIDAK ADA permission baru. Satu-satunya perubahan manifest: penambahan TombolkuTileService (BIND_QUICK_SETTINGS_TILE, exported=true, dilindungi permission signature sistem).
4. Kemampuan Accessibility TIDAK berubah: config hasil kompilasi identik (hanya description+summary; tanpa canRetrieveWindowContent, tanpa event types, tanpa canPerformGestures, tanpa isAccessibilityTool). Aplikasi TIDAK bisa membaca isi layar pada kedua versi.
5. Tidak ditemukan kode jaringan, pengumpulan data, akses SMS/lokasi/kamera, refleksi, dynamic loading, atau otomatisasi aplikasi lain pada bytecode kedua versi. Fungsi terbatas: lock screen, power dialog, volume (AudioManager), panel overlay.
6. Perubahan konkret v1.2: FAB permanen dihapus → panel on-demand via Quick Settings Tile (TileService + static bridge + unlockAndRun), requestAddTileService di MainActivity, deskripsi layanan diperbarui, versionCode TETAP 1 / versionName TETAP "1.0" (tidak di-bump — temuan build).
7. Penyebab blokir: pesan yang muncul cocok PERSIS dengan kebijakan resmi Google "App blocked to protect your device" untuk aplikasi sideload internet (browser/file manager) yang mendeklarasikan ACCESSIBILITY (atau SMS/NotificationListener). Aturan terdokumentasi ini berlaku untuk KEDUA versi (keduanya punya AccessibilityService). Perbedaan perilaku v1.1 (minta scan) vs v1.2 (blokir) TIDAK dapat dijelaskan oleh perubahan permission/kemampuan apa pun di APK. Hipotesis paling masuk akal: perubahan reputasi/verdict cloud Play Protect antar waktu instalasi (satu package+sertifikat), dan/atau bobot heuristik atas kombinasi fitur statis baru (QS tile + accessibility overlay + unlockAndRun). Ini BELUM dapat dibuktikan; hanya Google yang bisa memastikan.
8. Tidak ada bukti malware; namun audit ini TIDAK menyimpulkan "false positive" — blokir konsisten dengan kebijakan kategori yang terdokumentasi. Jalur yang sah: appeal resmi, distribusi via Play Store (dengan deklarasi accessibility), atau instalasi adb untuk penggunaan pribadi — tanpa menonaktifkan Play Protect dan tanpa menyamarkan fungsi.

Tingkat keyakinan: TINGGI untuk fakta APK (§2–§6); RENDAH–SEDANG untuk atribusi penyebab blokir (§7).

## 2. APK Verification
| Item | v1.1 | v1.2 |
|---|---|---|
| URL rilis | releases/download/v1.1/Tombolku-v1.1.apk | releases/download/v1.2/Tombolku-v1.2.apk |
| SHA-256 (hitung) | 08c5e78d4f91622a3f99187ddd9d4a1c4d1792326e25badb3a9d552afca91d32 | 4d4c44623894fd952d695c52f829309a4e780a91f35497263b5547ed1abb009b |
| Cocok hash resmi | YA (diverifikasi 3× unduhan independen) | YA |
| Ukuran | 6.216.838 B (cocok) | 6.218.134 B (cocok) |
| Digest aset GitHub API | cocok | cocok |
| Package | com.tombolku.app | com.tombolku.app |
| versionCode / versionName | 1 / "1.0" | 1 / "1.0" (TIDAK di-bump) |
| minSdk / targetSdk | 28 / 34 | 28 / 34 |
| Skema signature | v2 saja (V1 tidak ada, V3 tidak ada, V4 tidak ada) | sama |
| Sertifikat SHA-256 | de72532d741f410a936ae8ccff85a9749516539979df8aa362ff1413e928a55b | identik |
| Sertifikat | CN=saputr, OU=com.tombolku.app, O=TombolKu, L=Jakarta, ST=DKI, C=ID; self-signed; RSA-4096; sha384WithRSAEncryption; serial 0x2b752a1012fb8b55; valid 2026-11-14 → 2126-10-21 | identik |
| Signing block IDs | 0x7109871a (v2), 0x504b4453 (dependency metadata), 0x42726577 (padding) | identik |
| v2 digest algo | 0x0104 (RSASSA-PSS SHA-512) | identik |
| Embed textproto revision | a5b66838c0eba7acaef2ed963fbbcbe672260a5a (= tag v1.1, Build #42) | 691ea44de9a751146a5c494ba77bcc0312262bd5 (= tag v1.2, Build #44) |
| Provenance CI | artifact run 43657574919 → app-release.apk SHA-256 = hash rilis v1.1 | artifact run 43659532155 → app-release.apk SHA-256 = hash rilis v1.2 |

Catatan: v2-only valid untuk minSdk 28 (v2 berlaku API 24+). META-INF tidak berisi file signature JAR (konsisten). Sertifikat sama ≠ karakteristik APK sama — diff lengkap di bawah.

## 3. Manifest Comparison (hasil decode AndroidManifest.xml terkompilasi dari APK)
| Elemen | v1.1 | v1.2 | Relevansi Play Protect |
|---|---|---|---|
| uses-permission | com.tombolku.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (signature; auto dari androidx.core) | SAMA, tidak ada tambahan | TIDAK ada permission baru — bukan pemicu delta |
| permission (deklarasi) | DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION protectionLevel=signature | sama | netral |
| MainActivity | exported=true, MAIN/LAUNCHER | sama | netral |
| TombolkuService | BIND_ACCESSIBILITY_SERVICE, exported=false, intent-filter android.accessibilityservice.AccessibilityService, meta-data @xml/accessibility_service_config | SAMA persis | kategori accessibility sudah ada di v1.1 |
| TombolkuTileService | TIDAK ADA | BARU: exported=true, permission=BIND_QUICK_SETTINGS_TILE (signature sistem), icon=@drawable/ic_power, label=@string/tile_label, intent-filter android.service.quicksettings.TileService | Perubahan kategori/permukaan: hanya SystemUI yang bisa bind; bukan eskalasi permission. Berpotensi relevan sebagai fitur statis baru bagi heuristik (belum terbukti) |
| InitializationProvider (androidx.startup) | exported=false, authorities com.tombolku.app.androidx-startup, metadata emoji2+lifecycle | sama | netral |
| ProfileInstallReceiver (androidx) | sama | sama | netral |
| application attrs | allowBackup=true, supportsRtl=true, extractNativeLibs=false, appComponentFactory=androidx.core.app.CoreComponentFactory | sama (label ref bergeser ID saja) | allowBackup=true catatan hardening (kedua versi) |

Tidak ada provider/receiver/activity/service lain yang berubah. Tidak ada permission sensitif baru (SMS, kontak, lokasi, kamera, mic, storage, SYSTEM_ALERT_WINDOW, bahkan INTERNET TIDAK dideklarasikan di kedua versi).

## 4. Bytecode Comparison (parser DEX custom; diff class/method + referensi API)
Jumlah class: 4414 (v1.1) → 4417 (v1.2). Selisih HANYA class aplikasi:
- Dihapus: TombolkuService$$ExternalSyntheticLambda7, …Lambda8 (lambda FAB).
- Ditambah: TombolkuTileService, TombolkuTileService$Companion, TombolkuTileService$$ExternalSyntheticLambda0 (Runnable unlockAndRun), MainActivity$$ExternalSyntheticLambda1 (listener btnAddTile), MainActivity$$ExternalSyntheticLambda2 (Consumer hasil requestAddTileService).
- 4400+ class library: identik (tidak ada library baru/berubah).

TombolkuService:
- Dihapus: addFabWindow, setupFabDrag, maxFabX/maxFabY, updateFabLayout, saveFabPosition (SharedPreferences fab_x/fab_y), openPanel, positionPanel, closePanel, cleanupWindows, clampInt, dp.
- Ditambah: companion instance/isPanelOpen/requestShowPanel/requestHidePanel; tryShowPanel, tryHidePanel, setupOutsideTap (ACTION_OUTSIDE → tutup panel).
- Tetap: performGlobalAction ×2 (GLOBAL_ACTION_LOCK_SCREEN, GLOBAL_ACTION_POWER_DIALOG — sesuai source), AudioManager.adjustStreamVolume ×2 + setStreamVolume(STREAM_MUSIC,0,FLAG_SHOW_UI), onAccessibilityEvent no-op, onInterrupt no-op.
- Window: konstanta 2032 (TYPE_ACCESSIBILITY_OVERLAY) terverifikasi di bytecode KEDUA versi; flags 0x40008 = FLAG_NOT_FOCUSABLE|FLAG_WATCH_OUTSIDE_TOUCH keduanya; v1.2 gravity=17 (CENTER), panel WRAP_CONTENT.
- Lifecycle: onUnbind/onDestroy → isConnected=false, instance=null, tryHidePanel (cleanup window).

TombolkuTileService (baru): onClick → unlockAndRun { toggle panel via companion; Toast bila service belum connected }; onStartListening/onStopListening kelola instance; Companion.updateTile → Tile.STATE_ACTIVE/INACTIVE + updateTile; onTileAdded log. TIDAK memakai startActivityAndCollapse/PendingIntent.

MainActivity: +requestAddTile → StatusBarManager.requestAddTileService (API 33+, guard SDK_INT), Icon.createWithResource, ContextCompat.getMainExecutor, hasil hanya di-Log; fallback Toast instruksi manual. isServiceEnabled tetap membaca Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES (read-only, teknik standar).

Pemindaian API berbahaya pada seluruh class com.tombolku.app (kedua versi): TIDAK ditemukan java.net/HttpURLConnection/OkHttp, Socket, TelephonyManager, SmsManager, ContentResolver.query (selain Settings.Secure.getString), PackageManager.getInstalled*, LocationManager, Camera, MediaRecorder, takeScreenshot, dispatchGesture, AccessibilityNodeInfo/findAccessibilityNodeInfos/getRootInActiveWindow/getWindows, refleksi, DexClassLoader/PathClassLoader, Runtime.exec. Tidak ada native code (lib/ kosong).

Kesimpulan: perubahan bytecode = refactor UI/entry-point, BUKAN penambahan kemampuan akses data.

## 5. Dependency Comparison
- 31 file META-INF/*.version: IDENTIK seluruhnya (androidx.core:core-ktx 1.13.1, androidx.appcompat:appcompat 1.7.0 + transitive: activity, annotation-experimental, appcompat-resources, arch.core, core, cursoradapter, customview, drawerlayout, emoji2(+views-helper), fragment, interpolator, lifecycle-*, loader, profileinstaller, savedstate, startup-runtime, tracing, vectordrawable(+animated), versionedparcelable, viewpager; kotlinx-coroutines core/android).
- app-metadata.properties: AGP 8.5.2 (kedua). kotlin-tooling-metadata.json: identik (Kotlin 2.0.20, Gradle 8.9).
- Native libraries: TIDAK ADA (kedua). Assets: hanya assets/dexopt/baseline.prof/.profm (androidx.profileinstaller; berubah karena regenerasi per build — wajar). DebugProbesKt.bin: artefak standar kotlinx-coroutines (kedua).
- File ZIP: 439 → 438; satu-satunya file dihapus res/2Y.xml (= overlay_fab.xml). Tidak ada file baru. classes.dex tunggal.
- Resource terkompilasi: setelah normalisasi ID, satu-satunya layout berubah = activity_main (+1 Button btnAddTile); overlay_panel identik; accessibility_service_config identik; tile icon memakai ulang drawable ic_power yang sudah ada di v1.1 (ID 0x7F070065, dipakai FAB v1.1).
- String aplikasi (dari resources.arsc): dihapus subtitle lama, service_desc lama ("tombol melayang…"), fab_desc; ditambah subtitle baru, add_tile, add_tile_manual, tile_label, tile_service_not_ready, service_desc baru ("…panel kontrol…dari ubin Quick Settings."). Deskripsi accessibility yang dilihat pengguna di Settings berubah mengikuti fitur — transparan, bukan penyamaran.

## 6. Source Code Comparison (tag v1.1 a5b66838 → tag v1.2 691ea44d; 2 commit)
Commit: 48796f415d "Fitur: ubin Quick Settings membuka panel, hapus floating button permanen"; 691ea44de9 "Fix: import android.view.View di service, Icon dari android.graphics.drawable".
File berubah (7): AndroidManifest.xml (+10), MainActivity.kt (+31), TombolkuService.kt (+69/−204), TombolkuTileService.kt (baru, 74 baris), activity_main.xml (+9), overlay_fab.xml (dihapus), strings.xml (+6/−3).
TIDAK berubah: app/build.gradle.kts, build.gradle.kts, gradle.properties, gradle-wrapper.properties (Gradle 8.9), .github/workflows/build.yml, accessibility_service_config.xml.
Konfigurasi build (kedua rilis): compileSdk 34, minSdk 28, targetSdk 34, versionCode 1, versionName "1.0", isMinifyEnabled=false, signing release dari env secrets (KEYSTORE_FILE/KEY_ALIAS/KEYSTORE_PASSWORD/KEY_PASSWORD), fail-fast tanpa fallback debug key. Workflow: checkout@v5, setup-java@v5 (temurin 17), decode keystore dari secrets.KEYSTORE_BASE64, assembleRelease, apksigner verify --print-certs, upload-artifact@v6.
Komunikasi antarservice: v1.1 FAB→openPanel internal; v1.2 TileService→static companion bridge→TombolkuService (satu proses, main thread; tanpa IPC/Binder tambahan, tanpa exported component baru selain tile yang diproteksi sistem).
Status Enabled vs Connected: sudah ada di v1.1 (isServiceEnabled + isConnected); v1.2 tidak mengubah logika ini.
Semua perubahan source terkonfirmasi hadir di bytecode APK (§4) — tidak ada kode tersembunyi yang hanya ada di APK.

## 7. Play Protect Risk Assessment
### 7.1 Fakta (terverifikasi dari APK)
- F1: Kedua versi mendeklarasikan AccessibilityService (manifest + config). Config minimal: hanya description+summary; tanpa canRetrieveWindowContent/eventTypes/canPerformGestures/isAccessibilityTool → tidak bisa membaca layar/melakukan gesture.
- F2: Tidak ada permission RECEIVE_SMS/READ_SMS/NOTIFICATION_LISTENER/INTERNET di kedua versi.
- F3: Tidak ada kode akses data sensitif atau jaringan (§4).
- F4: Distribusi via GitHub Releases = unduhan browser = "Internet-sideloading source" menurut Google.
- F5: Perubahan v1.2 terbatas pada entry-point UI (QS Tile), bukan kemampuan akses data.

### 7.2 Dokumentasi resmi Google (referensi kunci)
- "Developer Guidance for Google Play Protect Warnings" (developers.google.com/android/play-protect/warning-dev-guidance): pesan PERSIS "This app can request access to sensitive data. This can increase the risk of identity theft or financial fraud." muncul untuk aplikasi dari internet-sideloading sources yang mendeklarasikan RECEIVE_SMS, READ_SMS, NOTIFICATION_LISTENER, atau ACCESSIBILITY → "Google Play Protect will automatically block the installation". Gambar pada halaman itu diberi caption "deceptive accessibility tool". Contoh accessibility yang Allowed: alat bantu disabilitas, screen reader dengan consent; Disallowed: interaksi dengan aplikasi lain tanpa consent, ekstraksi kredensial. Tersedia jalur appeal.
- Restricted settings (support.google.com/android/answer/12623953): Android 13+ membatasi enable accessibility untuk app sideload sampai user mengizinkan "Allow restricted settings" — mekanisme TERPISAH dari blokir instalasi Play Protect.
- Kebijakan AccessibilityService API Play (support.google.com/googleplay/android-developer/answer/10964491): isAccessibilityTool hanya untuk alat bantu disabilitas; app non-tool wajib prominent disclosure + consent dalam app; deklarasi Play Console. Tombolku (utility pengganti tombol) BUKAN accessibility tool → jangan set isAccessibilityTool=true.

### 7.3 Analisis kausal (dipisahkan tegas)
- Aturan terdokumentasi (sideload internet + ACCESSIBILITY) berlaku untuk v1.1 DAN v1.2. Karena permission/kemampuan accessibility identik, aturan ini sendiri TIDAK menjelaskan mengapa v1.1 hanya diminta scan sedangkan v1.2 diblokir.
- Yang dapat dinafikan dengan bukti sebagai penyebab delta: permission baru (tidak ada), perluasan kemampuan accessibility (tidak ada), perubahan signing (tidak ada), library/kode berbahaya baru (tidak ada), pembacaan layar (tidak bisa di kedua versi).
- Hipotesis yang TERSISA (belum dapat dibuktikan, jangan diklaim sebagai fakta):
  - H1 (paling masuk akal): verdict/reputasi cloud berubah di antara dua instalasi. Instalasi v1.1 memicu real-time scan APK yang belum dikenal; hasil klasifikasi (package com.tombolku.app + sertifikat yang sama) lalu diterapkan saat instalasi v1.2 → blokir. Konsisten dengan sifat Play Protect yang cloud-based dan dengan kebijakan kategori di atas.
  - H2: kombinasi fitur statis baru (AccessibilityService + overlay on-demand + TileService + unlockAndRun + requestAddTileService) menaikkan skor heuristik "deceptive accessibility tool". Mungkin, tetapi tidak ada bukti langsung; komponen-komponen ini sah dan umum.
  - H3: perbedaan waktu rollout kebijakan server-side antara dua instalasi (jarak menit; kurang mungkin namun tidak bisa dikesampingkan).
- Hanya Google yang dapat memastikan: fitur classifier yang dipakai, verdict per-APK/per-sertifikat, dan alasan persis perbedaan v1.1 vs v1.2.

### 7.4 Penilaian risiko keamanan aplikasi
- Risiko nyata rendah: tidak ada jalur eksfiltrasi data (tanpa INTERNET permission pun tidak ada kode jaringan); aksi global terbatas lock/power; volume via AudioManager tanpa permission.
- Catatan hardening (kedua versi, bukan delta): allowBackup=true; versionCode statis; tidak ada prominent disclosure in-app tentang penggunaan accessibility (relevan bila masuk Play Store); label "Mute" sebenarnya hanya men-0-kan STREAM_MUSIC.
- Jangan: menambah isAccessibilityTool=true untuk menghindari peringatan (melanggar kebijakan dan menyamarkan fungsi); menonaktifkan Play Protect; menambah kemampuan accessibility yang tidak perlu.

## 8. Functional Risk Assessment (v1.2, berdasarkan kode — BELUM diuji runtime)
| # | Area | Temuan | Dampak |
|---|---|---|---|
| 1 | Tile→Service | requestShowPanel return false bila service belum connected; Toast panduan | Baik; status tidak palsu |
| 2 | unlockAndRun | Pada keyguard aman, memicu bouncer dulu baru panel | Perilaku sah; panel tidak tampil di atas lockscreen tanpa unlock |
| 3 | Window leak | tryHidePanel dipanggil dari onUnbind/onDestroy; removeView guarded try/catch; panel di-cache | Risiko rendah; perlu uji disable-service saat panel terbuka |
| 4 | isPanelOpen stale | Bila proses mati mendadak, static reset (proses baru) | Konsisten; edge case ringan |
| 5 | updateTile | qsTile null saat tidak listening → guard return | Aman |
| 6 | requestAddTileService | Hasil hanya di-log; tidak ada feedback bila ditolak/sudah ada | UX minor (P2) |
| 7 | Status MainActivity | Enabled dari Settings.Secure; Connected dari flag static | Akurat; setelah force-stop bisa tampil "enabled not connected" sampai service reconnect |
| 8 | Mute | setStreamVolume(STREAM_MUSIC,0) bukan mute ringer | Label bisa menyesatkan (kedua versi) |
| 9 | GLOBAL_ACTION_LOCK_SCREEN | Butuh API 28+ (minSdk 28) | OK |
| 10 | exported=false pada AccessibilityService | Sistem tetap bisa bind (punya BIND permission); terbukti bekerja di v1.1 | Bukan masalah delta |

Tidak ada crash yang dapat dipastikan dari static analysis; keberhasilan build BUKAN bukti fungsi berjalan.

## 9. Recommended Fixes (prioritas)
P0 — proses/kebijakan (tanpa mengubah keamanan):
1. Jangan menonaktifkan Play Protect, jangan menambah isAccessibilityTool=true, jangan menyamarkan fungsi.
2. Ajukan appeal resmi klasifikasi Play Protect (tautan "File an appeal"/appeal pada halaman warning-dev-guidance dan Play Console Help "App verification"), lampirkan: link APK rilis, SHA-256, sertifikat, penjelasan fungsi (lock/power/volume), bukti config accessibility minimal dan tanpa akses data (laporan ini).
3. Untuk penggunaan pribadi: instal via adb (jalur developer, bukan kategori internet-sideloading menurut dokumentasi) — tanpa mengurangi keamanan Android. Untuk distribusi publik jangka panjang: Play Store + deklarasi AccessibilityService API + prominent disclosure.

P1 — penting:
4. Bump versionCode/versionName setiap rilis (saat ini 1/"1.0" di v1.1 dan v1.2); otomatisasi dari tag.
5. Tambahkan prominent disclosure + consent in-app yang menjelaskan penggunaan AccessibilityService (wajib bila ke Play; membantu reviewer Google dan pengguna).
6. Pertahankan config accessibility minimal (jangan tambah canRetrieveWindowContent, eventTypes, canPerformGestures, packageNames).
7. CI auditability: pin versi/SHA actions; cetak `apksigner verify --verbose --print-certs` (skema v1/v2/v3) + sha256 + fingerprint ke job summary; sertakan hash di release notes; buat release otomatis dari artifact build bertag agar rantai provenance terdokumentasi (sudah terbukti byte-identical, tinggal diformalkan).

P2 — peningkatan:
8. Tangani kode hasil requestAddTileService (feedback pengguna: sudah ditambahkan/ditolak).
9. allowBackup=false atau backup rules eksplisit.
10. Perjelas label mute (volume musik) atau sesuaikan perilaku dengan label (jangan menambah permission DND hanya untuk ini).
11. Uji regresi lifecycle (lihat §10); pertimbangkan onTileRemoved untuk cleanup state.

## 10. Verification Plan (setelah perbaikan; GitHub Actions + emulator)
Job CI baru `android-test` (reactivecircus/android-emulator-runner, matriks API 28/30/33/34):
1. Build release signed (workflow existing) → apksigner verify --verbose (harus v2=true) → sha256 dicatat.
2. `adb install -r app-release.apk` (instalasi adb tidak melewati/mematikan Play Protect; hanya bukan jalur browser).
3. Enable service: `adb shell settings put secure enabled_accessibility_services com.tombolku.app/.TombolkuService` + `accessibility_enabled 1`; assert `dumpsys accessibility` menampilkan service connected.
4. API 33+: `adb shell cmd statusbar add-tile com.tombolku.app/.TombolkuTileService`; klik: `cmd statusbar click-tile …`; assert window panel muncul: `dumpsys window windows | grep -i tombolku` (tipe TYPE_ACCESSIBILITY_OVERLAY).
5. Aksi: klik btnLock → `dumpsys power` state off/locked; btnPower → window GlobalActionsDialog muncul; btnVolUp/Down → `dumpsys audio` STREAM_MUSIC berubah; mute → volume 0.
6. Tutup panel: tap luar (input tap di luar bounds) → window hilang; klik tile lagi → toggle.
7. Lifecycle: disable service via settings saat panel terbuka → assert tidak ada window tersisa (no leak); enable lagi → reconnect; force-stop → status MainActivity benar.
8. `adb logcat` dipantau untuk "WindowLeaked", FATAL, AndroidRuntime; StrictMode bila perlu; monkey 500 event untuk stabilitas.
9. Uji perangkat fisik (oleh user, bukan agent): instal dari browser (reproduksi Play Protect), instal via adb, aktifkan restricted settings bila Android 13+, verifikasi semua tombol. Catat: emulator tanpa GMS TIDAK dapat mereproduksi perilaku Play Protect.

Kriteria lulus: semua assert di atas hijau pada matriks API; tidak ada window leak/crash; status Enabled/Connected akurat.

## 11. Final Conclusion (jawaban eksplisit)
1. Perbedaan paling signifikan: penggantian FAB permanen dengan Quick Settings Tile (TombolkuTileService baru + bridge static + panel on-demand terpusat) dan requestAddTileService di MainActivity; FAB + drag + SharedPreferences posisi dihapus.
2. Permission baru: TIDAK ADA. Manifest hanya menambah deklarasi service tile (diproteksi BIND_QUICK_SETTINGS_TILE, permission signature sistem).
3. Perubahan kemampuan Accessibility: TIDAK ADA. Config terkompilasi identik (description+summary saja); hanya teks deskripsi berubah. Aplikasi tidak bisa membaca layar di kedua versi.
4. Perubahan signing: TIDAK ADA. v2-only, sertifikat & fingerprint identik, bahkan signing block metadata identik.
5. Indikasi perubahan yang relevan terhadap Play Protect: secara kategori, kedua versi sama-sama "app sideload + ACCESSIBILITY" yang menurut dokumentasi resmi memang diblokir otomatis. Delta v1.2 yang berpotensi relevan bagi heuristik hanyalah kombinasi fitur statis baru (tile+overlay+unlockAndRun) — belum terbukti.
6. Penyebab paling masuk akal berdasarkan bukti: klasifikasi cloud Play Protect atas kategori "sideloaded accessibility app" yang diterapkan pada package/sertifikat ini pada waktu instalasi v1.2 (setelah v1.1 melewati alur scan), bukan perubahan kemampuan berbahaya di APK. Keyakinan sedang; mekanisme persisnya tidak dapat dibuktikan dari luar.
7. Yang belum bisa dipastikan: fitur/verdict internal classifier Google; apakah H1/H2/H3 yang benar; apakah appeal akan berhasil; perilaku runtime (belum diuji perangkat).
8. Perubahan yang sebaiknya dilakukan pertama: (a) ajukan appeal resmi dengan bukti audit ini; (b) untuk pemakaian pribadi instal via adb; (c) bump versionCode/versionName + formalisasi provenance CI; (d) tambah prominent disclosure in-app; (e) JANGAN menambah kemampuan accessibility, JANGAN set isAccessibilityTool=true, JANGAN menonaktifkan Play Protect.

## Referensi resmi
- https://developers.google.com/android/play-protect/warning-dev-guidance (pesan blokir persis + kategori pemicu + appeal)
- https://support.google.com/android/answer/12623953 (restricted settings)
- https://support.google.com/googleplay/android-developer/answer/10964491 (kebijakan AccessibilityService API & isAccessibilityTool)
- https://support.google.com/googleplay/android-developer/answer/2992033 (app verification & appeal)
- https://developer.android.com/guide/topics/ui/accessibility/service (membuat accessibility service)
- https://developer.android.com/reference/android/service/quicksettings/TileService ; StatusBarManager.requestAddTileService

Catatan artefak: audit dijalankan di sandbox cloud ephemeral (filesystem di-reset berkala oleh platform). File kerja (apk/, out/, github/, src/, diff) sempat dibuat dan dianalisis, namun tidak persisten; seluruh temuan kunci telah disalin ke laporan ini agar dapat dilanjutkan tanpa mengulang. Simpan laporan ini sebagai audit/tombolku-v1.1-vs-v1.2/REPORT.md di lingkungan kerja Anda.
