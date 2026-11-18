# Kasa

**A double-entry ledger library for Java.** Kasa keeps an append-only record of every money movement in your
application and computes balances from it, with idempotent posting, holds, refunds, reversals, statements and a
tamper-evident hash chain.

[![CI](https://github.com/kaantpcc/kasa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kaantpcc/kasa/actions/workflows/ci.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=kaantpcc_kasa&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=kaantpcc_kasa)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kaantpcc_kasa&metric=coverage)](https://sonarcloud.io/summary/new_code?id=kaantpcc_kasa)
[![Maven Central](https://img.shields.io/maven-central/v/dev.kaantopcu.kasa/kasa-core)](https://central.sonatype.com/artifact/dev.kaantopcu.kasa/kasa-core)
[![Javadoc](https://javadoc.io/badge2/dev.kaantopcu.kasa/kasa-core/javadoc.svg)](https://javadoc.io/doc/dev.kaantopcu.kasa/kasa-core)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](#installation)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

> **Status:** `0.1.0` is the first release. The ledger runs in memory; a PostgreSQL-backed ledger is planned
> for `0.2`. The API may still change before `1.0`.

## Contents

- [Why Kasa?](#why-kasa)
- [Features](#features)
- [Installation](#installation)
- [Quickstart](#quickstart)
- [Core concepts](#core-concepts)
- [Guide](#guide)
- [Errors](#errors)
- [Thread safety and time](#thread-safety-and-time)
- [Performance](#performance)
- [Quality](#quality)
- [Limitations](#limitations)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Why Kasa?

Most applications that hold a balance (a marketplace, a wallet, loyalty points, prepaid credit) start with a
`balance` column and an `UPDATE`. Over time the same problems appear:

| Problem | What happens |
|---|---|
| **No history** | Nobody can explain why a balance is 350. |
| **Double processing** | A client times out, retries, and the money moves twice. |
| **Money from nowhere** | One side of a transfer is written, the other is not, and the books no longer add up. |
| **Silent edits** | A wrong row is fixed in place and the audit trail is gone. |

Banks solved these decades ago with double-entry bookkeeping, idempotent requests, holds and reversals. Kasa
packages those rules as a plain Java library you can embed in any application, with no framework and no
runtime dependencies.

## Features

- **Double-entry transactions.** Every transaction balances in each currency; money is never created or lost.
- **Append-only ledger.** Nothing is updated or deleted. Mistakes are corrected with reversals and refunds.
- **Computed balances.** Balances come from the posted entries, never from a separately stored number.
- **Idempotency.** Every transaction has a caller-chosen key. Retrying the same request records it only once.
- **Safe money arithmetic.** `Money` is `BigDecimal` plus `Currency`, fixed to the currency's decimal places,
  with no silent rounding and no mixing of currencies.
- **Balance policies.** Per account: unlimited, non-negative, or an overdraft limit.
- **Account status.** Open, frozen (can receive but not send) and closed (only with a zero balance).
- **Holds.** Put money aside, then capture all or part of it, or release it. Each hold is settled once.
- **Refunds and reversals.** Partial refunds are limited to what the original moved; a reversal cancels a
  transaction with its original value date.
- **Account hierarchy.** Ids such as `seller:42:wallet` let you sum a whole branch.
- **Queries.** Point-in-time balances, statements for half-open periods, and a trial balance.
- **Audit hash chain.** Each transaction is chained with SHA-256; `verifyChain()` finds the first altered record.
- **Thread-safe and fast.** Posting and balance reads do not slow down as the ledger grows.

## Installation

Kasa needs **Java 21 or newer** and has no runtime dependencies.

Maven:

```xml
<dependency>
    <groupId>dev.kaantopcu.kasa</groupId>
    <artifactId>kasa-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("dev.kaantopcu.kasa:kasa-core:0.1.0")
```

## Quickstart

A customer pays a seller 100 TRY, and the payment is then reversed:

```java
var ledger = new InMemoryLedger(Clock.systemUTC());
var lira = Currency.getInstance("TRY");
var payments = Account.debitNormal("customer-payments", lira, BalancePolicy.unlimited());
var wallet = Account.creditNormal("seller:42:wallet", lira, BalancePolicy.nonNegative());
ledger.open(payments);
ledger.open(wallet);

ledger.post(Transaction.builder("order-1")
        .debit(payments, Money.of("100.00", "TRY"))
        .credit(wallet, Money.of("100.00", "TRY"))
        .build());
ledger.balance(wallet).amount();   // 100.00

ledger.reverse("order-1");
ledger.balance(wallet).amount();   // 0.00
```

All types come from three packages: `dev.kaantopcu.kasa.core.money`, `dev.kaantopcu.kasa.core.account` and
`dev.kaantopcu.kasa.core.ledger`.

## Core concepts

### Money

`Money` is an amount in one currency. It always has exactly as many decimal places as the currency defines, so
`10` TRY and `10.00` TRY are equal, and `10.001` TRY is rejected instead of rounded.

```java
var price = Money.of("1000.00", "TRY");
var fee = price.multiply(new BigDecimal("0.10"), RoundingMode.HALF_EVEN);   // 100.00, rounding is explicit
price.subtract(fee);                                                       // 900.00
Money.of("100.00", "TRY").allocate(3);                                     // [33.34, 33.33, 33.33]
Money.of("1.00", "TRY").add(Money.of("1.00", "EUR"));                      // CurrencyMismatchException
```

### Accounts and normal balance

An `Account` is a definition: an id, a currency, a normal balance and a balance policy. It holds no money
itself; the ledger computes its balance from the entries posted to it.

The **normal balance** is the side that increases the account:

| Normal balance | Typical accounts | Increased by |
|---|---|---|
| Debit (`Account.debitNormal`) | assets and expenses: bank account, customer payments | debits |
| Credit (`Account.creditNormal`) | liabilities, equity and income: seller wallet, commission income | credits |

Balances are always reported in the account's normal direction, so a funded wallet shows a positive balance.
You can also build an account with its record constructor:
`new Account(new AccountId("bank:main"), lira, Side.DEBIT, BalancePolicy.unlimited())`.

### Balance policies

Every account chooses how far its balance may go below zero. There is no default.

```java
BalancePolicy.unlimited();                              // system accounts
BalancePolicy.nonNegative();                            // wallets: never below zero
BalancePolicy.overdraft(Money.of("500.00", "TRY"));     // down to -500.00
```

A transaction that would break a policy is rejected with `InsufficientFundsException` and nothing is recorded.

### Transactions and idempotency keys

A `Transaction` is a balanced set of entries with a key you choose, usually the id of the business event:

```java
var sale = Transaction.builder("order-123")
        .debit(payments, price)
        .credit(wallet, price.subtract(fee))
        .credit(commission, fee)
        .description("Order 123")
        .build();                  // UnbalancedTransactionException if debits and credits differ

ledger.post(sale);
ledger.post(sale);                 // a retry with the same key and content has no effect
```

Posting a *different* transaction with a key that was already used throws `IdempotencyKeyConflictException`.
A transaction can also carry a value date (`.valueDate(LocalDate)`); without one, the ledger uses the recording
date.

## Guide

### Account lifecycle

```java
ledger.open(wallet);       // opening again with the same definition has no effect
ledger.freeze(wallet);     // can still receive money, cannot send
ledger.unfreeze(wallet);
ledger.close(wallet);      // only with a zero balance; cannot be undone
ledger.status(wallet);     // OPEN, FROZEN or CLOSED
```

### Refunds

A refund is a transaction that points to the original with `refundOf`. The ledger makes sure that, together
with earlier refunds, it never gives back more to any account than the original moved:

```java
ledger.post(Transaction.builder("refund-123-1")
        .debit(wallet, Money.of("90.00", "TRY"))
        .debit(commission, Money.of("10.00", "TRY"))
        .credit(payments, Money.of("100.00", "TRY"))
        .refundOf("order-123")
        .build());
```

### Reversals

`reverse` cancels a transaction by posting its exact opposite with the key `<key>:reversal` and the original's
value date. The original stays in the ledger. A reversal counts as a full refund, so a transaction that was
already partly refunded cannot be reversed (`RefundExceedsOriginalException`).

```java
ledger.reverse("order-1");
ledger.posted("order-1:reversal");   // Optional[PostedTransaction[...]]
```

### Holds

A hold sets money aside, for example while a payout is in progress. A `HoldableAccount` pairs an available
account with a held account; open it with `openHoldable`:

```java
var seller = new HoldableAccount(
        Account.creditNormal("seller:42:wallet", lira, BalancePolicy.nonNegative()),
        Account.creditNormal("seller:42:held", lira, BalancePolicy.nonNegative()));
ledger.openHoldable(seller);

ledger.placeHold(new Hold(new IdempotencyKey("payout-7"), seller, Money.of("500.00", "TRY")));
ledger.balance(seller.available());   // 500.00 less: it can no longer be spent
ledger.totalBalance(seller);          // unchanged: the money still belongs to the seller
ledger.activeHolds(seller);           // [Hold[key=payout-7, ...]]

ledger.captureHold("payout-7", bank, Money.of("500.00", "TRY"));   // send it, or capture less and return the rest
// or: ledger.releaseHold("payout-7");                             // give it all back
```

A hold is settled exactly once. Repeating the same capture or release has no effect; settling it a different
way throws `HoldAlreadySettledException`. The held account changes only through these operations: posting to
it directly throws `HeldAccountPostingException`.

### Account hierarchy

Account ids can be split into segments with `:`. `balanceUnder` sums every account in a branch:

```java
ledger.balanceUnder(new AccountId("seller:42"));   // seller:42:wallet + seller:42:held
```

The accounts in a branch must share a currency and a normal balance.

### Statements and point-in-time balances

All queries about the past use the time the ledger **recorded** each transaction. Recording times never go
backwards, so an answer about the past never changes later. Periods are half-open: `from` is included, `to` is
not, so consecutive periods never share or miss a transaction.

```java
Statement statement = ledger.statement(wallet, startOfMonth, startOfNextMonth);
statement.openingBalance();
for (StatementLine line : statement.lines()) {
    line.recordedAt(); line.valueDate(); line.idempotencyKey(); line.description();
    line.entry();      line.balanceAfter();
}
statement.closingBalance();

ledger.balanceAsOf(wallet, endOfDay);   // counts transactions recorded before endOfDay
ledger.entries(wallet);                 // every entry, in recording order
```

### Trial balance

The trial balance lists the debit and credit totals of every account. In a correct ledger they are equal in
every currency:

```java
TrialBalance trial = ledger.trialBalance(endOfDay);
trial.isBalanced();                // true
trial.totalDebits(lira);
trial.lines();                     // one TrialBalanceLine per account
```

### Audit hash chain

Every posted transaction stores a SHA-256 hash of its content and of the previous transaction's hash. Changing
any recorded transaction breaks every link after it:

```java
ledger.verifyChain();   // Optional.empty() when intact, otherwise the key of the first broken transaction
ledger.posted("order-123").map(PostedTransaction::hash);
```

### Depending on the smallest interface

`Ledger` combines three interfaces. Give each part of your application only what it needs:

| Interface | Methods |
|---|---|
| `AccountAdministration` | `open`, `openHoldable`, `freeze`, `unfreeze`, `close` |
| `LedgerWriter` | `post`, `placeHold`, `captureHold`, `releaseHold`, `reverse` |
| `LedgerReader` | `balance`, `balanceAsOf`, `entries`, `statement`, `totalBalance`, `activeHolds`, `balanceUnder`, `trialBalance`, `status`, `posted`, `verifyChain` |

Methods that take an `IdempotencyKey` also accept a `String`, for example `ledger.reverse("order-1")`.

## Errors

Kasa checks every rule before it writes anything: **when an exception is thrown, nothing has been recorded.**
No method accepts or returns `null`; a value that may be missing is an `Optional`.

Rejections that depend on the state of the ledger extend the sealed `LedgerException`:

| Exception | Thrown when |
|---|---|
| `InsufficientFundsException` | A transaction would break an account's balance policy. |
| `IdempotencyKeyConflictException` | A key is reused for a different transaction. |
| `AccountNotOpenException` | An account, or every account under a branch, has not been opened. |
| `AccountDefinitionConflictException` | An account id is used with a different definition from the one it was opened with. |
| `AccountFrozenException` | Money would leave a frozen account. |
| `AccountClosedException` | Money would move on a closed account, or a closed account would be changed. |
| `NonZeroBalanceException` | An account with a non-zero balance is closed. |
| `TransactionNotFoundException` | No transaction or hold exists with the given key. |
| `RefundExceedsOriginalException` | A refund or reversal would give back more than the original moved. |
| `HoldAlreadySettledException` | A settled hold is settled a different way. |
| `HeldAccountPostingException` | A held account would change outside the hold operations. |

Invalid input is reported as an `IllegalArgumentException` subclass, such as `UnbalancedTransactionException`,
`InvalidEntryAmountException`, `CurrencyMismatchException`, `InvalidMoneyScaleException`,
`UnsupportedCurrencyException` and `MixedNormalBalanceException`.

```java
try {
    ledger.post(payout);
} catch (InsufficientFundsException e) {
    // tell the user the balance is too low
} catch (LedgerException e) {
    // any other business rule
}
```

## Thread safety and time

`InMemoryLedger` is safe to share between threads. Writes run one at a time; reads run in parallel with each
other but never during a write, so a reader never sees half of a transaction. Every object it returns is
immutable.

The ledger stamps each transaction with the `Clock` you give it. Use `Clock.systemUTC()` in production and a
fixed clock in tests:

```java
var ledger = new InMemoryLedger(Clock.fixed(Instant.parse("2026-01-01T09:00:00Z"), ZoneOffset.UTC));
```

If the clock moves backwards, the ledger reuses the last recorded time. A transaction without a value date gets
the recording date in the clock's time zone.

## Performance

Measured with [JMH](https://github.com/openjdk/jmh) on a ledger of **100,000 recorded transactions**:

| Operation | Time per call | Grows with |
|---|---|---|
| `balance` | ~0.03 µs | nothing (constant) |
| `balanceAsOf` | ~0.04 µs | log of the account's entries |
| `post`, money in (sale) | ~2 µs | nothing (constant) |
| `post`, money out (payout, policy check) | ~9 µs | nothing (constant) |
| `entries` of an account with 1,000 entries | ~5 µs | the account's entries |
| `verifyChain` | ~100 ms | all transactions (one SHA-256 each) |

Most of the cost of a post is the SHA-256 hash. The numbers come from a desktop machine (Windows 11, JDK 25,
one fork) and should be read as orders of magnitude. To run the benchmarks yourself:

```bash
mvn package -DskipTests
java -jar kasa-benchmarks/target/benchmarks.jar
```

The `kasa-benchmarks` module is not published.

## Quality

- **Tests:** unit tests, contract tests that every `Ledger` implementation must pass, and property-based
  tests that run 200 seeded random scenarios of 50 operations each and check seven invariants after every
  step (the books balance, the hash chain is intact, policies hold, queries agree with each other, a rejection
  changes nothing, refunds stay within the original, held balances match active holds).
- **Coverage:** JaCoCo; the build fails below 95% line or branch coverage.
- **Mutation testing:** PIT; the build fails below a 95% mutation score (currently 99%).
- **Static analysis:** SonarQube Cloud quality gate on `main` and on pull requests from this repository.
- **CI:** the full build runs on Java 21 and Java 25.
- **Javadoc:** every public type and method is documented; a missing comment fails the build.

## Limitations

Kasa `0.1` is honest about what it does not do yet:

- **In memory only.** Everything is lost when the process stops. A PostgreSQL ledger is planned for `0.2`.
- **One capture per hold.** A hold is captured once (fully or partly, with the rest returned); multiple partial
  captures are not supported.
- **Holdable accounts are closed account by account.** If the available account is closed while a hold is
  active, that hold can no longer be released.
- **Open held accounts with `openHoldable`.** If a held account is opened with plain `open` and receives money
  through `post` before any hold is placed on it, its balance no longer equals its active holds.
- **Status changes are not in the audit chain.** The hash chain covers money movements, not freezing or
  closing accounts.
- **Queries follow recording time.** There is no balance by value date.
- **No lookup by `AccountId`.** Reads take the `Account` definition.
- **`verifyChain` and `trialBalance` read every transaction** and take time proportional to the ledger size.

## Roadmap

- **0.2:** `JdbcLedger` backed by PostgreSQL, passing the same contract tests as `InMemoryLedger`.
- **Later:** payouts to banks with compensation (sagas), end-of-day reconciliation, and a REST service.

Kasa is not a bank and does not hold real money, accept card payments or produce invoices and tax records. It
keeps the books for the money your application moves.

## Contributing

Bug reports, ideas and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) to build the project
and learn the design rules, and follow the [Code of Conduct](CODE_OF_CONDUCT.md). To report a security problem,
see [SECURITY.md](SECURITY.md); please do not open a public issue.

## License

Kasa is released under the [Apache License 2.0](LICENSE).
