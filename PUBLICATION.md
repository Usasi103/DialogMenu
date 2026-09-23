# PlayerSettings publication

The cumulative 0.1.9 release URL is https://github.com/Usasi103/PlayerSettings/releases/tag/v0.1.9. The publication workflow verifies the remote main/tag commit, Release notes, attachment digests and preservation of earlier releases and private visibility.

Release attachments are the actual built PlayerSettings-0.1.9.jar, matching resource pack, source archive and SHA256SUMS.txt. Repository checksums are in release/SHA256SUMS.txt. The source archive excludes local dependencies and release metadata; the Git checkout includes the existing compile-only PlaceholderAPI JAR.

With JDK 25 installed, use gradlew.bat test build. Build intermediates remain outside the project. See CHANGELOG.md, RELEASE-0.1.9.md and VALIDATION.md for behavior, limits and checks. Chinese configuration examples are in src/main/resources/配置说明.md and are exported to the plugin directory on first startup.
