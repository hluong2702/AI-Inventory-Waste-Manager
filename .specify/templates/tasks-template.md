---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are REQUIRED for behavior, security, tenant scope, data integrity, API contracts, and
critical user journeys. A documentation-only or non-behavioral change may use `N/A` only when the
feature specification gives a concrete rationale.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Frontend**: `src/` with colocated `*.test.ts` / `*.test.tsx`; browser journeys in `e2e/`
- **Backend**: `backend/src/main/java/` and `backend/src/test/java/`
- **Mobile**: `api/src/`, `ios/src/` or `android/src/`
- Paths shown below are illustrative - generated tasks MUST use the concrete paths from plan.md

<!--
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.

  The /speckit-tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/

  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment

  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create project structure per implementation plan
- [ ] T002 Initialize [language] project with [framework] dependencies
- [ ] T003 [P] Configure lint, unit, integration, browser, coverage, and build quality gates

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T004 Setup schema, Flyway migration, data repair, backup, and rollback verification
- [ ] T005 [P] Implement authenticated membership, tenant predicates, RBAC, and denial tests
- [ ] T006 [P] Setup bounded API contracts, validation, idempotency, and middleware structure
- [ ] T007 Create base entities with service and database invariants
- [ ] T008 Configure structured correlated logs, metrics, alerts, health, and error handling
- [ ] T009 Setup fail-closed environment configuration and secret management
- [ ] T010 Configure required MySQL/Redis Testcontainers gates so CI fails instead of skipping

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 1 *(required for behavior changes)* ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T011 [P] [US1] Backend contract/authorization test in backend/src/test/java/[package]/[Feature]ControllerTest.java
- [ ] T012 [P] [US1] Frontend behavior test in src/[path]/[Feature].test.tsx
- [ ] T013 [P] [US1] Critical browser journey in e2e/[feature].spec.ts

### Implementation for User Story 1

- [ ] T014 [P] [US1] Create [Entity1] in the concrete backend domain package
- [ ] T015 [P] [US1] Create frontend contract/types in src/types/ or the feature boundary
- [ ] T016 [US1] Implement backend service transaction in backend/src/main/java/[package]/[Service].java
- [ ] T017 [US1] Implement endpoint and frontend integration in the concrete plan paths
- [ ] T018 [US1] Add bounds, authorization, tenant isolation, failure, and recovery behavior
- [ ] T019 [US1] Add correlated logs, metrics, and alerts for critical operations

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 2 *(required for behavior changes)* ⚠️

- [ ] T020 [P] [US2] Backend contract/authorization test in backend/src/test/java/[package]/[Feature]ControllerTest.java
- [ ] T021 [P] [US2] Frontend behavior test in src/[path]/[Feature].test.tsx
- [ ] T022 [P] [US2] Critical browser journey in e2e/[feature].spec.ts

### Implementation for User Story 2

- [ ] T023 [P] [US2] Create backend entity or frontend contract in the concrete plan path
- [ ] T024 [US2] Implement backend service transaction in backend/src/main/java/[package]/[Service].java
- [ ] T025 [US2] Implement endpoint and frontend integration in the concrete plan paths
- [ ] T026 [US2] Integrate safely with User Story 1 and add logs/metrics where needed

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 3 *(required for behavior changes)* ⚠️

- [ ] T027 [P] [US3] Backend contract/authorization test in backend/src/test/java/[package]/[Feature]ControllerTest.java
- [ ] T028 [P] [US3] Frontend behavior test in src/[path]/[Feature].test.tsx
- [ ] T029 [P] [US3] Critical browser journey in e2e/[feature].spec.ts

### Implementation for User Story 3

- [ ] T030 [P] [US3] Create backend entity or frontend contract in the concrete plan path
- [ ] T031 [US3] Implement backend service transaction in backend/src/main/java/[package]/[Service].java
- [ ] T032 [US3] Implement endpoint and frontend integration in the concrete plan paths
- [ ] T033 [US3] Add bounds, recovery behavior, logs, metrics, and accessibility states

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Documentation updates in docs/
- [ ] TXXX Code cleanup and refactoring
- [ ] TXXX Performance optimization across all stories
- [ ] TXXX [P] Coverage measurement and risk-based gap closure in frontend and backend tests
- [ ] TXXX Security, privacy, dependency, and tenant-isolation review
- [ ] TXXX Performance and accessibility verification against measured budgets
- [ ] TXXX Deployment, rollback, backup/restore, and incident runbook validation
- [ ] TXXX Run quickstart.md plus all mandatory CI quality gates

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Required tests MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Backend contract/authorization test in backend/src/test/java/[package]/[Feature]ControllerTest.java"
Task: "Frontend behavior test in src/[path]/[Feature].test.tsx"
Task: "Critical browser journey in e2e/[feature].spec.ts"

# Launch independent contracts/models for User Story 1 together:
Task: "Create [Entity1] in the concrete backend domain package"
Task: "Create frontend contract/types in src/types/ or the feature boundary"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
