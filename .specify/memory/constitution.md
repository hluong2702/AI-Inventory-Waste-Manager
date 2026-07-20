<!--
Sync Impact Report
- Version change: unversioned template -> 1.0.0
- Added principles:
  - I. Tenant Isolation and Least Privilege (NON-NEGOTIABLE)
  - II. Inventory and Financial Correctness
  - III. Test-Evidenced Delivery (NON-NEGOTIABLE)
  - IV. Secure-by-Default Boundaries
  - V. Observable, Operable, and Simple
- Added sections: Operational and Technical Constraints; Development Workflow and Quality Gates
- Removed sections: none (template placeholders were replaced)
- Templates:
  - ✅ updated: .specify/templates/plan-template.md
  - ✅ updated: .specify/templates/spec-template.md
  - ✅ updated: .specify/templates/tasks-template.md
  - ✅ reviewed, no change required: .specify/templates/checklist-template.md
- Spec Kit command guidance:
  - ✅ updated: .agents/skills/speckit-tasks/SKILL.md
- Runtime guidance reviewed:
  - ⚠ pending: README.md (verification omits unit/E2E tests and its bundle warning is stale)
  - ⚠ pending: technical_design_document.md (Docker artifacts are described but absent; debt list is stale)
  - ⚠ pending: project_report.md (role/coverage claims are stale and bracketed metrics lack evidence)
  - ⚠ pending: SRS.md (performance, scale, availability, and compliance claims need evidence/owners)
- Follow-up TODOs:
  - Fix FlywayMigrationMySqlIntegrationTest to expect V15 and require Docker-backed gates in CI.
  - Establish measured performance, accessibility, coverage, recovery, and availability baselines.
  - Add CI, deployment artifacts, observability configuration, and backup/restore runbooks.
-->
# AI Inventory & Waste Manager Constitution

## Core Principles

### I. Tenant Isolation and Least Privilege (NON-NEGOTIABLE)
Every tenant-scoped read and write MUST derive its store from an authenticated, active membership.
Client-provided store identifiers MAY select among memberships but MUST NOT grant access. Backend
authorization MUST enforce role and store scope; frontend route guards are usability controls only.
Repository operations MUST include tenant predicates, and cross-tenant relationships MUST be
protected by database constraints where MySQL can express them. System administrators MUST NOT
impersonate a tenant without an explicit, time-bound, audited support mechanism. Thread-local tenant
state MUST be cleared in a `finally` block. Every tenant-sensitive change MUST include positive and
negative cross-tenant tests. These rules prevent the highest-impact SaaS failure: data disclosure or
mutation across customers.

### II. Inventory and Financial Correctness
Inventory, waste, subscription, payment, and invitation state transitions MUST be atomic and safe
under retries and concurrency. FEFO allocation MUST lock or otherwise serialize competing stock
updates. Monetary and quantity values MUST use exact decimal types and enforce invariants at request,
service, and database layers. Calls that can be retried, especially payment creation and webhook
handling, MUST use idempotency keys or equivalent uniqueness constraints. External side effects MUST
use a durable outbox or reconciliation process when they cannot share the database transaction.
Business time MUST use an injected `Clock` and configured business zone; persisted timestamps MUST
remain UTC. Flyway migrations MUST be forward-only, immutable after release, tested against MySQL,
and accompanied by a data repair, backup, and rollback strategy when they affect production data.

### III. Test-Evidenced Delivery (NON-NEGOTIABLE)
Every behavior change MUST ship with automated tests covering the happy path, boundary conditions,
authorization failures, and relevant recovery paths. Critical flows--authentication and token
rotation, tenant switching, inventory movement, migrations, Redis-backed coordination, billing,
webhooks, and invitation delivery--MUST have integration or contract tests in addition to unit tests.
User-visible critical journeys MUST have browser-level tests. Required CI tests MUST fail when their
dependencies are unavailable; a skipped Testcontainers test is not a passing quality gate. A change
is mergeable only when `npm run lint`, `npm test`, `npm run build`, the required Playwright journeys,
and `backend/gradlew test` pass in a production-like CI environment. Coverage MUST be measured, must
not regress for critical packages, and MUST be interpreted as supporting evidence rather than a
substitute for risk-based test design.

