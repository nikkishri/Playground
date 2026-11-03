# CLAUDE.md

## Project
- What: Gradle multi-project Java app scaffold (single subproject `app`, trivial CLI entry point)
- Stack: Java 21, Gradle 9.8.1, JUnit Jupiter 5
- Layout: `app/src/main/java/playground/` source · `app/src/test/java/playground/` tests · `gradle/libs.versions.toml` dependency catalog · `settings.gradle` subproject declarations
- Idioms: prefer `record`, sealed interfaces, and pattern-matching `switch` (Java 21).

## Commands
- Build: `./gradlew build`
- Run: `./gradlew run`
- Test (all): `./gradlew test`
- Test (class): `./gradlew test --tests "playground.AppTest"`
- Test (method): `./gradlew test --tests "playground.AppTest.appHasAGreeting"`
- Clean: `./gradlew clean`

## Workflow
1. **Explore first.** Read relevant code before editing. Don't guess APIs or file locations — grep.
2. **Plan non-trivial changes.** If it touches >1 method or introduces new types, present the plan (see plan structure below) and wait for go-ahead. Single-method fixes don't need a plan.
3. **Small, verifiable steps.** One change, run the narrowest relevant test, then continue.
4. **Verify before claiming done.** Build + full test suite must pass. If you couldn't run something, say so.
5. **Stop and ask** when requirements are ambiguous, a fix needs a design decision, or you've failed the same approach twice.

### Plan structure
Present a plan in this exact order, pausing for input at each gate:

**Gate 1 — Design:**
1. **Interfaces + their implementations** — list each interface and the concrete impl(s).
2. **Entities** — the data types / domain objects / records. Explain why each field exists.
3. **Exceptions** — the exceptions that will be thrown.

**Gate 2 — Tests:**
4. **Tests** — write test code and stubs BEFORE the implementation. Stubs throw `UnsupportedOperationException`. Run the tests and show the red output as proof they exercise something.

**Gate 3 — Implementation:**
5. **Implementation** — the actual logic.
6. **Metrics & logs** — `java.util.logging` for logs; a thin `Metrics` interface (pre-approved, in-memory impl) for counters/gauges. No external dependencies.

Get sign-off at each gate; don't generate implementation before tests.

## Code standards
- Match existing patterns in the file/module over personal preference.
- Smallest diff that solves the problem. No drive-by refactors, renames, or reformatting of untouched code.
- No new dependencies or abstractions without asking.
- Every field on a new entity/record/class gets a why-it-exists explanation in the plan. Inline comment only when the reason isn't obvious from the name or type.
- Comments explain *why*, not *what*. No commented-out code.
- No hardcoded secrets, credentials, or environment-specific values.
- Prefer immutable objects. Use `record` for value types; for objects with many optional fields, use the Builder pattern (static inner `Builder` class, `.build()` validates and returns an immutable instance).
- Validate at boundaries (API input, external data); trust internal types.
- Fail fast at construction/boundaries (`Objects.requireNonNull`, `IllegalArgumentException`).
- On request hot paths, return a result value instead of throwing. Count every degraded-mode entry; log on state transition (entering/exiting degraded), not per request.

### Concurrency
- One-line thread-safety note per class in Javadoc; no other verbose javadoc on prototype code.
- Prefer immutability (`record`) and `java.util.concurrent`; guard shared mutable state explicitly.
- Keep locks off hot paths where a volatile snapshot / immutable swap will do.

## Testing
- Name: `methodName_stateUnderTest_expectedBehavior` (e.g. `tryAcquire_bucketEmpty_returnsFalse`).
- Tests are deterministic. For rate/latency: inject a `LongSupplier` wrapping `System.nanoTime` (monotonic; compare via subtraction `a - b < 0`, never `a < b` — values overflow). For wall time: inject `java.time.Clock`. Never `Thread.sleep`.
- Test behavior, not implementation details. Prefer real implementations over mocks where cheap.
- Contention test recipe: start gate with `CountDownLatch`, N threads via `ExecutorService`. Call `get()` on every `Future` to surface worker exceptions; assert `awaitTermination` returns `true`; assert invariant. No sleeps.
- Bug fix → failing test first, then fix. New behavior → happy path + key edge/failure cases.
- Never delete, skip, or weaken a test to make it pass. If a test seems wrong, flag it.
- Existing tests (e.g. `appHasAGreeting`) are exempt from the naming convention — don't rename them.

## Git
- Not a git repo yet. These rules apply once `git init` is run.
- Never commit/push unless asked. Never force-push or rewrite shared history.
- Commits: imperative, scoped (e.g. `fix(ratelimit): handle expired token`). One logical change per commit.
- Don't stage unrelated files.

## Hard don'ts
- Don't modify: `gradle/wrapper/`, generated build files.
- Don't run destructive commands (`rm -rf`) without explicit approval.
- Don't invent behavior to fill gaps in requirements — ask.

## Communication
- Lead with the result. Be terse.
- When done: what changed, what was verified, what wasn't, any risks/follow-ups.
- Call out bad ideas directly rather than implementing them silently.

## Gotchas
- Config cache is on. If a task fails with a config-cache error, confirm with `--no-configuration-cache`; don't disable it globally.
- Gradle 9 needs `junit-platform-launcher` as `testRuntimeOnly` — don't remove it.
- Dependencies go in `gradle/libs.versions.toml`, referenced as `libs.*` — never hardcode versions in `build.gradle`.
