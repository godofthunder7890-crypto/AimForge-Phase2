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

## Phase 3 notes
- `capture/CaptureService` (foreground, mediaProjection) owns MediaProjection, VirtualDisplay, ImageReader on one HandlerThread. `capture/MediaProjectionCaptureEngine` exposes a `StateFlow<CaptureSnapshot>` and the start/stop handshake. `domain/CaptureMonitor` saves progress and finalizes sessions from engine state, even with no screen open.
- Only `SessionManager` moves a session between states; `finalizeCapture` is idempotent (mutex) so user End, system stop and recovery cannot double-finalize.
- DB v3: `test_sessions.failureReason`, `captures.sampledFrameCount/blankFrameCount/stopReason`. Migration 2 to 3 only adds nullable columns.
- Capture resolution is fixed at long side 1280 px, resized on rotation. This is what Phase 4 will analyze.

## Phase 4 notes
- `domain/cv` is pure Kotlin, JVM-testable with no Android types: `Frame`, `Coordinates`, `CrosshairDetection`/`TargetDetection`, `FramePreprocessor`, `CrosshairVisionDetector`/`HeuristicCrosshairDetector`, `TargetVisionDetector`/`NoOpTargetDetector`, `TemporalTracker`/`CrosshairTemporalTracker`, `AimFeatureAnalyzer`, `CvAnalysisEngine`, `CvAnalysisStore`.
- `capture/GraySampler` is the only Android-adjacent Phase 4 file (still plain `ByteBuffer` math, no android.* imports) and is what `CaptureService` calls per analyzed frame.
- One `CvAnalysisEngine` instance per session, created in `CaptureService.beginCapture()`, fed on the capture HandlerThread (no extra thread/queue), finalized once in `finish()` and saved via `AimForgeApp.cvAnalysisStore` on `Dispatchers.IO`.
- This is deliberately a second, independent pipeline from the Phase 2/3 `AnalysisEngine`/`AnalysisOutcome` (aimScore-shaped, still `NotImplementedAnalysisEngine`) so Phase 4's raw CV metrics can't leak into or be mistaken for the Phase 5/6 Aim Score path.
- DB v4: additive `cv_analyses` table only; `MIGRATION_3_4` touches nothing else.

## Phase 5 notes
- `domain/metrics` is pure Kotlin, JVM-testable, and depends only on `domain/cv`'s existing detection models (`CrosshairDetection`, `TargetDetection`, `FrameObservation`) — no parallel/incompatible model.
- `CvAnalysisEngine` gained one small addition: a capped `ArrayDeque<FrameObservation>` and `snapshotObservations()`. Nothing else in its public API or behavior changed (Phase 4's own tests are untouched and still pass against this file).
- `AimMetricsEngine` is stateless/deterministic (pure function of its inputs) and is called once, from `CaptureService.finish()`, right after the Phase 4 CV result — same observation snapshot, no second capture or replay.
- DB v5: additive `aim_metrics` table only; `MIGRATION_4_5` touches nothing else. Deleting a session cascades to `aim_metrics` too now (`RoomSessionStore.delete`).

## Phase 5 final notes
- Detector v2 (`LocalContrastCrosshairDetector`) is the default in `CvAnalysisEngine`; it consumes the cropped max-channel `Frame` from `GraySampler.buildCentralCrop`.
- `Frame` gained optional source-mapping fields (sourceWidth/Height, cropOffset, cropWidth/Height), all defaulted so existing full-frame use is unchanged.
- `CrosshairDetection` gained nullable diagnostics (candidateCount, rejectionReason).
- Metrics stream through `StreamingAimMetricsAccumulator`; `CaptureService.finish()` calls `cv.finalizeAimMetrics(...)`. DB is v6 (`MIGRATION_5_6`).
- Phase 3 capture protections (mutex serialization, session/capture association, terminal-state protection, recovery, ImageReader lifecycle) were not touched.
