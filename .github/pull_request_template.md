## What and why

<!-- What does this change do, and why is it needed? Link the issue it closes, for example "Fixes #12". -->

## Checklist

- [ ] `mvn verify` passes locally
- [ ] New behaviour has tests, and I saw them fail before the change
- [ ] Money stays in `Money` (no `double` or `float`)
- [ ] The ledger stays append-only: corrections are new transactions
- [ ] Changes to the public API are described above
