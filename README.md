# type-migrate

**Flyway for the SAP Commerce type system — versioned, reviewable schema migrations with a dry-run diff and safe rollback.**

**🌐 Live site: https://alextsvetkov.github.io/type-migrate/**

> ⚠️ **Status:** early scaffold. The core abstraction, a starter implementation and tests are real; this is a foundation to build on, not a finished product. See [Roadmap](#roadmap).

**Stack:** Java 21 + Gradle.

---

## The problem

There is no Flyway/Liquibase for the Hybris type system. `system update` at deploy time is a black box: renames and removals are hand-tracked, blast radius is unknown, and rollback is prayer. It is the single most dangerous, least-tooled operation on the platform.

## The solution

Versioned, git-checked type-system migrations applied deterministically per environment, with a **dry-run diff** ('this deploy drops column X, rebuilds index Y, touches 4M rows'), a blast-radius report, and a guided rollback plan.

See the [project site](https://alextsvetkov.github.io/type-migrate/) for the full benefits narrative.

## Design principles

1. **Plan before apply** — Every migration produces a reviewable diff and impact report before a single DDL statement runs.
2. **Deterministic order** — Migrations are versioned and applied in a fixed, recorded sequence per environment — no drift between d1/s1/p1.
3. **Reversible by design** — Each migration declares its inverse so rollback is a first-class, tested path.
4. **Type-system aware** — Understands items.xml deployment tables, indexes and the region cache — not just raw SQL.

## Core abstraction

`MigrationPlanner` — Diffs a target type model against the deployed schema and returns an ordered, reversible migration plan with an impact estimate.

## Features

| Capability | Description |
|------------|-------------|
| ``plan`` | Diff target items.xml against the deployed schema; print DDL + row-impact estimate. |
| ``apply`` | Execute pending migrations transactionally with an audit record. |
| ``rollback`` | Apply the declared inverse of the last migration. |
| ``status`` | Show applied/pending migrations per environment. |

## Quick start

```bash
gradle build
gradle test
```

## Roadmap

- [ ] Flesh out the core beyond the starter implementation.
- [ ] Wire against a live SAP Commerce / BTP environment.
- [ ] Publish artifacts and usage docs.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md). Conventional commits; generated code stays out of version control.

## License

[MIT](./LICENSE) © 2026 Aliaksandr Tsviatkou

## Honest assessment

> From the v2 self-critical analysis. Scores use **Gap · Value · Moat · Time-to-revenue · Risk** (for Risk, **higher = safer**). Prior art is named deliberately — "no competitor" is almost never true.

**Scores:** Gap 3 · Value 4 · Moat 3 · TTR 2 · Risk 2 (high blast radius)

- **Prior art / competition.** SAP *system update* + essential/project-data ImpEx exist; community migration approaches exist. The gap is *safety tooling* (dry-run diff, blast radius, rollback), not 'no mechanism'.
- **True differentiator.** Trustworthy dry-run + rollback around an operation people are scared of.
- **Kill criterion.** If a design-partner DBA won't let it run non-read-only in a pre-prod env within ~2 months, the trust curve is too steep for a small team.
- **Verdict.** **Defer.** Highest blast radius (writes to prod schemas) + long trust cycle; only after a trusted install base exists.

See the full landscape, go-to-market and the **IP / conflict-of-interest** discussion in [sap-commerce-general-ideas-for-startup.md](https://github.com/AlexTsvetkov/sap-commerce-ideas-for-projects/blob/main/ideas-for-startup/sap-commerce-general-ideas-for-startup.md).

---

*Part of a backend tooling suite for SAP Commerce Cloud. See [`commerce-mcp`](https://github.com/AlexTsvetkov/commerce-mcp) for the AI-native flagship.*
