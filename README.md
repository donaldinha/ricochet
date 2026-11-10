# Ricochet (Android 10+ / Kotlin + Jetpack Compose)

A Material 3 Android utility app that forwards incoming SMS messages matching user-configured regular expression rules to a designated recipient phone number via SMS.

## Features Built
- **Material 3 UI (Jetpack Compose)** with **Rules** and **History** tabs plus a **Ricochet Active/Paused** master switch.
- **Regex Rule Engine (AND logic)**:
  - Each rule configures a **Rule Name**, **Recipient Phone Number**, optional **Sender Regex**, and optional **Message Keyword/Regex** (case-insensitive, multiline-safe).
  - If both Sender Regex and Message Regex are specified on a rule, **both must match (AND)**.
  - Includes a live **Quick Regex Tester** right inside the Add/Edit Rule dialog so you can test sample sender IDs and SMS bodies before saving.
- **Prepending & Multipart Default-SIM Forwarding**:
  - Prepends `[Ricochet from: <OriginalSender>]` to the forwarded SMS body.
  - Uses `SmsManager` with the phone's default outbound SMS SIM selection and `divideMessage()` / `sendMultipartTextMessage()` for long messages (>160 characters).
  - Includes loop prevention so an already-forwarded `[Ricochet from: ...]` message or a message arriving from the recipient's own number is never re-forwarded.
- **Forwarded History & Visible Notifications**:
  - Logs every forwarded SMS with timestamp, matched rule name, original sender, recipient, and delivery status (`Sent` / `Failed`).
  - Posts a local notification whenever a rule triggers.

## How to Build & Install on Your Parents' Phone
1. Open the unzipped `Ricochet` folder in **Android Studio** (Iguana / Jellyfish / Koala or newer).
2. Let Gradle sync, then run directly on a connected phone (`Run 'app'`) or build an APK via **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. **Android 13+ (API 33+) Sideloading Note**:
   - If you install the APK manually (rather than via USB from Android Studio), Android 13+ places SMS permissions under **Restricted Settings**.
   - On the phone, open **Settings -> Apps -> Ricochet**, tap the **three-dot menu (⋮)** in the top-right corner, and tap **Allow restricted settings**. Then return to the app and tap **Grant Permissions**.
