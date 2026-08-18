# Policy Premium Service

[![CI](https://github.com/volodimier/policy-premium-service/actions/workflows/ci.yml/badge.svg)](https://github.com/volodimier/policy-premium-service/actions/workflows/ci.yml)

Calculates a policy premium from a few attributes and stores the result so it can be
retrieved later. Two endpoints, deliberately small.

A quote is **immutable once issued**: retrieving one returns the numbers it was calculated
with, never a fresh calculation. Every response carries a factor-by-factor breakdown, so a
premium can be audited without re-running the rules or reading the source.

All amounts are in **SEK**.

---

## Quick start

### Docker (nothing else needed)

```bash
docker compose up --build
```

The service listens on <http://localhost:8080>.

### Build and verify in one command

Runs the full quality gate — formatting, static analysis and all tests — inside the build,
with no local JDK or Gradle required:

```bash
docker build --target test .
```

Nothing depends on that stage, so a normal `docker build .` skips it entirely and stays fast.

### Locally with a JDK 21

```bash
./gradlew bootRun
```

Other useful tasks:

| Command | Does |
|---|---|
| `./gradlew check` | Compile, format check, static analysis, tests, coverage report |
| `./gradlew spotlessApply` | Reformat the code |
| `./gradlew bootJar` | Build the runnable jar |

---

## API

| Method | Path | Returns |
|---|---|---|
| `POST` | `/api/v1/quotes` | `201` with the quote and a `Location` header |
| `GET` | `/api/v1/quotes/{id}` | `200` with the stored quote, or `404` |
| `GET` | `/actuator/health` | Liveness and readiness |

Errors use [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) problem details, served as
`application/problem+json`:

| Status | When |
|---|---|
| `400` | A constraint was violated. The body lists every failing field, not just the first. |
| `400` | The body could not be parsed — for example an unknown coverage type. The response lists the accepted values. |
| `400` | The id in the path is not a UUID. |
| `404` | No quote exists with that id. |

An unknown enum value is worth calling out: it fails during deserialisation, before validation
runs, so without explicit handling the framework reports it as a `500` — blaming the server for
a caller's typo.

---

## OpenAPI and Swagger UI

| | |
|---|---|
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| OpenAPI document | <http://localhost:8080/v3/api-docs> |

The spec is annotated rather than left to defaults. Out of the box the generated document
claimed both endpoints returned `200`, documented no error responses, and described every
payload as `*/*` — a spec a client would code against and get wrong. It now states the real
status codes, the `problem+json` error shape, and the response schema for each.

Request fields carry examples, so "Try it out" is pre-filled with a working request.

---

## Examples

Calculate a premium:

```bash
curl -i -X POST http://localhost:8080/api/v1/quotes -H "Content-Type: application/json" -d '{"coverageType":"AUTO","sumInsured":750000,"region":"MALMO","priorClaimsCount":2}'
```

```json
{
  "id": "b387aa9a-b628-4578-90f0-f6b13b97885d",
  "premium": 4402.20,
  "currency": "SEK",
  "input": { "coverageType": "AUTO", "sumInsured": 750000, "region": "MALMO", "priorClaimsCount": 2 },
  "breakdown": {
    "baseRate": 2400,
    "regionFactor": 1.10,
    "sumInsuredFactor": 1.15,
    "claimsLoading": 1.45,
    "calculatedPremium": 4402.200000,
    "minimumPremiumApplied": false
  },
  "createdAt": "2026-08-18T17:42:57.426719048Z"
}
```

Retrieve it — same body, because nothing is recalculated:

```bash
curl -s http://localhost:8080/api/v1/quotes/b387aa9a-b628-4578-90f0-f6b13b97885d
```

The minimum premium engaging (`720.00` raised to `750.00`):

```bash
curl -s -X POST http://localhost:8080/api/v1/quotes -H "Content-Type: application/json" -d '{"coverageType":"LIABILITY","sumInsured":100000,"region":"NORTH","priorClaimsCount":0}'
```

A validation failure, reporting every bad field at once:

```bash
curl -s -X POST http://localhost:8080/api/v1/quotes -H "Content-Type: application/json" -d '{"coverageType":null,"sumInsured":-5,"region":"STOCKHOLM","priorClaimsCount":99}'
```

An unknown coverage type — `400` listing what is accepted, not a `500`:

```bash
curl -s -X POST http://localhost:8080/api/v1/quotes -H "Content-Type: application/json" -d '{"coverageType":"MOTORCYCLE","sumInsured":300000,"region":"STOCKHOLM","priorClaimsCount":0}'
```

---

## Business rules

These are invented for the exercise. They are deliberately simple, and chosen so the
interesting cases — a band edge, a cap, a floor — are reachable and testable.

```
premium = baseRate(coverageType)
        × regionFactor(region)
        × sumInsuredFactor(sumInsured)
        × claimsLoading(priorClaimsCount)

        → raised to MINIMUM_PREMIUM if below it
        → rounded to 2dp, HALF_UP
```

**Base rate** by coverage type:

| Coverage | Base rate |
|---|---|
| `HOME` | 1 200 |
| `AUTO` | 2 400 |
| `LIABILITY` | 800 |

**Region factor:**

| Region | Factor |
|---|---|
| `STOCKHOLM` | 1.25 |
| `GOTEBORG` | 1.15 |
| `MALMO` | 1.10 |
| `NORTH` | 0.90 |
| `OTHER` | 1.00 |

**Sum insured factor** — bands are **inclusive of the lower bound, exclusive of the upper**:

| Sum insured | Factor |
|---|---|
| 0 – 500 000 | 1.00 |
| 500 000 – 1 000 000 | 1.15 |
| 1 000 000 – 5 000 000 | 1.35 |
| 5 000 000 and above | 1.60 |

So a sum insured of exactly 500 000 falls in the **second** band, not the first.

**Prior claims loading**, capped at 4 claims:

| Claims | Loading |
|---|---|
| 0 | 1.00 |
| 1 | 1.20 |
| 2 | 1.45 |
| 3 | 1.75 |
| 4 or more | 2.00 |

**Minimum premium:** 750 SEK.

### Worked example

`AUTO`, 750 000 insured, `MALMO`, 2 prior claims:

```
2400  ×  1.10  ×  1.15  ×  1.45  =  4402.20
base     region  band    claims
```

Above the 750 floor, so it stands: **4 402.20 SEK**.

---

## Assumptions

- **Band boundaries are half-open** — inclusive lower, exclusive upper. The rules could be read
  either way at 500 000, and consistent half-open bands are the conventional treatment; the
  alternative would make the first band uniquely inclusive at both ends.
- **Rounding happens once, at the very end**, `HALF_UP`. Rounding intermediate factors would
  let error accumulate across four multiplications and make the result depend on the order
  they were applied in.
- **The floor is compared against the unrounded figure**, so a premium a fraction below the
  minimum is treated as below it rather than rounded up to meet it.
- **`sumInsured` selects a band; it does not scale the premium.** Doubling the sum insured
  within a band does not change the price.
- **SEK only.** No currency conversion, no multi-currency support.
- **A quote is immutable once issued.** `GET` returns stored numbers, never a recalculation, so
  a rate change cannot silently reprice a quote a customer was already given.
- **Money is `BigDecimal` throughout**, never `double`.
- **Validation bounds** — `sumInsured` in (0, 100 000 000], `priorClaimsCount` in [0, 50] — are
  sanity limits, not business rules.

---

## Test strategy

93 tests, ~95% line coverage. The mix is chosen so each layer can fail for a reason no other
layer can:

| Layer | What it covers | Why there |
|---|---|---|
| **Rules unit tests** | Every published rate, all band boundaries, the claims cap, the floor | The rules are a pure function with no Spring context, so exhaustive cases cost milliseconds |
| **Store unit tests** | Storage semantics, including 50 concurrent writes across 8 threads | A web container serves on many threads; concurrency is the normal case, not an edge case |
| **Web-slice tests** | Status codes, headers, response shape, every validation failure mode | Contract detail is cheap here and expensive end to end |
| **One integration test** | Real HTTP against a real container: create → retrieve → compare | Proves the pieces are wired together, which no slice can |

The strongest assertion in the suite is that the `POST` and `GET` response bodies are
**byte-identical**. If retrieval ever started recalculating, that test fails immediately.

### Coverage is measured but not gated

Every pull request shows test counts, line and branch coverage, and static-analysis findings
as a comment. **There is deliberately no coverage threshold.**

A threshold measures how much code was executed, not whether it was checked. It reliably
produces tests written to reach a number. The numbers are published on every PR so a reviewer
can judge them — which is the point of measuring — but they cannot fail a build on their own.

---

## CI/CD

One pipeline, so the whole delivery path is visible in a single graph:

```
verify ──┬─→ quality
         └─→ image ──→ deploy to staging (MOCK)   [main only]
```

| Job | Does | Blocks merge? |
|---|---|---|
| `verify` | Compile, Spotless, Checkstyle, all tests, coverage report | **Yes** — required status check |
| `quality` | Publishes tests, coverage and findings to the PR | No — reporting only |
| `image` | Builds the container, asserts non-root, boots it, exercises both endpoints against it, scans it | Yes, on failure |
| `deploy` | Mocked deployment | Skipped on PRs |

`main` is protected: changes land only through a pull request with `verify` passing.

**The image is smoke-tested, not just built.** CI runs the container and drives real requests
through it, because an image can be broken by a bad base, a missing layer or a permissions
mistake that no unit test would ever catch.

**The deployed artifact is provably the tested one.** The `image` job exports the exact image
that passed the smoke test and the vulnerability scan; the deploy job loads it and asserts the
digest matches. It is never rebuilt — and since the runtime stage upgrades OS packages, a
rebuild of the same commit genuinely can differ.

### What is mocked

**Nothing is published and nothing is deployed.** The deploy job prints the commands it would
run — registry login, push, rollout, health verification, rollback — rather than executing
them. The job is labelled `(MOCK)` and its run summary says so.

Everything around it is real: gating on green lint, tests and a scanned container, deriving an
immutable tag from the commit SHA, promoting the tested artifact, and the environment gate.

Two things would make it real:

1. A registry and credentials, so the image is published rather than discarded.
2. A deployment target and its secrets, so rollout, verification and rollback can run.

---

## Security

- **The container runs as an unprivileged user**, asserted by a CI step rather than assumed.
  Application files stay root-owned, so the process can read its own code but not modify it.
- **Images are scanned with Trivy** on every build, failing on HIGH or CRITICAL findings that
  have a fix available. Failing on *unfixable* base-image CVEs would block unrelated changes
  with no remedy beyond a suppression, which teaches people to suppress rather than fix.
- **The runtime stage upgrades OS packages.** Base images lag the package repositories, and
  without this the image shipped known-fixable CVEs. This is why the scan currently reports
  zero.
- **Dependabot** watches Gradle dependencies and GitHub Actions weekly.
- **Actions are pinned to commit SHAs**, not tags, so a moved tag cannot change what runs.
- No secrets are committed, and the pipeline needs none — every job runs with least-privilege
  permissions, and only the reporting job may write to pull requests.

---

## Deliberately left out

| Not built | Why                                                             |
|---|-----------------------------------------------------------------|
| Persistence | Out of scope                                                    |
| Authentication and authorisation | No user model in scope                                          |
| Rate limiting and quotas | Out of scope                                                    |
| Registry publishing and real deployment | No target available; mocked and labelled as such                |
| Actuarial sophistication | The brief asks for simple documented rules, not a rating engine |
| Structured logging, metrics, tracing | Actuator health is enough at this size                          |

---

## With more time

1. **Real persistence** behind the existing `QuoteStore` interface, with schema migrations and
   a Testcontainers-backed integration test.
2. **Observability** — structured JSON logs with correlation ids, metrics on quote volume and
   premium distribution, and tracing.
3. **Contract testing** against the published OpenAPI document, so the spec cannot drift from
   the implementation.
4. **Finish the deployment path** — publish to a registry and deploy to a real environment,
   with the rollout, health verification and rollback steps the mock currently prints.
5. **Additional security and quality checks in the pipeline** — things like Snyk and/or Sonar scans
