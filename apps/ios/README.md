# Thi360 — iOS App (`apps/ios`)

Native Swift / SwiftUI student app for Thi360 (`vn.unicom.thi360`, iOS 18.0+).

## Tool Versions & Dependencies (Verified on `devin-mac`)

| Tool / Item | Value | Notes |
| --- | --- | --- |
| Xcode | `27.1` (`27A9275`) | Installed at `/Applications/Xcode-27.1.0.app` (required for `iPhone Duo` simulator) |
| XcodeGen | `2.48.0` | Installed via Homebrew (`/opt/homebrew/bin/xcodegen`) |
| iOS Deployment Target | `18.0` | Swift 5 / SwiftUI (`@Observable`), verified by `DeploymentTargetTests` and `BundleInfoTests` |
| Simulators | `iPhone 17` (iOS 26.5), `iPad Air 11-inch (M4)` (iOS 26.5), `iPhone Duo` (iOS 27.1) | Boot cleanly before each test step; parallel lanes use a dedicated simulator named `<Device> <TASK>` |
| SwiftPM Packages (incl. SwaTex) | Declared in `apps/ios/project.yml` and locked in `apps/ios/Thi360.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` | Includes `SwiftOpenAPIGenerator`/`SwiftOpenAPIRuntime`/`SwiftOpenAPIURLSession`, `SwiftSoup`, and the pinned **SwaTex** formula engine (do not hardcode SwaTex versions in docs; verified by `FormulaEnginePinTests`) |

## Environment Setup on `devin-mac`

The default `xcode-select` on `devin-mac` points to Xcode 26.6. Export `DEVELOPER_DIR` and `PATH` before every iOS command:

```bash
export DEVELOPER_DIR=/Applications/Xcode-27.1.0.app/Contents/Developer
export PATH="/opt/homebrew/bin:$HOME/.local/bin:$PATH"
```

Because `devin-mac` is a Devin Cloud session that sleeps when idle and has limited disk:
- Touch `~/.keep-awake` before long-running or quiet steps and remove it (`rm -f ~/.keep-awake`) immediately when finished.
- Always pass `-derivedDataPath DerivedData` (worktree-local `DerivedData/` inside `apps/ios/`, gitignored) rather than building into `~/Library/Developer/Xcode/DerivedData`.
- Boot required simulators explicitly (`xcrun simctl boot <name-or-udid>`) or create missing lane simulators (`xcrun simctl create`) before each build/test step, and delete temporary lane simulators and `DerivedData` when the lane closes.

## Generate, Build, and Test (Plan A5)

`Thi360.xcodeproj` is generated from `apps/ios/project.yml` and ignored by `apps/ios/.gitignore` (only `apps/ios/Thi360.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` is committed for dependency locking).

```bash
cd apps/ios
xcodegen generate

# Build and test Thi360Dev on iPhone 17 (use your lane simulator name when running parallel lanes)
xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev -derivedDataPath DerivedData \
  -destination 'platform=iOS Simulator,name=iPhone 17' test

# Run a targeted test class (e.g. ContractFixturesTests, StatusPaletteTests, FormulaCorpusTests, DeploymentTargetTests)
xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev -derivedDataPath DerivedData \
  -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:Thi360Tests/FormulaCorpusTests

# Build Thi360Demo (strict HTTPS-only ATS, https://thi360.zone17.click)
xcodebuild -project Thi360.xcodeproj -scheme Thi360Demo -derivedDataPath DerivedData \
  -destination 'generic/platform=iOS Simulator' build

# Verify Demo ATS and environment configuration (CI .github/workflows/ios.yml also runs BundleInfoTests, DeploymentTargetTests, and *LogRedactionTests)
xcodebuild -project Thi360.xcodeproj -scheme Thi360Demo -derivedDataPath DerivedData \
  -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:Thi360Tests/TransportSecurityTests \
  -only-testing:Thi360Tests/EnvironmentConfigTests
```

### Overriding Local Dev URLs

`Config/Dev.xcconfig` defaults to `http://localhost:8080` (API) and `http://localhost:3000` (Web) and optionally includes `Config/Local.xcconfig` (gitignored). Create `Config/Local.xcconfig` inside `apps/ios/` to point `THI360_API_BASE_URL` and `THI360_WEB_BASE_URL` at another dev host (escape `//` as `/$()/` in `.xcconfig`).

