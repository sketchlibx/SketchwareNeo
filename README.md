# Sketchware Neo

A modern, actively maintained continuation of Sketchware Pro.

This repository contains the data files that power the **About** screen in the Sketchware Neo app (project info, changelog, beta status, contributors, and announcements).

## Features

- Android SDK 36 support
- Material 3 UI
- Cloud Backup
- Dependency Manager
- Modern Build System
- Active Development
- Community Driven

## Links

- Website: https://SketchLib.fun
- Repository: https://github.com/sketchlibx/SketchwareNeo
- Telegram: https://t.me/sketchlibneo

## About data files

| File | Purpose |
|---|---|
| `about.json` | Project identity and core team |
| `changelog.json` | Release history, newest entries appended to the top |
| `beta.json` | Current beta availability status |
| `contributors.json` | Contributor list and call to action |
| `announcements.json` | In-app announcements |

All files are plain JSON, parsed with Gson on the Android side. Schemas are intentionally flat and additive so new entries can be appended without breaking existing parsers.

## Contributions

Pull Requests are welcome. Open one on GitHub or reach out through [SketchLib.fun](https://SketchLib.fun).

## License

Sketchware Neo is a continuation of Sketchware Pro. Review this repository's `LICENSE` file for current terms — if it still references Sketchware Pro, update the project name/copyright holder while keeping the original license type intact to preserve legal compatibility.
