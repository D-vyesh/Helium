# Database Standards

- PostgreSQL is the MVP source of truth.
- Flyway migrations are append-only after merge.
- Do not create database tables without a module owner.
- Do not let one module write another module's owned tables.
- Ledger-owned financial data must only be changed through ledger application flows.

