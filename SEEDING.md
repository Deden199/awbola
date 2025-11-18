# Firestore seeding

Aplikasi otomatis men-*seed* konfigurasi awal ke Firestore saat layar `Bolalive` pertama kali dijalankan. Dokumen `app_config/bolalive` akan dibuat jika belum ada sehingga banner, menu, dan URL awal langsung tersedia tanpa input manual.

## Cara kerja
1. Saat aplikasi dibuka, `Bolalive` memanggil `seedFirestoreConfigIfMissing`.
2. Fungsi ini mengecek dokumen `app_config/bolalive` di Firestore.
3. Jika dokumen belum ada, aplikasi mengisi nilai default:
   - `bannerUrls`: tiga URL banner placeholder.
   - `menuUrls`: daftar, login, dan livechat diarahkan ke `https://jalaa35.com/`.
   - `webviewUrl`: URL awal untuk WebView (nilai pertama dari `menuUrls`).
4. Setelah seeding selesai (atau jika dokumen sudah ada), aplikasi memuat konfigurasi terbaru melalui `fetchRemoteConfig`.

## Menjalankan seeding otomatis
1. Pastikan kredensial Firebase sudah benar (`google-services.json` ada di `app/`).
2. Pastikan aturan keamanan Firestore mengizinkan penulisan awal untuk pengguna aplikasi (setidaknya untuk dokumen `app_config/bolalive`).
3. Instal dan jalankan aplikasi di perangkat/emulator yang terhubung.
4. Buka layar utama aplikasi. Jika koleksi kosong, data seed akan dibuat otomatis.
5. Buka Firestore Console atau Emulator untuk memverifikasi dokumen `app_config/bolalive` sudah terisi.

## Menyesuaikan data seed
- Ubah nilai default banner/menu di `Bolalive.kt` (variabel `defaultBannerImages` dan `defaultMenuUrls`).
- Jika ingin menambah field lain, edit map `seedData` di `seedFirestoreConfigIfMissing`.
- Deploy ulang aplikasi lalu buka kembali agar seeding ulang dijalankan untuk instalan baru atau lingkungan Firestore kosong.
