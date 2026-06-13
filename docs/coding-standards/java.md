# Java Standards

- Java 21 only.
- Spring Boot configuration belongs in `config`.
- Business rules belong in module `domain` packages.
- Use `application` packages for use-case orchestration.
- Use `infrastructure` packages for adapters to databases, external systems, and framework details.
- Do not import another module's `domain` or `infrastructure` packages.

