# CLAUDE.md

Project instructions for Claude Code working in this repository.

## What this is

A small **Policy Premium service** built as a take-home assessment.

**The functional feature is deliberately trivial and is explicitly not what is scored.**
The assessment scores: repo & git hygiene, CI/CD pipeline, test strategy,
containerization/IaC, code quality, documentation rationale, and agentic-AI fluency.

**Time-box: ~3-4 hours total.** Prioritization under constraint is being assessed.
When a trade-off appears, spend effort on delivery quality (pipeline, tests, docs)
and *not* on the premium logic. Resist gold-plating.

## Stack

- Java 21, Spring Boot 4.1.x (note: Boot 4 renamed starters, e.g. `spring-boot-starter-webmvc`)
- Gradle via the wrapper (`./gradlew`), Kotlin DSL (`build.gradle.kts`)
- JUnit 5 + AssertJ, Spring MockMvc
- Spotless + Checkstyle (lint), JaCoCo (report only, no gate)
- springdoc-openapi (Swagger UI)
- Docker multi-stage, GitHub Actions

## Commands

```bash
./gradlew check               # compile + lint + tests + jacoco report (the CI gate)
./gradlew test                # tests only
./gradlew spotlessApply       # auto-format
./gradlew bootRun             # run locally on :8080
./gradlew bootJar             # build the runnable jar
docker compose up --build     # run containerized
```

Swagger UI: `http://localhost:8080/swagger-ui.html` · Health: `/actuator/health`

## Architecture

```
src/main/java/com/example/policypremium/
  api/     QuoteController, dto/, GlobalExceptionHandler
  domain/  CoverageType, Region, PolicyAttributes, Quote
  rules/   PremiumCalculator, RateTable
  store/   QuoteStore (interface), InMemoryQuoteStore
```

**Design rules:**
- `domain/` and `rules/` are **pure Java - no Spring annotations, no framework imports.**
  The calculator must be testable with zero application context.
- Storage sits behind the `QuoteStore` interface so the swap point to real persistence is
  visible without building it.
- All money is `BigDecimal`. Never `double`/`float` for money.
- Round **once, at the end**, `HALF_UP`, 2dp. Intermediate factors stay unrounded.

## Business rules (source of truth)

Currency **SEK**.

```
premium = baseRate(coverageType)
        x regionFactor(region)
        x sumInsuredFactor(sumInsured)
        x claimsLoading(priorClaimsCount)
        -> max(result, MINIMUM_PREMIUM)
```

| Component | Values |
|---|---|
| baseRate | HOME 1200 · AUTO 2400 · LIABILITY 800 |
| regionFactor | STOCKHOLM 1.25 · GOTEBORG 1.15 · MALMO 1.10 · NORTH 0.90 · OTHER 1.00 |
| sumInsuredFactor | <=500k 1.00 · 500k-1M 1.15 · 1M-5M 1.35 · >5M 1.60 |
| claimsLoading | 0 -> 1.00 · 1 -> 1.20 · 2 -> 1.45 · 3 -> 1.75 · 4+ -> 2.00 (capped) |
| MINIMUM_PREMIUM | 500 SEK |

Band boundaries are **inclusive lower, exclusive upper**. This is a stated assumption and
must be covered by boundary tests.

Validation: `coverageType`/`region` required enums · `sumInsured` > 0 and <= 100,000,000 ·
`priorClaimsCount` >= 0 and <= 50.

## API

- `POST /api/v1/quotes` -> `201` + `Location`; body has id, premium, currency, echoed
  input, `createdAt`, and a factor-by-factor `breakdown` (incl. `minimumPremiumApplied`).
  The breakdown exists to make the rules auditable from the response.
- `GET /api/v1/quotes/{id}` -> `200`, or `404` if unknown.
- Errors use RFC 7807 `ProblemDetail` (built into Spring 6).

## Testing

Mix chosen for judgement, **not coverage percentage**:
- Parameterized unit tests over the rate table: band edges, claims cap, minimum-premium
  floor, rounding at the cent.
- Web-slice tests for validation and error contracts.
- One `@SpringBootTest` round-trip: create -> retrieve -> 404 on unknown id.

JaCoCo produces a report as a CI artifact. **There is deliberately no coverage threshold**
- the README explains why. Do not add one.

## CI/CD

- `ci.yml` (push + PR): setup-java 21 + `gradle/actions/setup-gradle` caching ->
  `./gradlew check` -> publish test reports -> build Docker image -> **run the container
  and curl `/actuator/health`** -> Trivy scan the built image.
- `cd.yml` (main): **fully mocked. No registry push, no real deploy.** The deploy job is
  explicitly labelled MOCK and echoes the commands it would run.
- Pin action versions, least-privilege `permissions`, concurrency groups.

## Workflow

Public GitHub repo. Work is tracked as **issues created up front**, each delivered through
its own short-lived branch and PR. `main` is protected: changes land only via PR with a
passing CI check.

Loop per issue:

1. Claude implements the change and runs `./gradlew check` locally until green.
2. Claude reports the branch name and a Conventional Commit message.
   **The developer commits and pushes.**
3. Claude opens the PR with `gh pr create`, linking the issue with `Closes #N`.
4. Claude reports CI status; on green, Claude **asks before squash-merging**.
5. Squash-merge closes the issue automatically.

## Git

**Claude must not run `git commit`, `git push`, or create remotes - ever.**
Committing and pushing are the developer's, always.

Claude *may* use `gh` for issues and pull requests. Merging requires explicit confirmation
each time.

- **Many small commits telling a real story** - the brief explicitly penalizes one giant
  "initial commit". Land the CI pipeline *before* the feature code so the history shows a
  CI-first progression rather than a retrofit.
- Squash-merge one PR per issue, so every commit on `main` traces to an issue and a green
  check.
- **Never commit secrets.** `reqs.pdf` is gitignored and must stay out of the repo.

## Deliberately out of scope

Do not build these; document them in the README instead:
real persistence · auth/authz · rate limiting · Testcontainers · registry publishing ·
any real cloud deploy · multi-currency · actuarial sophistication in the rules.
