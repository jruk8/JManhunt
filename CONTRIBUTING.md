# Contributing

Keep changes focused and run the full build before opening a pull request:

## Tests

Create tests where needed, only for pure logic.
Tests can be found in src/tests.

## Publish a snapshot

Tag the commit and change `gradle.properties` version on ongoing builds with a 
`v*.*.*-SNAPSHOT`-prefix.

## Build

```shell
./gradlew build
```

Follow the existing Java style and update the README when setup or user-facing
behavior changes.
 