# MPay Notification Levels Implementation Plan

> **For agentic workers:** Use executing-plans to implement this approved plan inline.

**Goal:** Restore MPay notification capture without requiring transaction numbers and expose ordered manual, notification, and notification-plus-accessibility modes.

**Architecture:** SettingsStore owns the persistent mode. Notification ingestion creates MPay records; accessibility enriches an exactly matched receipt and never creates historical MPay records. Notification event identity distinguishes equal-value transactions without an amount-only suppression window.

**Tech Stack:** Kotlin, Compose, SharedPreferences, Android notification listener, accessibility service, JUnit.

**Spec:** User instructions in this conversation on 2026-10-05; transaction IDs only appear in MPay history, and are optional.

## Global Constraints
- Preserve architecture and existing user records and settings.
- No payment submission through automation; real transactions require the user's cooperation.
- Manual disables both readers; notification mode does not traverse payment screens; enhanced mode retains notification reading.
- MPay history cannot create records; receipt enrichment requires unambiguous exact metadata.

## Review Focus
- Missing transaction number on valid notices and success pages.
- Pending requests, marketing, failed transactions and refunds.
- Equal-value independent notifications versus updates to one notification.
- Legacy settings migration and enabled accessibility with notification-only mode.
- Notification/screen arrival in either order, with no second MPay entry.

### Task 1: Capture regressions
- [x] Add parser, gate, identity, source ownership and settings tests.
- [x] Run focused tests and verify failure against the old implementation.
- [x] Introduce AutoRecordMode and PaymentNotificationAnalyzer; accept fresh MPay results without IDs.
- [x] Run focused tests to green.

### Task 2: Connect services and settings
- [x] Use the mode in both services; use notification event identity in the actual writer.
- [x] Add three selectable levels and mode-aware permission/status copy.
- [x] Run full tests, assemble and lint; request a read-only review.

### Task 3: Device verification
- [x] Install without clearing data; verify mode changes and screen filtering on Samsung.
- [x] Monitor user-generated MPay transfer notification (one transfer); no other real transactions requested.
- [x] Document observed results separately from untested real transaction flows.

## Execution Ledger
- Regression: missing-ID success gate and MPay notification rejected valid inputs; watched tests fail then pass.
- Review: legacy notification identity was not permanently seeded on all service branches; removed short circuit and added permanent aliases/baseline tests.
- Device: one user-operated 0.10 transfer added one notification record and displayed a system ball. Three history reopens did not add entries.
- Ruling: MPay remains notification-owned at the highest level — neither fresh screen nor historical screen may independently create a second no-ID entry. Exact metadata only enriches when unambiguous.
- Final checks: 250 tests, assemble and lint pass; all prior user data preserved; highest mode restored after testing.
