# Security Policy

## Supported versions

Kasa is before its 1.0 release. Security fixes go into `main` and the latest release only.

| Version | Supported |
|---|---|
| Latest 0.x release | Yes |
| Older releases | No |

## Reporting a vulnerability

Please do not report security problems through public issues, pull requests or discussions.

Report them privately through GitHub instead: open the **Security** tab of this repository and choose **Report a vulnerability**, or go directly to
<https://github.com/kaantpcc/kasa/security/advisories/new>.

Please include:

- the Kasa version or commit you tested,
- what the problem is and what an attacker could do with it,
- the steps or a small test that reproduces it.

## What counts as a security problem

Kasa is a ledger, so besides the usual issues we treat these as security problems:

- money being created or destroyed (a transaction that does not balance gets recorded),
- a balance policy, refund limit or hold that can be bypassed,
- the same idempotency key recording two different transactions,
- a change to a posted transaction that the hash chain does not detect.

## What happens next

This project is maintained by one person in their spare time. You can expect a first reply within 7 days. Once the problem is confirmed, a fix is prepared in a private advisory and released before the details are made public. You will be credited in the advisory unless you prefer not to be.
