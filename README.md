# Ricochet 🟠⚡

**Ricochet** is a lightweight, Material 3 Android utility app built with Kotlin and Jetpack Compose that bounces important incoming SMS alerts from one phone to another based on customizable regular expression rules.

## Why Ricochet Exists (Who This Is For)

Broadly, 2 sets of people will find Ricochet helpful:
1. People with parents or older family members living in another city who aren't tech-savvy. 
2. People who transact or invest through accounts of their parents, siblings or spouses. 

Ricochet automatically forwards you SMSs about important alerts, OTPs, banking alerts, or unfamiliar login warnings without needing to call up the recipient or walking them through checking their messages. To begin, just set-up a rule on Ricochet to forward SMSs from a particular sender (say "HSBC") or containing particular keywords ("OTP").

**Ricochet** makes that effortless:
- **For people who manage their parents' netbanking accounts**: Receive real time copies of login OTPs, or transaction OTPs sent by your bank to your parents' phone, without having to call them up. 
- **For people who invest or transact using the accounts of their parents, siblings or spouses**: If you invest using your parents accounts' (common for salaried employees in India due to tax savings), OTPs for authentication (say for a fixed deposit creation) will be sent to your parents' phone. Ricochet send you real time copies of these OTPs. Likewise, if you like to use your spouse's credit card which has a SMS based 2FA, Ricochet is your friend.   
- **For people who worry about their parents, or older relatives being scammed online**: Receive real-time copies of specific login or security alert SMS messages sent to their phones so you can spot suspicious activity early.
- **For multi-device users**: Bounce important carrier, bank, or service notifications from a secondary device to your primary number without carrying two phones everywhere.
- **100% On-Device & Transparent**: No external servers, no cloud accounts, and no third-party APIs. Every forwarded message is logged in the on-device **History** tab, triggers a visible local notification, and can be paused anytime with a single **Master Switch**.

## Key Features

- **Material 3 UI (Jetpack Compose)**: Clean, accessible interface supporting Android 10+ (`minSdk = 29`) with dynamic Material You theming on Android 12+.
- **Regex Rule Engine (`AND` Logic)**:
  - Configure rules by **Sender Regex** (e.g., `CP-HSBC-S` or `.*(Facebook|FB).*`), **Keyword / Message Regex** (e.g., `.*(login alert|new sign-in).*`), or **both**.
  - When both Sender and Message patterns are filled, Ricochet requires **both to match (`AND`)** before forwarding.
- **Built-in Quick Regex Tester**: Test sample sender IDs and SMS text live inside the Add/Edit Rule dialog before saving.
- **Sender Prepending & Multi-Part SMS Support**:
  - Prepends `[Ricochet from: <OriginalSender>]` to the forwarded SMS so you immediately know which service sent the alert.
  - Automatically splits long alerts (>160 characters) into multi-part SMS messages using the phone's default outbound SIM selection (dual-SIM friendly).
- **Loop Prevention**: Automatically ignores messages that already start with `[Ricochet from:` or originate from a rule's target recipient number.
- **On-Device History & Master Toggle**: View timestamps, matched rules, and `Sent`/`Failed` delivery status in the **History** tab, or pause all forwarding with one tap.

## Getting Started

1. Open the unzipped `Ricochet` folder in **Android Studio** (Iguana / Jellyfish / Koala or newer).
2. Let Gradle sync, then run directly on a connected phone (`Run 'app'`) or build an APK via **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. **Android 13+ (API 33+) Sideloading Note**:
   - If you install the APK manually (rather than via USB from Android Studio), Android 13+ places SMS permissions under **Restricted Settings**.
   - On the phone, open **Settings -> Apps -> Ricochet**, tap the **three-dot menu (⋮)** in the top-right corner, and tap **Allow restricted settings**. Then return to the app and tap **Grant Permissions**.
