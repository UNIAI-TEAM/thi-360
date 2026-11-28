# Thi360 — App học viên Android (`apps/android`)

Ứng dụng học viên native cho Android (Kotlin + Jetpack Compose + Material 3).

## Thông số dự án

| Mục | Giá trị |
| --- | --- |
| Định danh (`applicationId`) | `vn.unicom.thi360` |
| Tên hiển thị | `Thi360` |
| `minSdk` / `compileSdk` / `targetSdk` | `26` (Android 8.0) / `37.2` / `37` |
| JDK | `21` |
| Gradle / AGP / Kotlin / Compose BOM / Thư viện | Khai báo và ghim tại `apps/android/gradle/libs.versions.toml` và tệp khóa `apps/android/app/gradle.lockfile` (gồm OpenAPI Generator, Coil 3, Jsoup, AndroidX Browser và bộ vẽ công thức RaTeX) |

## Môi trường (`flavor`)

| Flavor | `API_BASE_URL` | `WEB_BASE_URL` | Chính sách `http://` |
| --- | --- | --- | --- |
| `dev` | `http://10.0.2.2:8080` | `http://10.0.2.2:3000` | Chỉ cho `http://` tới `10.0.2.2`, `localhost`, `127.0.0.1` |
| `demo` | `https://thi360.zone17.click` | `https://thi360.zone17.click` | Chặn toàn bộ `http://` (chỉ HTTPS) |

## Kiến trúc màn và thành phần chính

- **Khung & nền chung (`core/`, `shell/`):**
  - `BrandMark` (`apps/android/app/src/main/java/vn/unicom/thi360/core/brand/BrandMark.kt`), `StatusPalette` (`apps/android/app/src/main/java/vn/unicom/thi360/core/theme/StatusPalette.kt`), `LoadStateView` (`apps/android/app/src/main/java/vn/unicom/thi360/core/ui/LoadStateView.kt`), `LegalDocumentView` (`apps/android/app/src/main/java/vn/unicom/thi360/core/legal/LegalDocumentView.kt`), `AccountScopedStore` & `SessionCleanup` (dọn dữ liệu theo `accountId` khi đăng xuất/xóa tài khoản và lúc khởi động), `CatalogOptionsCache`, `ImageLoader` (Coil 3).
  - Các nhóm tuyến trong `apps/android/app/src/main/java/vn/unicom/thi360/shell/routes/`: `CatalogRoutes`, `PaymentRoutes`, `MyExamsRoutes`, `PrepareRoutes`, `AttemptRoutes`, `ResultRoutes`, `ProfileRoutes`, `LegalRoutes`, cùng `InboundLinkRouter` xử lý App Links `/reset-password` và `/checkout/result/app`.
- **Các màn cổng thi (`feature/`):**
  - **Trang chủ & Danh mục:** `CatalogScreen`, `ProductDetailScreen` (`ExamDetailScreen`, `BundleDetailScreen`).
  - **Mua hàng & Đơn hàng:** `CheckoutScreen` (Custom Tabs VNPAY + Liên kết trả về), `PaymentResultScreen`, `OrdersScreen`, `OrderDetailScreen`.
  - **Bài thi của tôi & Lịch sử:** `MyExamsScreen`, `HistoryScreen`.
  - **Chuẩn bị, Bộ máy & Làm bài:** `PrepareScreen`, `AttemptWaitingScreen`, `AttemptEngine`, `PlayerScreen`, `QuestionView`, `QuestionSetView`, `AttachmentsView`, `AttemptAudioPlayer` (giới hạn lượt nghe, không tua, tốc độ cố định `1×`).
  - **Kết quả & Xem lại:** `ResultScreen`, `ReviewScreen`.
  - **Tài khoản & Hồ sơ:** `AccountScreen`, `ProfileScreen` (biểu mẫu đổi email mở ngay trong khối Email), `VerifyEmailScreen`, `DeleteAccountScreen`.
