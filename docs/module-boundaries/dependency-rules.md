# Module Dependency Rules

## Backend Modules

Application module:

```text
app -> modules/*
app -> shared/*
```

Domain modules:

```text
modules/* -> shared/common
modules/* -> shared/persistence where persistence support is needed
modules/* -> shared/security where security support is needed
modules/* -> shared/observability
```

Shared modules:

```text
shared/* -> shared/common only when needed
shared/common -> no internal project dependencies
```

Forbidden:

```text
module -> another module domain package
module -> another module infrastructure package
frontend -> backend source code
database migration -> hidden inside feature source
business logic -> shared libraries
```

Package naming:

```text
com.helium.core.<module>.domain
com.helium.core.<module>.application
com.helium.core.<module>.infrastructure
com.helium.core.<module>.events
com.helium.core.<module>.config
```

