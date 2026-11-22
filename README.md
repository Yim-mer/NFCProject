# NFC Patrol – Guard Tour System with Signed NDEF Tags

Android app (Kotlin, Jetpack Compose) for the course assignment on **Near Field Communication (NFC)**.
Cheap NTAG213 stickers act as patrol checkpoints. The app writes them as signed NDEF tags, verifies them during a patrol, and reports anomalies (missed, out of order, too fast, duplicate and forged/copied tags).

## Features

- **Admin mode:** writes a checkpoint to a blank tag (custom MIME record `application/vnd.jim.patrol` + Android Application Record).
- **Signed checkpoints:** HMAC-SHA256 over the tag UID + data, with the key generated and kept in the Android Keystore. A tag whose content has been copied to another tag is detected as forged.
- **Patrol mode:** start a patrol, scan checkpoints and get a report at the end.
- **Anomaly detection:** `FORGED`, `DUPLICATE`, `OUT_OF_ORDER`, `TOO_FAST` (< 5 s between scans), `MISSED`, `TIMEOUT` (> 10 min).
- **Reader mode:** while the app is open, tags go straight to it. Tapping a checkpoint while the app is closed opens it via the AAR.

## Requirements

| Item | Requirement |
|---|---|
| Device | Android phone with NFC, Android 8.0 (API 26) or newer |
| Tags | NFC Forum Type 2 tags: NTAG213 (tested), NTAG215/216 also work |
| Not supported | MIFARE Classic tags, 125 kHz RFID tags (EM4100 etc.) |
| Emulator | **Not supported**: the Android Emulator has no NFC hardware |

## Tool versions

| Tool | Version |
|---|---|
| Android Studio | AI-262.10968.63.2622.16840690 (built November 5, 2026) |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.2.10 |
| Jetpack Compose BOM (incl. Material 3) | 2026.02.01 |
| androidx.activity:activity-compose | 1.8.0 |
| androidx.core:core-ktx | 1.10.1 |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| Java (source/target compatibility) | 11 |
| Gradle | `[from gradle/wrapper/gradle-wrapper.properties]` |

## Tested on

- **Xiaomi POCO F7 Ultra**, Android 16 (BP2A.250605.031.A3)
- 5 × NTAG213 25 mm stickers (4 checkpoints + 1 spare for the copy test)
- Tag content checked with **NFC Tools** (wakdev) and **NXP TagInfo**

## Build and run

1. Clone the repository and open the project folder in Android Studio. Gradle downloads the dependencies automatically.
   ```
   git clone [repository URL]
   ```
2. On the phone, enable Developer options (Settings → About phone → tap **Build number** 7 times), then turn on **USB debugging**.
3. Turn on **NFC** on the phone.
4. Connect the phone with a USB cable that supports data transfer, and accept the **"Allow USB debugging?"** prompt.
5. In Android Studio, select the phone in the device dropdown next to **Run** (not the emulator) and press **Run ▶**.

## Usage

### 1. Register checkpoints (once)
1. Turn on the **Write mode** switch.
2. Enter a checkpoint name (no `|` character) and its order (1–4).
3. Hold a blank tag to the back of the phone until "Wrote checkpoint …" appears.
4. Repeat for checkpoints 2–4.

### 2. Run a patrol
1. Turn **Write mode** off.
2. Enter a guard name and press **Start patrol**.
3. Scan the checkpoints in order, at least 5 seconds apart.
4. Press **Finish patrol** to see the report.

### Trying the forgery detection
Use NFC Tools (*Other → Copy tag*) to copy a registered checkpoint onto the spare tag, then scan the copy during a patrol. The app shows **WARNING FORGED OR COPIED TAG** and the report lists a `FORGED` anomaly.

## Important notes

- **No secrets in the repository.** The HMAC key is generated on the device on first use and stored in the Android Keystore. Nothing needs to be configured.
- **Tags are bound to one install.** Tags only verify on the same installation that registered them. **Uninstalling the app or clearing its data deletes the key**, after which all tags must be registered again.
- **Never lock tags.** Do not use "lock" / "make read-only" in NFC Tools or other apps: it is permanent and the tag can no longer be rewritten.
- **Portrait only.** The patrol is kept in memory and the activity is locked to portrait so rotation does not reset it. Closing the app ends the current patrol.
- Hold the tag still against the phone's NFC antenna (usually the upper-middle back) until the result appears.

## Project structure

```
app/src/main/
├── AndroidManifest.xml        NFC permission, nfc feature, singleTop, portrait
└── java/.../
    ├── MainActivity.kt        reader mode, tag read/write, Compose UI
    ├── SigningKey.kt          Keystore key, HMAC sign/verify
    └── PatrolSession.kt       patrol rules, anomalies, ReadResult
```

## Known limitations

- Special "magic" tags with a writable UID can clone a checkpoint, UID included, and pass verification.
- Patrol history is not saved, and the route is fixed (R1, 4 checkpoints).
- The first tap that opens the app from a closed state is not processed. Tap again once the app is open.
