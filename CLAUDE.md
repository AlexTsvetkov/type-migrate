# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository.

## What this project is

**type-migrate** — Flyway for the SAP Commerce type system — versioned, reviewable schema migrations with a dry-run diff and safe rollback.

There is no Flyway/Liquibase for the Hybris type system. `system update` at deploy time is a black box: renames and removals are hand-tracked, blast radius is unknown, and rollback is prayer. It is the single most dangerous, least-tooled operation on the platform.

**Solution:** Versioned, git-checked type-system migrations applied deterministically per environment, with a **dry-run diff** ('this deploy drops column X, rebuilds index Y, touches 4M rows'), a blast-radius report, and a guided rollback plan.

> Status: early scaffold. The core abstraction, a starter implementation and tests are real; most capabilities are documented intent, not yet built. Do not claim features exist that aren't in the code.

## Stack

Java 21 + Gradle (`java-library` plugin), JUnit 5.

## Project layout

- `src/main/java/**` — production code (core abstraction: `MigrationPlanner`).
- `src/test/java/**` — JUnit 5 tests.
- `build.gradle`, `settings.gradle` — build config.
- `docs/` — GitHub Pages site (`index.html`, `.nojekyll`). Served at https://alextsvetkov.github.io/type-migrate/.
- `.github/workflows/ci.yml` — CI (build + test on push/PR).

## Common commands

```bash
gradle build      # compile
gradle test       # run tests
```

## Conventions

- Prefer **constructor injection**; interface + `Default*` impl per service.
- No inline literals — use constants classes for log/config/exception strings.
- Keep the core abstraction (`MigrationPlanner`) honest so implementations stay swappable.
- **Conventional commits** (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`).
- Generated code (if any) stays out of version control.
- Keep `README.md`, `docs/index.html` and this file in sync when the scope changes.

## Working agreements for agents

- This is part of a **suite of SAP Commerce backend tools**; keep terminology consistent with the sibling repos (e.g. `commerce-mcp`, `flow-context`).
- When adding real behaviour, update the Roadmap in `README.md` and add tests in the same PR.
- Don't introduce a live-backend dependency into the default build — keep the scaffold green on a clean checkout.
- If you change the public contract, reflect it in the docs site and the README capability table.
