# PlayerSettings 0.1.6 publication

This repository publishes the cumulative PlayerSettings implementation through 0.1.6.
The release is at https://github.com/Usasi103/PlayerSettings/releases/tag/v0.1.6.

The JAR, resource pack and source archive are the exact files validated and deployed
before GitHub credentials became available. Their SHA-256 values are recorded in
`release/SHA256SUMS.txt`. Older release notes and validation entries mentioning
pending GitHub publication describe the state at packaging time; this publication
completes that pending step. The source archive retains those historical notes
unchanged. Repository-only publication metadata does not change the plugin build.

The release includes:

- `PlayerSettings-0.1.6.jar`
- `PlayerSettings-resourcepack-0.1.6.zip`
- `PlayerSettings-source-0.1.6.zip`
- `SHA256SUMS.txt`

For a complete build checkout, use this repository. It includes the existing
PlaceholderAPI compile-only dependency under `libs/`, which is omitted from the
hand-prepared source archive. With JDK 25 installed, run `gradlew.bat test build`
(or `./gradlew test build`). Gradle runs the source-format check as part of `build`.
The artifact is exported to `dist/`; build intermediates remain outside the project.

See `CHANGELOG.md` for cumulative changes and `VALIDATION.md` for the nine passing
tests, client verification, persistence checks and resource-pack validation.
