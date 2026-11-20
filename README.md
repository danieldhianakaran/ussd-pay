# USSD Pay

Turns a scanned UPI QR code into a guided `*99#` (NUUP) USSD payment.

## Workflow

1. **Scan** — camera preview + ZXing decodes any UPI QR
   (`upi://pay?pa=...&am=...&tn=...`) and fills VPA / amount / note.
2. **Confirm** — the Start button asks
   `Send Rs.X to <vpa> via *99#?`.
3. **Session** — `TelephonyManager.sendUssdRequest("*99#")` opens the
   operator menu; Android renders it in `com.android.phone`'s USSD dialog.
4. **Autofill** — `AutoPilotService` (AccessibilityService) watches that
   dialog and answers by prompt text:

   | Prompt | Action |
   |---|---|
   | menu with `N. Send Money` | sends `N` |
   | menu with `N. UPI ...` (labels containing "pin" excluded) | sends `N` |
   | `Enter UPI ID` | pastes the scanned VPA |
   | `Enter amount` | pastes the amount |
   | `Enter a remark` | pastes the note, or `1` when the note is empty |
   | anything containing **PIN** | stops, disarms — the user types it |
   | menu with `N. Exit` | sends `N`, closing the session |

5. **PIN** — always entered by the user. The service never fills, stores
   or transmits the UPI PIN.

## Why accessibility

Android exposes no API to continue an interactive USSD session —
`sendUssdRequest()` is single-shot and returns `-1` (`USSD_ERROR_SERVICE_UNAVAIL`)
for a second request while the dialog is open. Driving the operator's
dialog through accessibility is the only workable route. USSD sessions
also expire after roughly 30–60 s of silence, which is why the VPA and
amount are pasted rather than typed.

## Build

```sh
bash build.sh
```

Manual pipeline (no Gradle, no AndroidX):

```
aapt2 compile -> aapt2 link -> javac -> d8 -> zipalign -> apksigner
```

Expects under `~/toolchain`:

- Temurin JDK 17 (`jdk/`)
- Android SDK with `platforms/android-30` and `build-tools/34.0.0`
- `zxing-core.jar` (dexed into the APK)

Output: `build/ussdpay.apk` (debug-signed).

## Permissions

- `CAMERA` — QR scanning
- `CALL_PHONE` — `sendUssdRequest`
- Accessibility service — optional, required only for the autofill step