- **Nội dung đề & bộ vẽ công thức RaTeX (`feature/richcontent/`):**
  - `RichContentView` vẽ native (không dùng WebView) cùng `AttemptAssetSource` tải ảnh/tệp qua Bearer và dọn tệp tạm qua `SessionCleanup`.
  - Công thức vẽ bằng **RaTeX** (`RaTeXFormulaEngine`); phiên bản ghim khai báo ở `apps/android/gradle/libs.versions.toml` (không ghi lặp số phiên bản trong tài liệu) và khóa ở `apps/android/app/gradle.lockfile`, kiểm bởi `FormulaEnginePinTest`.
  - Trên JVM không nạp được thư viện gốc của RaTeX nên các test JVM dùng `SourceBoxFormulaEngine` thế chỗ; lượt kiểm vẽ công thức thật trên toàn bộ bộ mẫu `contracts/mobile/rich-text/formulas.json` chạy trên máy ảo bằng `FormulaCorpusInstrumentedTest`. Căn lề trang bộ nhớ 16 KB của các tệp `.so` RaTeX được kiểm tĩnh trong CI (`.github/workflows/android.yml`, task `check16KbAlignment` ở `apps/android/app/build.gradle.kts`) và chạy lượt kiểm tệp công thức trên ảnh máy ảo ARM64 trang bộ nhớ 16 KB theo ĐT 723.

## Kho khóa debug dùng chung

Tệp `apps/android/keystores/debug.keystore` là kho khóa **debug dùng chung** cho mọi máy lập trình và CI (không phải khóa phát hành), nhằm giữ cùng vân tay chứng chỉ cho xác minh Android App Links (`/.well-known/assetlinks.json`):

- Alias: `thi360-debug`
- SHA-256: `E5:81:6D:6A:34:B3:D3:C9:BE:91:9B:98:75:79:39:84:51:7C:58:E3:9B:1E:10:E9:77:4B:17:8E:BE:D9:FB:56`

Vân tay này cũng được khai báo tại biến `THI360_ANDROID_CERT_SHA256` trong `.env.example`.

## Biến môi trường đĩa trên máy Windows (ổ `D:`)

Trên máy Windows có ổ `C:` ít dung lượng, luôn đặt `GRADLE_USER_HOME` và `ANDROID_AVD_HOME` sang ổ `D:` trước khi chạy lệnh Gradle hoặc máy ảo:

```powershell
$env:GRADLE_USER_HOME = "D:/.gradle-thi360"
$env:ANDROID_AVD_HOME = "D:/.android-avd"
```

## Các lệnh thường dùng (theo Kế hoạch A5)

Chạy trong thư mục `apps/android`:

```bash
# Sinh client Kotlin từ contracts/mobile/openapi-mobile.json (không commit mã sinh)
./gradlew :app:generateMobileOpenApiClient

# Build APK cho cả hai flavor
./gradlew :app:assembleDevDebug :app:assembleDemoDebug

# Chạy toàn bộ test đơn vị và giao diện trên JVM (cả hai flavor)
./gradlew :app:testDevDebugUnitTest :app:testDemoDebugUnitTest

# Chạy test JVM theo mẫu lớp (ví dụ ContractFixturesTest, StatusPaletteTest, BrandNameScanTest)
./gradlew :app:testDevDebugUnitTest --tests '<mẫu>'
./gradlew :app:testDemoDebugUnitTest --tests '<mẫu>'

# Ghi lại ảnh chuẩn Roborazzi khi chủ động đổi giao diện
./gradlew :app:recordRoborazziDevDebug

# Đối chiếu ảnh chụp Roborazzi (360x640, 412x915, 840x720, 1280x800 · sáng & tối)
./gradlew :app:verifyRoborazziDevDebug

# Chạy Android Lint
./gradlew :app:lintDevDebug

# Chạy test trên máy ảo (Pixel 7 API 34, hoặc ảnh máy ảo ARM64 trang bộ nhớ 16 KB cho lượt kiểm FormulaCorpusInstrumentedTest)
./gradlew :app:connectedDevDebugAndroidTest
```

Ghi chú: trong CI (`.github/workflows/android.yml`), `FormulaCorpusInstrumentedTest` chạy trên máy ảo Pixel 7 API 34 chia 4 mảnh; trên ảnh máy ảo ARM64 trang 16 KB, chạy cùng lệnh `./gradlew :app:connectedDevDebugAndroidTest` (có thể lọc riêng `-Pandroid.testInstrumentationRunnerArguments.class=vn.unicom.thi360.feature.richcontent.FormulaCorpusInstrumentedTest`).
