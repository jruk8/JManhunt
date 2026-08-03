# Contributing to JManhunt

Thank you for your interest in contributing to JManhunt! This document outlines
the project philosophy, coding conventions, testing expectations, commit style,
documentation expectations, and pull request guidelines.

## Project Philosophy

JManhunt is a Minecraft Manhunt plugin built on Paper. The project values:

- **Simplicity:** Prefer straightforward, readable solutions over clever
  abstractions.
- **Reliability:** The game must never crash because of a feature failure.
  Structure placement, command execution, and outcome resolution should fail
  gracefully and log warnings rather than throwing unhandled exceptions.
- **Configurability:** Game behavior should be tunable through `config.yml`
  and `messages.yml` without requiring code changes.
- **Performance:** Avoid unnecessary work on the main server thread. Cache
  expensive lookups and use scheduled tasks for recurring operations.

## Coding Conventions

- **Java 25** is the target. Use modern language features (records, switch
  expressions, pattern matching) where appropriate.
- **Checkstyle** is enforced. Run `./gradlew check` before submitting to
  catch style violations. Key rules:
  - 4-space indentation, no tabs.
  - Lines should not exceed 120 characters.
  - `final` is required for local variables that are not reassigned.
  - `final` is required for method parameters that are not reassigned.
  - Unused imports are not allowed.
- **Naming:** Use descriptive names. Avoid abbreviations except for well-known
  ones (`id`, `uuid`, `nbt`).
- **Null safety:** Use `@NotNull` and `@Nullable` annotations from
  `org.jetbrains.annotations` where the nullability is not obvious.
- **Thread safety:** Bukkit operations must run on the main thread. Use
  `Bukkit.getScheduler().runTask()` to schedule tasks from async contexts.
- **Error handling:** Catch exceptions at the boundary (event handlers,
  command executors) and log them with context. Never let an exception
  propagate to the server.

## Testing Expectations

- **Logic-based tests** are required for new features. Use JUnit 5.
- Tests should not require a running Minecraft server. Mock or stub Bukkit
  dependencies where needed.
- **Do not add runtime or integration tests.** Tests that require a live
  server, a database, or network access will be rejected.
- Place tests in `src/tests/java/` mirroring the main source package structure.
- Run tests with `./gradlew test` before submitting.

## Commit Message Style

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[optional scope]: <description>

[optional body]
```

Types:

- `feat` — a new feature
- `fix` — a bug fix
- `docs` — documentation only changes
- `refactor` — code changes that neither fix a bug nor add a feature
- `test` — adding or fixing tests
- `chore` — maintenance tasks (dependencies, build config, etc.)

Example:

```
feat: add STRUCTURE lucky block outcome type

Add support for placing .nbt structures from the lucky-block
challenges/lucky-block/structures/ directory. Includes reroll
logic for failed placements and random rotation support.
```

## Documentation Expectations

- Update `docs/` when adding or changing user-facing features.
- Update `docs/commands.md` for new commands.
- Update `docs/configuration.md` for new configuration options.
- Update `docs/permissions.md` for new permissions.
- Update `docs/getting-started.md` for new workflows.
- Keep documentation accurate and concise.

## Pull Request Guidelines

1. **Branch** from `main` with a descriptive name (e.g.
   `feat/structure-outcomes`).
2. **Commit** using the style above. Squash fixup commits before submitting.
3. **Test** — ensure `./gradlew test` passes.
4. **Build** — ensure `./gradlew build` passes (includes checkstyle).
5. **Document** — update relevant docs.
6. **Describe** — write a clear PR description explaining what and why.
7. **Review** — respond to feedback promptly. CI must pass before merge.

## Code of Conduct

Be respectful and constructive in all interactions. Harassment or
discriminatory behavior of any kind will not be tolerated.
