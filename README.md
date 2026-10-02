<p align="center">
  <img src="assets/Sketchware-Neo.png" width="220" alt="Sketchware Neo">
</p>

<h1 align="center">Sketchware Neo</h1>

<p align="center">
  A community-driven continuation of Sketchware Pro for Android.
</p>

<p align="center">
  <a href="https://github.com/sketchlibx/SketchwareNeo/graphs/contributors"><img src="https://img.shields.io/github/contributors/sketchlibx/SketchwareNeo" alt="Contributors"></a>
  <a href="https://github.com/sketchlibx/SketchwareNeo/commits/"><img src="https://img.shields.io/github/last-commit/sketchlibx/SketchwareNeo" alt="Last commit"></a>
  <a href="https://github.com/sketchlibx/SketchwareNeo/releases/latest"><img src="https://img.shields.io/github/v/release/sketchlibx/SketchwareNeo" alt="Latest release"></a>
  <a href="https://github.com/sketchlibx/SketchwareNeo/releases"><img src="https://img.shields.io/github/downloads/sketchlibx/SketchwareNeo/total" alt="Downloads"></a>
  <a href="https://t.me/sketchwareneo"><img src="https://img.shields.io/badge/Telegram-sketchwareneo-26A5E4?logo=telegram" alt="Telegram"></a>
</p>

## About

Sketchware Neo continues the development of Sketchware Pro with community contributions, maintenance work, new features, and ongoing improvements.

The project is based on and continues to include updates from [Sketchware Pro](https://github.com/Sketchware-Pro/Sketchware-Pro).

> **Important:** Back up your Sketchware projects before using development or modified builds. Unexpected build, migration, or compatibility issues can occur.

## Building

The project uses Gradle. Android Studio is recommended for the easiest development setup.

### Core source map

| Class | Purpose |
| --- | --- |
| `a.a.a.ProjectBuilder` | Builds a complete Sketchware project |
| `a.a.a.Ix` | Generates `AndroidManifest.xml` |
| `a.a.a.Jx` | Generates activity source code |
| `a.a.a.Lx` | Generates component/listener source code |
| `a.a.a.Ox` | Generates layout XML |
| `a.a.a.qq` | Manages built-in library dependencies |
| `a.a.a.tq` | Handles compile-dialog quizzes |
| `a.a.a.yq` | Organizes project file paths |

## Plugins

Sketchware Neo supports runtime plugins without rebuilding the base APK.

A plugin is a `.jar` or `.apk` containing a class that implements `NeoPluginInterface` and a `plugin.json` at the archive root:

```java
public interface NeoPluginInterface {
    String getPluginId();
    int getPluginApiVersion();
    void onLoad(NeoPluginContext context) throws Exception;
    void onUnload();
    // Optional: getDrawerEntries(), getBlockContributions(),
    // onBuildError(), onBuildSuccess()
}
```

Example `plugin.json`:

```json
{
  "pluginId": "com.example.myplugin",
  "entryClass": "com.example.myplugin.MyPlugin",
  "apiVersion": 1
}
```

Build the plugin with your preferred setup. A normal Android app module is sufficient, and `assembleDebug` produces usable DEX output.

Place the built file in `.sketchware/plugins/`, or install it from **App Settings → Plugins**. Plugins can be enabled, disabled, or removed from the same screen.

### Current limitations

Runtime plugins cannot currently register a completely new full-screen Activity through the normal Android manifest system. Block and palette contributions are also available in the API, but still need validation against real block specifications.

For either area, open a [GitHub Discussion](https://github.com/sketchlibx/SketchwareNeo/discussions).

## Contributing

1. Fork the repository.
2. Create a branch for your change.
3. Make focused changes and test them.
4. Open a pull request with a clear description.
5. Maintainers will review the changes before merging.

Contributions of all sizes are welcome, including bug fixes, new features, performance improvements, UI/UX work, documentation, and testing.

For changes that stay within `pro.sketchware`, keep the existing package structure and naming conventions. Prefer Java for new code unless Kotlin is necessary.

### Commit messages

Keep commit messages short, specific, and clear. A good commit should make it easy to understand what changed without reading the entire diff.

## Acknowledgements

Sketchware Neo exists because of the original Sketchware and the Sketchware Pro community. We appreciate the developers and contributors whose work made continued development possible.

## Disclaimer

Sketchware Neo is a community project intended to preserve and continue Sketchware development. Use it at your own discretion and keep backups of your projects.

See [LICENSE.md](LICENSE.md) for the project's source-available terms and third-party licensing information.
