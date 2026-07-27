# Paper plugin template

A minimal, buildable starting point for Paper plugins using Gradle and Java 25.
It intentionally provides no commands, listeners, configuration, or other plugin
features.

## Start a plugin

1. Create a repository from this template.
2. Update the plugin values in `gradle.properties`.
3. Rename the example package and `PaperTemplatePlugin` class to match the
   `pluginMain` value.
4. Change `rootProject.name` in `settings.gradle`.
5. Add your plugin code and metadata. Commands and permissions belong in
   `src/main/resources/plugin.yml`.
6. Choose a license before publishing your project.

## Build

Java 25 is required. Gradle can use a locally installed matching toolchain.

```shell
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

The plugin jar is written to `build/libs/`. The included GitHub Actions workflow
runs the same build for pushes and pull requests.
