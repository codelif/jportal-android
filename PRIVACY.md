# Privacy policy

JPortal for Android is an unofficial app for the JIIT webportal, made by Harsh Sharma. It isn't affiliated with or endorsed by JIIT.

Last updated: 20 October 2026

## What the app handles

You sign in with your JIIT Google account. Everything the app then shows comes from the JIIT webportal, for your own account: attendance, marks, grades, exams, subjects, profile, fees, hostel, bank details and feedback forms.

## Where your data goes

- **The JIIT webportal** (webportal.jiit.ac.in). Every request goes there, to read your data and, only when you submit feedback, to send your ratings. What JIIT does with it is covered by JIIT's own policies.
- **Google** (accounts.google.com), for the sign-in itself.
- **GitHub** (api.github.com), only if you turn on "Check for updates" in settings. It is off by default, and the Play Store build doesn't have it at all. While it is on, once a day the app asks GitHub whether a newer version exists. That request carries nothing about you beyond what any web request does: your IP address and the app's version.
- **GitHub** (raw.githubusercontent.com), for the planner's class timetable. It's one public file, the same for every student, fetched at most once a day or when you pull to refresh, and downloaded again only when it has changed. The request carries nothing about you either, just your IP address and the app's version. Picking your batch and electives happens on the phone.

Nothing is sent to the developer. The app has no analytics, no ads, no crash reporting and no tracking of any kind.

## What stays on your phone

- Your portal session, encrypted with a key held in the Android Keystore that can't be copied off the phone.
- A copy of your portal data, so the app works offline.
- Your settings, like theme and target attendance, and the batch and electives you picked in the planner.
- The planner's timetable.

The session and the portal data are left out of Android backups; only the settings can travel to a new phone. Signing out deletes the session, the portal data and your planner picks; the timetable stays, since it's the same for everyone. Uninstalling deletes everything.

## Debug report

"Copy debug report" under About copies a text report to your clipboard, with tokens, names and ids removed. It goes nowhere unless you paste it somewhere yourself.

## Children

The app is made for university students and isn't directed at children.

## Changes

This policy lives in the app's public repository, so every change to it is visible in its history.

## Contact

Open an issue at https://github.com/codelif/jportal-android/issues.