## Directory Structure & Architecture

- `apps/ios/project.yml` — XcodeGen spec (`Thi360`, `Thi360Tests`, `Thi360UITests`, schemes `Thi360Dev` and `Thi360Demo`; bundles `contracts/mobile/legal` in app resources and `contracts/mobile/rich-text` + `docs/specs/fixtures/rich-text-samples.json` in `Thi360Tests`).
- `apps/ios/Config/` — `Base.xcconfig` (`IPHONEOS_DEPLOYMENT_TARGET = 18.0`, `INFOPLIST_KEY_CFBundleDisplayName = Thi360`), `Dev.xcconfig`, `Demo.xcconfig`.
- `apps/ios/Thi360/Thi360.entitlements` — Associated Domains (`applinks:` and `webcredentials:`) for `/reset-password` and `/checkout/result/app`.
- `apps/ios/Thi360/Core/` — Shared foundation:
  - `Config/AppEnvironment.swift`, `Network/MobileAPIClient.swift` (generated at build time from `contracts/mobile/openapi-mobile.json`), `Network/AppLogger.swift` (demo log redaction).
  - `Brand/BrandMark.swift`, `Theme/BrandPalette.swift` (17-token web-parity palette), `Theme/StatusPalette.swift` (8 semantic status tokens verified by `StatusPaletteTests` against `contracts/mobile/appearance/status-colors.json`).
  - `Loading/LoadStateView.swift`, `Layout/ListDetailLayout.swift`, `Layout/TwoColumnLayout.swift`, `Legal/LegalDocumentView.swift`, `Catalog/CatalogOptionsCache.swift`, `Image/ImageLoader.swift` (`URLSession` + in-memory cache cleared on logout).
  - `Preferences/AppPreferences.swift` (`vi` default locale, theme override never following OS dark mode), `Storage/AccountScopedStore.swift` & `Session/SessionCleanup.swift` (account-scoped local persistence & logout/startup cleanup).
  - `Localization/AppStrings.swift`, `Localization/ShellStrings.swift` & `apps/ios/Thi360/Resources/` String Catalogs (`*.xcstrings`, verified by `StringCatalogParityTests`).
- `apps/ios/Thi360/Shell/` — `AppRouter.swift`, `AdaptiveShellView.swift` (compact `TabView`, regular iPad `NavigationSplitView`, regular iPhone / `iPhone Duo` unfolded two-half book layout), `OrientationPolicy.swift`, and typed route registries in `apps/ios/Thi360/Shell/Routes/` (`CatalogRoutes`, `PaymentRoutes`, `MyExamsRoutes`, `PrepareRoutes`, `AttemptRoutes`, `ResultRoutes`, `ProfileRoutes`, `InboundLinkRouter`).
- `apps/ios/Thi360/Features/` — Student portal features:
  - **Catalog:** `CatalogHomeView`, `ProductDetailScreen` (`ExamDetailView`, `BundleDetailView`).
  - **Payment & Orders:** `CheckoutView`, `PaymentResultView`, `OrdersView`, `OrderDetailView`.
  - **My Exams & History:** `MyExamsScreen`, `HistoryScreen`.
  - **Prepare, Engine & Player:** `PrepareView`, `AttemptWaitingView`, `AttemptEngine`, `PlayerView` (chromeless in root `ZStack` behind lock/privacy shield).
  - **Rich Content, Question & Audio:** `RichContentView`, `SwaTexFormulaEngine` (SwaTex pinned in `apps/ios/project.yml`, verified against `contracts/mobile/rich-text/formulas.json` by `FormulaCorpusTests`), `AttemptAssetSource`, `QuestionView`, `QuestionSetView`, `AttachmentsView`, `AttemptAudioPlayerView` (play-limited, non-seekable, fixed `1×` rate).
  - **Result & Review:** `ResultView`, `ReviewView`.
  - **Account & Profile:** `AccountView`, `ProfileView` (inline email change in Profile Email block), `VerifyEmailView`, `DeleteAccountView`.
