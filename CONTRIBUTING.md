# Contributing

Thanks for wanting to help. JPortal is maintained by one person in their spare
time, so a few ground rules keep things sane.

## Talk first

Open an issue before writing code for a new feature, or for anything that changes
how the app looks or behaves. Say what you want to build and how you plan to do it,
then wait for a go-ahead. A feature PR that was never discussed may be closed, no
matter how much work went into it.

Bug fixes, typos and small cleanups can go straight to a PR.

## Design

- Follow the [Material 3 (Material You)](https://m3.material.io/) guidelines.
- Treat the existing screens as the reference. Prefer what is already in
  `ui/components` and `ui/theme` (`ScreenScaffold`, `MessageState`, the color
  scheme, `LocalExtraColors`) over building your own. When nothing there fits,
  a new component is fine, but it should look like it belongs, and it's worth
  raising in the issue.
- No hardcoded colors. Everything comes from the theme, so dynamic color, dark
  mode and AMOLED keep working.
- Agree on the look in the issue before building it. A mockup or sketch there
  saves a redesign later.
- PRs that touch UI include screenshots, light and dark.

## Code

- Match the style of the code around you.
- Portal data goes through `Repository`, `Store` and `Cache`. Settings go through
  `Prefs`. Don't add a parallel networking or storage layer.
- Don't commit large data files (scraped JSON, dumps and the like). Data that
  changes belongs on a remote source the app fetches.
- Comments are lowercase, short, and only there when the code can't speak for
  itself.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/),
  one line, lowercase: `feat(exams): timeline date sheet`,
  `fix(subject): ...`.
- Signed commits aren't required, but they're recommended.
- Run the tests with `./gradlew :app:testGithubDebugUnitTest`. Screens are
  covered by screenshot tests. If you change a screen, re-record with
  `./gradlew :app:recordRoborazziGithubDebug` and commit the new images.
- Build setup is in the [README](README.md#building).

## AI usage

This project is not anti-AI. Use whatever tools you like, but:

- You answer for every line you submit. If you can't explain a change, and how
  it fits the rest of the app, without asking an AI, don't submit it.
- Issues and PR descriptions drafted with AI must be reviewed and trimmed by you.
  Keep them short and in your own words.
- No AI-generated media: art, icons, images, video or audio.
- Slop is not accepted. What counts as slop is the maintainer's call, and those
  PRs may be closed unmerged without notice.
