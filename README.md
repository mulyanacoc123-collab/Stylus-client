# StylusClient

Aplikasi Android untuk tablet display. Proyek Kotlin/XML memakai `minSdk 24` (Android 7.0 Nougat) dan `targetSdk 33`. UI awal meminta IP HP dan kode pairing; setelah autentikasi berhasil, aplikasi mengunci orientasi landscape dan memakai fullscreen immersive.

## Build APK lewat GitHub

1. Buat repository GitHub dan upload seluruh isi folder proyek ini.
2. Buka tab **Actions** dan jalankan workflow **Build APK**, atau push commit.
3. Unduh artifact `StylusClient-debug-apk` setelah workflow selesai. APK berada di `app/build/outputs/apk/debug/app-debug.apk`.

Workflow memakai Java 17, Gradle 8.7, Android SDK, `gradle assembleDebug`, dan `actions/upload-artifact@v4`.

## Cara menjalankan

1. Pastikan HP menjalankan StylusHost dan screen cast aktif.
2. Pastikan kedua perangkat berada pada Wi-Fi yang sama dan tidak terhalang client isolation di router.
3. Buka StylusClient di tablet, masukkan IP yang terlihat di HP dan kode pairing 8 digit.
4. Tekan **Connect**. Setelah pairing berhasil, layar masuk ke fullscreen landscape.
5. Gunakan stylus pada area gambar. Sentuhan jari diabaikan sebagai palm rejection sederhana.

## Perilaku input

- WebSocket menerima frame JPEG sebagai binary message.
- Koordinat stylus dinormalisasi terhadap area gambar yang benar-benar dirender (`0.0`–`1.0`), termasuk kompensasi letterbox.
- Input dikirim sebagai JSON `type=touch`, dengan `action`, `x`, `y`, dan `pressure`.
- Hanya `MotionEvent.TOOL_TYPE_STYLUS` yang diteruskan. Finger/palm ditolak; perangkat yang melaporkan pena sebagai tipe lain mungkin tidak kompatibel.

## Batasan

Client mengirim pressure sebagai metadata, tetapi StylusHost tidak dapat mengubahnya menjadi tekanan digitizer asli karena keterbatasan Accessibility `dispatchGesture()`. Ini bukan pengganti koneksi USB/HID SuperDisplay; latency, kompatibilitas aplikasi gambar, dan kualitas garis dipengaruhi oleh Wi-Fi dan implementasi input sintetis Android.
