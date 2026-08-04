# AGENTS.md

Project guidance for AI coding agents working on the JManhunt repository.

## Project conventions

- Follow the existing architecture.
- Reuse existing services before introducing new ones.
- Avoid duplicating logic.
- Keep changes minimal and consistent.

## Architecture

- Event listeners should remain thin.
- Business logic belongs in services.
- Configuration parsing belongs in engines/loaders.
- Avoid introducing static state unless already used consistently.

## Configuration

- Default configuration belongs in `src/main/resources/`.
- Runtime configuration belongs in the plugin data folder.
- Never hardcode paths.

## Commands

- Register commands through the existing command framework.
- Support tab completion where appropriate.
- Console execution should be supported unless impossible.

## Documentation

Whenever user-facing behaviour changes:

- Update the relevant MkDocs page.
- Don't be afraid to add new pages for new features.
- Keep README.md concise.
- Update CONTRIBUTORS.md if contributor workflow changes.

## Testing

- Prefer logic/unit tests.
- Do not add integration tests unless explicitly requested.
- Build the project before committing.

## Commits

- Use Conventional Commits.
- Never push unless explicitly requested.

## Common pitfalls

- Support both players and console as command senders.
- Consider reload behaviour when introducing new configuration.
- Add permission checks to new commands.
- Update tab completion when introducing new subcommands.
- Preserve Legacy and MiniMessage compatibility.
