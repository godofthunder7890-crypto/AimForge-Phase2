# AimForge
Personal BGMI aim coach. Observes only what you share via screen capture. No game modification, no input simulation, no server, no ads, no accounts.

## PHASE 2 COMPLETED (real test-session engine)
Phase 2 builds the workflow that Phase 3 (screen capture) plugs into. **It does not capture or analyze gameplay.**

### Actually implemented
- Test Setup screen (all test types, scope, weapon, optional attachments, distance, target, FPS, refresh rate, notes).
- Sensitivity snapshot: user-entered Camera / ADS / Gyro / ADS Gyro, prefilled from the Lab if you entered them there. Blank = Unknown (stored as NULL). AimForge never reads BGMI settings.
- Session engine (`SessionManager`) with a real state machine, single open session rule, real timer (pause / resume / end / cancel), delete.
- Conditions lock: after "Lock conditions", the session record's conditions and sensitivity snapshot are never rewritten.
- Room database v2 with migration from v1 (profile and Lab values are kept). Tables: sessions, captures, diagnostic batches and steps.
- History and Session Detail built only from stored sessions. Score shows "Not analyzed" unless a real analysis exists.
- Interfaces `CaptureEngine`, `AnalysisEngine`, `RecommendationEngine` with NOT_IMPLEMENTED placeholders that never return values.
- Unit tests: state machine, timer math, session manager, engines, Room persistence + restart, migration 1 to 2.

### NOT implemented
- Screen capture (MediaProjection), computer vision, aim metrics, Aim Score, recommendations, graphs, before/after, floating panel.
- Full Diagnostic runner. Only its data model and planned sequence exist; the screen says "Not available yet".
- Android does not give a reliable in-game FPS, so FPS is manual. Refresh rate: the screen's current Hz is shown as a hint, you decide what to store.

### How to create a session
1. Tests tab, pick a test.
2. Fill what you know. Leave the rest blank (Unknown).
3. LOCK CONDITIONS. The session opens in READY.
4. START SESSION. With no capture engine you get "Screen capture engine is not available yet." and nothing is recorded. The session moves to Waiting and the timer counts real elapsed time.
5. PAUSE / RESUME as needed, then END SESSION. You get SESSION SAVED with Capture "Not available yet" and Analysis "Pending capture". No score.
6. CANCEL keeps the record as Cancelled in History. Delete removes it from the phone.

### Session states
DRAFT (in-memory form only, never stored) -> READY -> CAPTURE_PENDING -> CAPTURING -> CAPTURE_COMPLETE -> ANALYSIS_PENDING -> ANALYZING -> ANALYZED. Any open state can go to CANCELLED, some to FAILED. In Phase 2 sessions end in CAPTURE_PENDING (ended, no capture) or CANCELLED. Illegal jumps are rejected.

### Phase 3 will add
MediaProjection permission, foreground service, real frame capture into app-private storage, real `captures` rows (frames, resolution, fps), CAPTURING to CAPTURE_COMPLETE.

## Build (no PC needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Actions tab: "Build APK" builds the APK, then runs the unit tests.
3. Download artifact `AimForge-debug-apk`, unzip, install `app-debug.apk`. Test reports are in artifact `unit-test-reports`.
