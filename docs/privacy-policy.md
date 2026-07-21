# Privacy Policy — Pills in Time

**Effective:** 2026-07-20 · **Version:** 1.0 · **Package:** `app.zelgray.pills_in_time`

A plain explanation of what the app stores, where it goes, and what stays entirely on your device.

## In short

- Your medication data stays **on your device** unless you explicitly turn on backup.
- Backup, if you enable it, goes to **your own** Google Drive or a file you choose — never to our servers, because we don't run any.
- No ads, no analytics, no trackers, no data sold to anyone.
- Uninstalling the app deletes everything it stored locally.

## 1. Overview

Pills in Time ("the app") is a medication-tracking app for Android. This policy explains what
information the app handles, why, and the choices you have — written for the app as it actually
works, not as boilerplate.

The short version: the app was built to work entirely offline. Everything you enter — drug names,
dosages, schedules, stock levels, intake history — is written to a private database on your own
device and is never transmitted anywhere by default. The only data that ever leaves your device
does so because you chose a specific, optional feature (cloud backup) and only goes to a
destination you control (your own Google Drive account, or a file you save yourself).

## 2. Information the app stores

To do its job, the app stores the information you give it directly:

| Category | Examples | Where it lives |
|---|---|---|
| Patient profiles | Name you give each tracked person, chosen accent color | On-device database |
| Medication details | Drug name, form, strength, stock quantity | On-device database |
| Schedules | Dosing times, cycle pattern, start/end dates | On-device database |
| Intake history | Taken/skipped status, timestamps, doses logged | On-device database |
| App settings | Language, snooze duration, backup connection state | On-device preferences |

None of this is health data in the sense of a medical record tied to your identity — there's no
account, no name, no date of birth collected by the app itself. It's simply whatever you choose to
type in. We don't collect analytics events, usage statistics, advertising identifiers, device
fingerprints, or diagnostic reports of any kind.

## 3. Optional Google Drive backup

If you turn this on from Settings, the app asks Google to authorize access to a single, narrow
scope: `drive.appdata`. This is a hidden folder inside your own Google Drive that:

- only this app can read or write to — not other apps, not your visible Drive files;
- we (the developer) cannot see or access — it lives entirely under your Google account;
- holds a single JSON export of your medication data, replaced each time you back up.

Authorization is handled by Google's own Play Services Authorization API — your Google credentials
are never seen or stored by this app. You can revoke access at any time from your Google Account's
permissions page, which also removes the backup file. Turning the feature off in the app disconnects
it but does not, by itself, delete a backup already sitting in your Drive — use your Google Account
settings to remove it fully.

## 4. Optional local file backup

As an alternative to Google Drive, you can export the same data to a file you choose yourself, using
Android's standard file picker. That file is saved wherever you tell it to — on your device, an SD
card, or a cloud folder you already sync — and the app has no further involvement with it once
written.

## 5. Notifications and permissions

The app requests a small number of Android permissions, all used strictly for local functionality:

- **Notifications** — to show medication reminders and low-stock alerts.
- **Exact alarms** — so reminders fire at the precise time you scheduled, not "roughly around" it.
- **Run at device startup** — so reminders survive a phone restart.

None of these permissions send information off your device — they exist purely to schedule and display local notifications.

## 6. Data retention and deletion

You're always in control of your own data:

- Delete a medication, stock entry, or history record any time from within the app.
- Uninstalling the app permanently deletes its local database — there is no server copy to remain behind.
- If you used Google Drive backup, remove the stored backup by revoking the app's access from your Google Account, which deletes its `appdata` folder.
- A local backup file you exported is yours to delete like any other file on your device.

## 7. Third-party services

The only third party involved at all is Google, and only if you opt into Drive backup — through the
Play Services Authorization API and the Drive API, both governed by
[Google's own Privacy Policy](https://policies.google.com/privacy).
The app includes no advertising networks, analytics SDKs, or crash-reporting services from any other
third party.

## 8. Children's privacy

Pills in Time isn't directed at children and isn't intended for use without adult supervision. We
don't knowingly collect information from children, and given the app collects nothing beyond what's
typed in locally, there is no data of any kind transmitted about any user, child or otherwise.

## 9. Changes to this policy

If this policy changes in a way that matters — for example, if a future feature introduces new data
handling — the "Effective" date above will be updated and, for material changes, noted on the app's
Play Store listing.

## 10. Contact

Questions about this policy or how the app handles your data can be sent to
[zel.gray@gmail.com](mailto:zel.gray@gmail.com).

---
Pills in Time — Privacy Policy · Last updated 2026-07-20
