# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

**Security/Privacy**: [tenant scope, roles, sensitive data, abuse controls, secrets or N/A with rationale]

**Observability**: [logs, correlation, metrics, alerts, dependency health or N/A with rationale]

**Deployment/Recovery**: [migration, compatibility, rollout, rollback, backup/restore requirements]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Tenant isolation & authorization**: Every tenant-scoped operation derives store access from an
  active membership, applies repository predicates, and has cross-tenant denial tests.
- **Data integrity & concurrency**: Transaction boundaries, exact decimal handling, invariants,
  locking, idempotency, and external-side-effect reconciliation are specified where relevant.
- **Secure boundaries**: Public endpoints, input/size limits, secrets, cookies/tokens, CORS/proxies,
  webhook verification, privacy, and abuse controls are identified.
- **Test evidence**: Unit, integration/contract, and critical browser journeys are planned. Required
  Testcontainers tests run in CI and cannot pass by being skipped.
- **API & migration safety**: Contracts, compatibility, pagination/bounds, Flyway migration, data
  repair, backup, and rollback are documented.
- **Operability**: Correlated logs, metrics, alerts, health checks, degraded behavior, deployment,
  rollback, and incident ownership are defined.
- **Performance & accessibility**: Measurable budgets and representative verification are defined;
  keyboard, semantic, loading, empty, error, and retry states are covered.
- **Simplicity**: Controllers remain transport adapters; new abstractions or exceptions are justified
  in Complexity Tracking with an owner and expiry date.

All eight checks MUST be marked PASS or N/A with a concrete rationale. Any unresolved failure blocks
Phase 0 and implementation.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: This repository's React + Spring web application
backend/
├── src/
│   ├── main/java/vn/inventoryai/[domain]/
│   ├── main/resources/db/migration/
│   └── test/java/vn/inventoryai/[domain]/
src/
├── components/
├── pages/
├── services/
├── stores/
└── **/*.test.{ts,tsx}
e2e/
└── *.spec.ts

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed / Risk | Owner / Expiry | Compliant Alternative Rejected Because |
|-----------|-------------------|----------------|----------------------------------------|
| [e.g., temporary skipped integration gate] | [specific blocker and exposure] | [owner / YYYY-MM-DD] | [why the compliant path cannot be used yet] |