### IV. Secure-by-Default Boundaries
Production configuration MUST fail closed when secrets, allowed origins, trusted proxies, external
URLs, or security-critical dependencies are absent or invalid. Secrets and credentials MUST NOT be
committed, returned to the browser, or written to logs. Access tokens MUST remain in memory, refresh
credentials MUST use rotating `HttpOnly` cookies, and public endpoints MUST be explicitly allowlisted.
All input MUST be bounded and validated; exported CSV and rendered output MUST neutralize injection.
Payment webhooks MUST verify provider authenticity and tolerate duplicate delivery. Authentication,
invitation, import, export, and payment endpoints MUST have abuse controls appropriate to their cost.
Production traffic MUST use TLS. Releases MUST include dependency vulnerability review and MUST
document personal-data collection, retention, deletion, and access rules.

### V. Observable, Operable, and Simple
Critical workflows MUST emit structured, tenant-safe logs with a correlation identifier and MUST
expose actionable metrics for authentication throttling, payment reconciliation, outbox delivery,
scheduled jobs, inventory conflicts, and dependency health. Degraded operation MUST be explicit;
failures MUST NOT be swallowed without a log, metric, and defined user-visible outcome. Controllers
MUST remain transport adapters; business orchestration and mapping that grow beyond a small endpoint
MUST move to focused services. New abstraction or infrastructure MUST solve a documented need and
have an owner. Performance, scale, waste-reduction, and availability claims MUST be backed by
repeatable measurements. Every production release MUST have deployment, rollback, backup/restore,
and incident-response instructions.

## Operational and Technical Constraints

- The supported baseline is Java 21 with Spring Boot, MySQL 8 with Flyway, Redis, and React with
  TypeScript. A stack change MUST include compatibility, migration, operations, and rollback analysis.
- API contracts MUST be documented and checked at the backend/frontend boundary. Breaking changes
  MUST be versioned or migrated without an unannounced client break.
- Collection endpoints MUST be paginated or strictly bounded. Imports and exports MUST enforce size,
  row, time-range, and memory limits.
- Production MUST NOT use the Spring `dev` profile, local credentials, wildcard origins, or disabled
  transport security. Each deployment environment MUST use isolated cache namespaces and secrets.
- User-facing workflows MUST define measurable latency and accessibility acceptance criteria.
  Keyboard access, semantic labels, loading, empty, error, and retry states are mandatory where the
  interaction supports them.
- Compliance statements and business metrics MUST identify their evidence, measurement period, and
  accountable owner. Aspirational values MUST be labelled as targets, never reported as achieved.

## Development Workflow and Quality Gates

1. A feature specification MUST state tenant scope, roles, data invariants, failure/retry behavior,
   security and privacy impact, observability, performance, accessibility, and measurable outcomes.
   Any non-applicable item MUST include a concrete rationale.
2. The implementation plan MUST pass the Constitution Check before research/design and again after
   contracts and the data model are complete. Database changes MUST include forward migration,
   compatibility, backup, and rollback or repair plans.
3. Tasks MUST place tests before or alongside implementation and MUST include exact repository paths.
   Critical integration tests MUST declare their MySQL, Redis, mail, payment, or browser dependency.
4. Review MUST verify tenant predicates, authorization, transaction boundaries, idempotency, bounded
   queries, time-zone behavior, API compatibility, logs/metrics, and user-facing failure states.
5. CI MUST run all mandatory gates. A release MUST retain test and scan evidence, reconcile runtime
   documentation with deployed artifacts, and verify rollback plus backup/restore readiness.
6. A principle exception MUST be recorded in the plan's Complexity Tracking table with risk, owner,
   expiry date, and the rejected compliant alternative. Undocumented exceptions are prohibited.

## Governance

This constitution supersedes conflicting project conventions and templates. Amendments require a
documented proposal, a Sync Impact Report, updates to dependent templates and runtime guidance, and
approval through the normal project review process. Versioning follows semantic versioning: MAJOR for
principle removal or incompatible redefinition, MINOR for a new principle or materially expanded
obligation, and PATCH for clarification without changed obligations. Every feature plan and code
review MUST demonstrate compliance; every release MUST review unresolved exceptions and operational
readiness. At least once per quarter, maintainers MUST review evidence for security, tenant isolation,
data recovery, dependency health, performance, and documentation accuracy.

**Version**: 1.0.0 | **Ratified**: 2026-07-15 | **Last Amended**: 2026-07-15
