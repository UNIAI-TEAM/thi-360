# Thi 360 — iOS App (`apps/ios`)

Native Swift / SwiftUI student app for Thi 360 (`vn.unicom.thi360`, iOS 17.0+).

## Tool Versions (Verified on `devin-mac`)

| Tool | Verified version | Notes |
| --- | --- | --- |
| Xcode | `27.1` (`27A9275`) | Installed at `/Applications/Xcode-27.1.0.app` (required for `iPhone Duo` simulator) |
| XcodeGen | `2.48.0` | Installed via Homebrew (`/opt/homebrew/bin/xcodegen`) |
| iOS Deployment Target | `17.0` | Swift 5 / SwiftUI (`@Observable`) |
| Simulators | `iPhone 17` (iOS 26.5), `iPad Air 11-inch (M4)` (iOS 26.5), `iPhone Duo` (iOS 27.1) | Boot cleanly before each test step |

## Environment Setup on `devin-mac`

The default `xcode-select` on `devin-mac` points to Xcode 26.6. Export `DEVELOPER_DIR` and `PATH` before every iOS command:

```bash
export DEVELOPER_DIR=/Applications/Xcode-27.1.0.app/Contents/Developer
export PATH="/opt/homebrew/bin:$HOME/.local/bin:$PATH"
```

Because `devin-mac` is a Devin Cloud session that sleeps when idle:
- Touch `~/.keep-awake` before long-running or quiet steps and remove it (`rm -f ~/.keep-awake`) immediately when finished.
- Do not rely on simulators left running from earlier steps; boot required simulators explicitly (`xcrun simctl boot <name-or-udid>`) or create missing ones with `xcrun simctl create`.

## Generate, Build, and Test

`Thi360.xcodeproj` is generated from `project.yml` and ignored by `apps/ios/.gitignore`.

```bash
cd apps/ios
xcodegen generate

# Build and test Thi360Dev on iPhone 17
xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev \
  -destination 'platform=iOS Simulator,name=iPhone 17' test

# Build Thi360Demo (strict HTTPS-only ATS, https://thi360.zone17.click)
xcodebuild -project Thi360.xcodeproj -scheme Thi360Demo \
  -destination 'generic/platform=iOS Simulator' build

# Verify Demo ATS and environment configuration
xcodebuild -project Thi360.xcodeproj -scheme Thi360Demo \
  -destination 'platform=iOS Simulator,name=iPhone 17' test \
  -only-testing:Thi360Tests/TransportSecurityTests \
  -only-testing:Thi360Tests/EnvironmentConfigTests
```

### Overriding Local Dev URLs

`Config/Dev.xcconfig` defaults to `http://localhost:8080` (API) and `http://localhost:3000` (Web) and optionally includes `Config/Local.xcconfig` (gitignored). Create `apps/ios/Config/Local.xcconfig` to point `THI360_API_BASE_URL` and `THI360_WEB_BASE_URL` at another dev host (escape `//` as `/$()/` in `.xcconfig`).

## Directory Structure & Downstream Extension Points

- `project.yml` — XcodeGen spec (`Thi360`, `Thi360Tests`, `Thi360UITests`, schemes `Thi360Dev` and `Thi360Demo`). Sources are directory-grouped, so adding Swift files does not require editing `project.yml`.
- `Config/` — `Base.xcconfig`, `Dev.xcconfig`, `Demo.xcconfig`.
- `Thi360/Core/Config/AppEnvironment.swift` — Build environment and ATS policy validation.
- `Thi360/Core/Theme/BrandPalette.swift` — 17-token web-parity palette (`thi360-green` light & dark defaults).
- `Thi360/Core/Preferences/AppPreferences.swift` — Persisted `locale` (`vi` default regardless of OS language) and `themeMode` (`nil` defaults to light / server default, never follows OS dark mode).
- `Thi360/Core/Localization/ShellStrings.swift` & `Thi360/Resources/Shell.xcstrings` — Instant in-app `vi`/`en` string lookup verified by `StringCatalogParityTests`.
- `Thi360/Shell/AppRouter.swift` — Root routing hub where `IOS-2..IOS-4` attach session, recovery/deep-link, and account/lock flows.
- `Thi360/Shell/AdaptiveShellView.swift` — Adaptive 4-destination shell:
  - Compact width (`iPhone`, `iPhone Duo` folded `466x678`, `iPad` compact Split View) → bottom `TabView`
  - `iPad` regular width → `NavigationSplitView` sidebar
  - `iPhone` regular width (`iPhone Duo` unfolded `669x951` / `951x669`) → two-half book layout with bottom tab bar in the left half
- `Thi360/Shell/OrientationPolicy.swift` — Portrait lock for compact iPhone; portrait & landscape for iPad and regular-width iPhone.
