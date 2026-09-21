# AIMFORGE architecture

Single Gradle module (`:app`), package `com.aimforge.app`, layered so each phase adds a package without touching the others.

```
data/          Room entities, DAOs, AppDatabase, AimForgeRepository        (Phase 1, done)
domain/        ScopeType, SensType, AimErrorState, TestMode catalog        (Phase 1, done)
ui/            theme, components, screens, AppViewModel, nav shell          (Phase 1, done)
domain/SessionManager  state machine, timer, lock, cancel/delete; SessionStore seam  (Phase 2, done)
domain/Engines         CaptureEngine / AnalysisEngine / RecommendationEngine + NOT_IMPLEMENTED placeholders (Phase 2, done)
capture/       MediaProjection + foreground service, frame source           (Phase 3)
vision/        crosshair/target detection, per-frame observations           (Phase 4)
analysis/      windowed metrics, confidence, error classifier, aim score    (Phase 5)
recommend/     one-variable-at-a-time recommendation engine, anti-bad rules (Phase 6)
experiment/    before/after comparison, keep/revert decision                (Phase 7)
overlay/       optional floating panel (SYSTEM_ALERT_WINDOW)               (Phase 8)
```

Data flow (final): capture -> vision -> analysis -> recommend -> experiment -> Room -> UI.
UI only reads Room via Flows. Nothing leaves the device (no INTERNET permission).

## Phase 1 scope
- Onboarding (device, control layout, gyro), state-driven from Room profile row.
- Bottom-nav shell: Home, Tests, Lab, History, Profile (+ test instruction and report screens).
- Sensitivity Lab: 8 scopes x 4 sensitivity types, manual entry, stored in Room.
- Tests: all 13 modes with short instructions. START is disabled until Phase 3 (no fake buttons).
- History filters, Profile with real "Delete all data" (clears every table -> back to onboarding).
- Dashboard shows "--" until real scored sessions exist. Nothing is mocked.

## Known limitations (Phase 1)
- No test can actually run yet (needs Phase 3 capture).
- Strongest/weakest scope and trend need >=3 scored tests per scope / >=6 total.
- Launcher icon is a simple vector crosshair.

## Phase 2 notes
- DB v2. `test_sessions` is the real session table; `captures` and `diagnostic_*` tables exist for Phase 3+ and hold no rows until real data exists.
- Cancel rule: persisted sessions are marked CANCELLED and kept; only explicit Delete removes a record. Drafts are never persisted.
- Timer is wall-clock based (start, pause, total paused time stored in Room) so it survives app restart. Changing the phone clock during a session would distort it.
- Target SDK is 35. Android 16 (API 36) devices run it fine; compileSdk 36 needs a newer AGP and is left for the release phase.
