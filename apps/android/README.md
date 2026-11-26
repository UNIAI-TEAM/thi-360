# Thi 360 — App học viên Android (`apps/android`)

Ứng dụng học viên native cho Android (Kotlin + Jetpack Compose + Material 3).

## Thông số dự án

| Mục | Giá trị |
| --- | --- |
| Định danh (`applicationId`) | `vn.unicom.thi360` |
| Tên hiển thị | `Thi 360` |
| `minSdk` / `compileSdk` / `targetSdk` | `26` (Android 8.0) / `37.2` / `37` |
| JDK | `21` |
| Gradle / AGP / Kotlin / Compose BOM | Xem `gradle/libs.versions.toml` (Gradle `9.9.2`, AGP `9.5.2`, Kotlin `2.4.21`, Compose BOM `2026.11.01`) |

## Môi trường (`flavor`)

| Flavor | `API_BASE_URL` | `WEB_BASE_URL` | Chính sách `http://` |
| --- | --- | --- | --- |
| `dev` | `http://10.0.2.2:8080` | `http://10.0.2.2:3000` | Chỉ cho `http://` tới `10.0.2.2`, `localhost`, `127.0.0.1` |
| `demo` | `https://thi360.zone17.click` | `https://thi360.zone17.click` | Chặn toàn bộ `http://` (chỉ HTTPS) |

## Kho khóa debug dùng chung

Tệp `apps/android/keystores/debug.keystore` là kho khóa **debug dùng chung** cho mọi máy lập trình và CI (không phải khóa phát hành), nhằm giữ cùng vân tay chứng chỉ cho xác minh Android App Links (`/.well-known/assetlinks.json`):

- Alias: `thi360-debug`
- SHA-256: `E5:81:6D:6A:34:B3:D3:C9:BE:91:9B:98:75:79:39:84:51:7C:58:E3:9B:1E:10:E9:77:4B:17:8E:BE:D9:FB:56`

Vân tay này cũng được khai báo tại biến `THI360_ANDROID_CERT_SHA256` trong `.env.example`.

## Biến môi trường đĩa trên máy Windows (nếu ổ `C:` ít dung lượng)

Trước khi chạy lệnh Gradle hoặc máy ảo trên Windows, đặt bộ đệm sang ổ `D:`:

```powershell
$env:GRADLE_USER_HOME = "D:/.gradle-thi360"
$env:ANDROID_AVD_HOME = "D:/.android-avd"
```

## Các lệnh thường dùng

Chạy trong thư mục `apps/android`:

```bash
# Build APK cho cả hai flavor
./gradlew :app:assembleDevDebug :app:assembleDemoDebug

# Chạy toàn bộ test đơn vị và giao diện trên JVM (cả hai flavor)
./gradlew :app:testDevDebugUnitTest :app:testDemoDebugUnitTest

# Ghi lại ảnh chuẩn Roborazzi khi chủ động đổi giao diện
./gradlew :app:recordRoborazziDevDebug

# Đối chiếu ảnh chụp Roborazzi (360x640, 412x915, 840x720, 1280x800 · sáng & tối)
./gradlew :app:verifyRoborazziDevDebug

# Chạy Android Lint
./gradlew :app:lintDevDebug

# Chạy test trên máy ảo (Pixel 7 API 34)
./gradlew :app:connectedDevDebugAndroidTest
```
