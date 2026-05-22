# SIM CSV Bulk Sender

Native Android app (Kotlin) for bulk SMS sending directly from the device's physical SIM card.

---

## How to Open in Android Studio

1. **Unzip** this folder
2. Open **Android Studio** → `File → Open` → select the `SIMCSVBulkSender/` folder
3. Wait for Gradle sync to complete (requires internet to download dependencies)
4. Connect a **physical Android device** (not emulator — SMS requires a real SIM)
5. Click **Run ▶** to build and install the APK

> Minimum SDK: Android 8.0 (API 26)  
> Target SDK: Android 14 (API 34)  
> Language: Kotlin

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml          — permissions + service declarations
├── kotlin/com/simcsv/bulksender/
│   ├── BulkSenderApp.kt         — Application class, settings persistence
│   ├── MainActivity.kt          — Nav host with bottom navigation
│   ├── csv/
│   │   └── CsvParser.kt         — Memory-efficient CSV parser (100k rows)
│   ├── data/
│   │   ├── AppDatabase.kt       — Room database
│   │   ├── AppSettings.kt       — Settings data class
│   │   ├── Contact.kt           — Parsed CSV contact
│   │   ├── SendJob.kt           — Queue job with status
│   │   ├── SmsLog.kt            — Room entity for log storage
│   │   └── SmsLogDao.kt         — Room DAO
│   ├── sms/
│   │   ├── SimSelector.kt       — Dual SIM detection + selection
│   │   ├── SmsQueue.kt          — Thread-safe queue manager
│   │   ├── SmsSenderService.kt  — Foreground service (survives screen lock)
│   │   ├── SmsSentReceiver.kt   — BroadcastReceiver for send result
│   │   └── SmsDeliveryReceiver.kt — BroadcastReceiver for delivery
│   ├── permission/
│   │   └── PermissionManager.kt — Runtime permission handling
│   ├── logger/
│   │   ├── SmsLogger.kt         — Async DB logger
│   │   └── LogExporter.kt       — CSV export via FileProvider
│   └── ui/
│       ├── dashboard/           — Dashboard screen + ViewModel
│       ├── csvimport/           — CSV import screen + ViewModel + adapter
│       ├── preview/             — Queue preview screen + adapter
│       ├── progress/            — Live sending progress screen
│       ├── logs/                — Logs screen + ViewModel + adapter
│       └── settings/            — Settings screen
└── res/
    ├── layout/                  — All XML layouts (Material Design 3)
    ├── navigation/nav_graph.xml — Navigation graph
    ├── menu/bottom_nav_menu.xml — Bottom navigation items
    ├── values/                  — strings, colors, themes
    └── xml/file_paths.xml       — FileProvider paths
```

---

## Key Features

| Feature | Implementation |
|---|---|
| CSV Import | `CsvParser.kt` — streaming `BufferedReader`, no OOM on 100k rows |
| Phone Validation | UK (+44 / 07xxx), US (+1 / NANP), AU (+61 / 04xx) regex |
| SMS Engine | `SmsManager` via `SmsSenderService` foreground service |
| Dual SIM | `SubscriptionManager` lists active SIMs; user selects in Settings |
| Background Sending | Foreground service + `PARTIAL_WAKE_LOCK` (screen-lock safe) |
| Pause / Resume / Stop | Service actions sent via `Intent` |
| Retry | Configurable max retries per job in `SmsQueue` |
| Delay | Fixed (5–600s) or randomized (min–max range) |
| Daily Cap | Configurable; checked against Room DB today-count |
| Logging | Room DB (`SmsLog`) with session ID, status, timestamp |
| Log Export | CSV via `FileProvider`, shareable to any app |

---

## Required Permissions

Declared in `AndroidManifest.xml`, requested at runtime via `PermissionManager`:

- `SEND_SMS` — send messages
- `READ_PHONE_STATE` — detect SIM
- `READ_SMS` / `RECEIVE_SMS` — delivery confirmation
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — background sending
- `POST_NOTIFICATIONS` — persistent sending notification
- `READ_EXTERNAL_STORAGE` (Android ≤ 12) — file picker

---

## Building a Release APK

In Android Studio:
1. `Build → Generate Signed Bundle / APK`
2. Choose **APK**
3. Create or select a keystore
4. Select `release` build variant
5. APK output: `app/release/app-release.apk`

Or via command line:
```bash
./gradlew assembleRelease
```

---

## Notes

- SMS sending requires a **physical device with an active SIM** — emulators cannot send SMS
- Carrier restrictions may block bulk SMS; this varies by carrier and country
- UK/US/AU number formats are auto-detected and normalized (e.g. `07911 123456` → `+447911123456`)
- Invalid rows are shown separately in the Import screen and excluded from the queue
