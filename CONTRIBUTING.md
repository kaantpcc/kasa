# Contributing to Kasa

Thanks for your interest in Kasa, a double-entry ledger library for Java. Bug reports, ideas and pull requests are all welcome.

## Before you start

- **Found a bug or have an idea?** Open an issue using one of the templates.
- **Planning a larger change?** Please open an issue first so we can agree on the approach before you spend time on code.
- **Found a security problem?** Do not open a public issue. Follow [SECURITY.md](SECURITY.md) instead.

By taking part in this project you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Building and testing

You need JDK 21 or newer and Maven 3.9 or newer. Kasa itself targets Java 21; CI also runs the build on Java 25.

```bash
mvn verify
```

This compiles the code, runs all tests and checks test coverage with JaCoCo. The build fails if line or branch coverage drops below 95%. The coverage report is written to `kasa-core/target/site/jacoco/index.html`.

Mutation testing with PIT runs only when you ask for it:

```bash
mvn verify -Pmutation
```

It fails below a 95% mutation score. The slow property-based tests are left out of this run. The report is written to `kasa-core/target/pit-reports/index.html`.

## Design rules

Kasa moves money, so a few rules are not negotiable:

- **No `double` or `float` for money.** Amounts are `Money` (`BigDecimal` plus `Currency`), and rounding is always explicit.
- **The ledger is append-only.** Posted transactions are never updated or deleted. Mistakes are corrected with new transactions, such as reversals and refunds.
- **Balances are computed from entries**, never stored separately.
- **Domain objects are immutable** (`record` or final fields) and validate themselves in their constructor, so an invalid object cannot exist.
- **The public API does not accept or return `null`.** A value that may be absent is returned as `Optional`.
- **Errors are exceptions.** Rejections that depend on the ledger state (for example, insufficient funds) are subtypes of `LedgerException`. Invalid input is an `IllegalArgumentException`.
- **`kasa-core` depends only on the JDK.** No Spring, JPA or Lombok in the core module.
- **Every ledger implementation passes the same contract tests.** The abstract `*ContractTest` classes define the behaviour; a new implementation adds a subclass instead of its own copy of the tests.

Keep methods small and names clear. Comments explain *why* something is done, not *what* the code does.

## Tests

- Tests use JUnit and AssertJ.
- Test names describe behaviour, for example `rejectsTransactionWhenDebitsAndCreditsDiffer`.
- Given, when and then are separated by blank lines, and one test checks one behaviour.
- No real clock, no `Thread.sleep` and no unseeded randomness. Pass a fixed `Clock`, and use a fixed seed for random data.
- See a test fail before you make it pass. A bug fix starts with a test that reproduces the bug.
- Cover the edge cases: zero and negative amounts, different currencies, empty lists and `null`.

## Branches, commits and pull requests

1. Fork the repository and create a branch from `main`, such as `feature/partial-capture` or `fix/refund-limit`.
2. Keep each commit to one logical change. Write the message as a short English sentence in the imperative, without prefixes or emoji, for example `Add refund limit per account`.
3. Run `mvn verify` before you push.
4. Open a pull request and fill in the template. Pull requests are squash-merged into `main`.

### Continuous integration

Every pull request runs the build, the tests and the coverage check, and all of them must pass before merging. The first workflow run for a new contributor waits for a maintainer's approval.

SonarQube Cloud analyzes pushes to `main` and pull requests from branches in this repository. GitHub does not share secrets with pull requests from forks, so the SonarQube step is skipped there and the code is analyzed after it is merged. If the analysis finds something, a maintainer may ask for a small follow-up change.

## License

Kasa is licensed under the [Apache License 2.0](LICENSE). By contributing, you agree that your contributions are licensed under the same terms.
