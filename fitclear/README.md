# FitClear HM08 diagnostic (first APK)
Black background, white large text. Native read-only Android BLE diagnostic for already-paired HM08-98F.
No health data writes, message access, unpairing, remote servers, or commands.
This build is not the full FitClear companion app. Health Connect/MyFitnessPal syncing is not yet implemented.
Keep FitCloudPro and pairing intact. The app may temporarily interfere with FitCloudPro while connected.
See .github/workflows/fitclear-hm08.yml to build debug APK.

## V2
Separate app ID for installation alongside v1. Safely checks FF04 and FF03 read channels sequentially and explains read denial code 2. No commands, writes, notifications, pairing changes, health syncing or cloud transfers.

## V3 Health Connect
New native read-only Health Connect dashboard for steps, last sleep session and latest recorded heart-rate. Permissions user-controlled, source may be other than HM08. Bluetooth diagnostics preserved. No export or writes.

## v4 Health Connect permission repair
Add platform Android 14+ VIEW_PERMISSION_USAGE privacy rationale alias and package query for Android <=13, fix Health Connect settings intent, show user-visible permission status and fallback. New unique app id (v4) avoids debug signing collision with previous installations. No health writes; no watch commands.

## v5 source attribution and sleep fragments
Step aggregate source names from Health Connect AggregateResult.dataOrigins; sleep and heart rate sources from record Metadata. Show the longest recorded sleep session from the last 72h and flag anything under 30 min as a short fragment, rather than pretending it is a full night's sleep. Still read-only, no phone-to-watch writes, no MyFitnessPal integration directly.
