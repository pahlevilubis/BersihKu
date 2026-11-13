# BersihKu — Pembersih Memori HP Android

Dibuat oleh **Pahlevi Lubis**.

Aplikasi sederhana untuk menganalisa dan membersihkan memori HP (dibuat untuk Samsung Galaxy S21 Ultra,
jalan juga di HP Android 10 ke atas). Semua pemeriksaan dilakukan di HP sendiri — **tidak memakai internet**.

## Fitur

- **Memori HP sekilas** — berapa yang terpakai, sisa berapa, dan *apa yang makan memori*
  (Foto, Video, Musik, Dokumen, APK, Arsip, Aplikasi & sistem). Ketuk salah satu untuk melihat file-nya.
- **Periksa HP** (satu tombol) — hasilnya dikelompokkan:
  - **File sampah** — thumbnail galeri, file sementara, log, download gagal *(aman)*
  - **Isi tempat sampah Galeri / Google Foto** yang masih makan memori *(aman)*
  - **File kembar** — isi sama persis, 1 salinan tetap disimpan *(aman)*
  - **File installer APK** *(aman)*
  - **Foto & video WhatsApp** — bisa difilter: Foto, Video, Dokumen, Voice note, Status, Stiker
  - **File Telegram**, **File besar (50 MB+)**, **Screenshot & rekaman layar**, **Download lama (> 3 bulan)**
- **Bersihkan Sekarang** — sekali tekan untuk semua yang aman.
- **Folder paling besar** — jelajahi folder dari yang paling besar, lengkap dengan persentasenya.
- **Aplikasi jarang dipakai** — daftar aplikasi yang lama tidak dibuka + ukurannya; hapus beberapa sekaligus,
  atau ketuk ⚙ untuk hapus cache lewat Pengaturan.
- **Tempat Sampah BersihKu** — file foto/video/dokumen yang dihapus disimpan dulu 7 hari, bisa dikembalikan.
- **Pengaman** — folder utama (DCIM, Download, Android, dll) dan cadangan chat WhatsApp tidak bisa dihapus.

## Build lewat GitHub

Setiap kali kode di-push, GitHub otomatis membuat APK (tab **Actions**, ±5–8 menit).
APK-nya bisa di-download dari halaman **Releases** (paling gampang dari HP) atau dari **Artifacts** di hasil build.

## Install di HP Samsung

1. Buka halaman **Releases** repo ini dari HP → download **BersihKu.apk**.
2. Buka file-nya → kalau muncul peringatan, izinkan **Instal dari sumber tidak dikenal** untuk browser/My Files.
3. Buka BersihKu → tekan **Beri Izin** → nyalakan **Izinkan akses untuk mengelola semua file** → tekan Kembali.
4. Tekan **Periksa HP Sekarang**.
5. Untuk menu *Aplikasi jarang dipakai*: tekan **Beri Izin** → pilih BersihKu → nyalakan **Izinkan akses penggunaan**.

Update versi baru bisa langsung ditimpa (tanda tangan APK selalu sama).

## Catatan

- Cache & data aplikasi lain (folder `Android/data`) tidak bisa dihapus oleh aplikasi apa pun di Android 11+;
  BersihKu membuka halaman Pengaturan aplikasinya supaya kamu bisa tekan *Hapus cache*.
- Kalau build gagal, catatan error otomatis disimpan di branch `build-log`.
