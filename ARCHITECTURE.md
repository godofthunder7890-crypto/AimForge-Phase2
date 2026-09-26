# AIMFORGE architecture

Single Gradle module (`:app`), package `com.aimforge.app`, layered so each phase adds a package without touching the others.

```
data/          Room entities, DAOs, AppDatabase, AimForgeRepository        (Phase 1, done)
domain/        ScopeType, SensType, AimErrorState, TestMode catalog        (Phase 1, done)
ui/            theme, components, screens, AppViewModel, nav shell          (Phase 1, done)
domain/SessionManager  state machine, timer, lock, cancel/delete; SessionStore seam  (Phase 2, done)
domain/Engines         CaptureEngine / AnalysisEngine / RecommendationEngine + NOT_IMPLEMENTED placeholders (Phase 2, done)
capture/       MediaProjection + foreground service, frame source           (Phase 3)
domain/cv/     crosshair detection, temporal movement, CV session metrics   (Phase 4)
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

## Phase 3 notes
- `capture/CaptureService` (foreground, mediaProjection) owns MediaProjection, VirtualDisplay, ImageReader on one HandlerThread. `capture/MediaProjectionCaptureEngine` exposes a `StateFlow<CaptureSnapshot>` and the start/stop handshake. `domain/CaptureMonitor` saves progress and finalizes sessions from engine state, even with no screen open.
- Only `SessionManager` moves a session between states; `finalizeCapture` is idempotent (mutex) so user End, system stop and recovery cannot double-finalize.
- DB v3: `test_sessions.failureReason`, `captures.sampledFrameCount/blankFrameCount/stopReason`. Migration 2 to 3 only adds nullable columns.
- Capture resolution is fixed at long side 1280 px, resized on rotation. This is what Phase 4 will analyze.

## Phase 4 notes
- `domain/cv` is pure Kotlin and JVM-testable: it contains frame models, preprocessing, heuristic crosshair detection, temporal tracking, and honest result states.
- `capture/GraySampler` converts real RGBA ImageReader planes into a small grayscale frame without persisting image data.
- A fresh `CvAnalysisEngine` is created per capture and finalized once on the capture HandlerThread. Results are saved asynchronously as one Room row per session.
- DB v4 adds only `cv_analyses`; `MIGRATION_3_4` is additive and leaves all Phase 3 tables unchanged.
- The CV result is deliberately separate from the Phase 2/3 `AnalysisEngine` and does not create an Aim Score or recommendation.

### Phase 3 startup ordering and race fix

The capture service can publish `RUNNING`, `STOPPED`, or `FAILED` before the
suspending `CaptureEngine.start()` call returns. Previously, `SessionManager`
inserted the `captures` row only after a `Started` result. A monitor callback
could therefore finalize the session while no capture row existed; the later
insert either created an orphan or failed to persist the real terminal metadata.

`SessionManager` now allocates the capture ID, moves the session to
`CAPTURE_PENDING`, and inserts the metadata row before invoking the engine.
Startup, stop, callback finalization, progress, recovery, and deletion share one
`Mutex`. The monitor may still observe an early callback, but it waits until the
row and session transition are committed. Updates also verify the callback's
`captureId`, so a late service callback cannot update another capture. No sleep,
retry loop, or fake metric is used.
