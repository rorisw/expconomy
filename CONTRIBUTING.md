# Contributing to Expconomy

Thanks for helping out. These notes keep the code consistent.

Expconomy is licensed under GPL-3.0. By opening a pull request, you agree that your contribution is released under the same license.

## Getting started

1. Fork and clone the repo.
2. Install JDK 25.
3. Build with the wrapper:

   ```sh
   ./gradlew shadowJar
   ```

   You don't need Gradle installed. The wrapper downloads the right version.

4. The jar goes to `build/libs/`. Test it on a local Paper 1.21.4+ server.

## Project layout

- `src/main/java/dev/melishy/expconomy/`:
  - `command/` the `/xpc` command and tab completion
  - `economy/` XP math and balances
  - `storage/` YAML stores for balances, banks and fake accounts
  - `listener/` join, quit, death and respawn
  - `hook/` Vault and PlaceholderAPI
  - `message/` locale loading and formatting
- `src/main/resources/` `plugin.yml`, `config.yml` and locale files

## Code style

- Four-space indentation, UTF-8. `.editorconfig` covers the basics.
- The build compiles for Java 21 (`options.release = 21`) so the jar runs on Java 21 servers. Don't raise it, and don't use APIs newer than Java 21.
- Use the Paper API, not NMS.
- No telemetry.
- Write comments as plain sentences. Javadoc tags like `<p>`, `{@code}` and `{@link}` are fine.

## Commits and pull requests

- Keep commits focused and write clear messages.
- Update `CHANGELOG.md` under `[Unreleased]` for user facing changes.
- Make sure `./gradlew build` passes before opening a PR.
- Describe what changed and why in the PR. Link any related issue.

## Reporting bugs

Open an issue with the bug template. Include your server version, the plugin version, steps to reproduce, and any relevant console output.
