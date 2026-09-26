# AimForge
Personal BGMI aim coach. Observes only what you share via Android screen capture. No game modification, no input simulation, no anti-cheat interaction, no server, no ads, no accounts, no INTERNET permission.

## Status
- Phase 1, Phase 2: complete and verified on a real realme GT 6T (GitHub Actions run 35562368282, commit 8829406).
- Phase 3 (real MediaProjection screen capture): implemented with real lifecycle and race protections; the latest CI verification is tracked in GitHub Actions.
- Phase 4 (real first-pass computer vision): **code written, device verification pending**. See "Phase 4 verification" below.

## Phase 3: what is implemented
- Real Android MediaProjection through the system consent dialog. No capture without the user approving it for that session.
- Foreground service (type mediaProjection) with a visible notification and a Stop action. Nothing runs when no capture is active.
- Pipeline: MediaProjection -> VirtualDisplay -> ImageReader. Frames are counted as Android delivers them (long side 1280 px).
- A sparse pixel grid is sampled on some frames to detect black frames (apps/screens that block capture).
- Live evidence on the session screen: resolution, frames received, measured FPS, last-frame age, non-black samples.
- State is honest: "Capturing" only while the engine reports a running capture for that session.
- Real failure states: permission denied (session stays READY), start failure, no frames received (session FAILED), capture stopped by system or notification (saved automatically), app killed during capture (marked FAILED on next launch).
- Screen rotation: the capture display is resized so frames keep full resolution when BGMI is landscape.
- Storage: only metadata in Room (frames counted, resolution, measured rate, sampled/black counts, start/end, reason). No frames, video or audio are stored.
- `FrameSource` for Phase 4: the engine already receives frames on a dedicated thread; Phase 4 will attach the analyzer there.

## Phase 4: what is implemented
- Real CV pipeline: `GraySampler` -> `FramePreprocessor` -> `HeuristicCrosshairDetector` -> `CrosshairTemporalTracker` -> `CvAnalysisEngine`.
- Every third real captured frame is downsampled and analyzed on the capture thread to bound CPU and battery use.
- Crosshair movement uses real frame timestamps and rejects large gaps or implausible jumps instead of fabricating motion.
- Analysis metadata is persisted in the new `cv_analyses` Room table through the additive DB v3-to-v4 migration.
- Session Detail shows frames processed, crosshair detection rate, confidence, movement samples, rejected jumps, skipped frames, and algorithm version.
- No frames or video are persisted. Target detection remains an honest `NoOpTargetDetector`; no Aim Score, recommendation, or AI coaching is included.

## Phase 4 verification
- [ ] APK installs on the realme GT 6T
- [ ] A real BGMI capture produces `Frames processed > 0`
- [ ] Crosshair-visible gameplay produces a non-zero detection rate
- [ ] Moving the view produces movement samples
- [ ] Static/non-gameplay content reports an honest insufficient or not-detected state

## Phase 3: what is NOT implemented
- Computer vision, aim metrics, Aim Score, recommendations (Phase 4+).
- Pausing a live capture (only the timer of a session without capture can be paused).
- Recording/saving frames or video.

## Phase 3 verification (fill in with evidence)
- [ ] GitHub Actions: APK build green
- [ ] GitHub Actions: unit tests green
- [ ] APK installs on realme GT 6T
- [ ] Android capture dialog appears and START works after approving
- [ ] Denying the dialog shows "Screen-capture permission was denied" and session stays READY
- [ ] Frames received counter increases while BGMI is on screen; measured FPS is plausible
- [ ] Black-frame check is not (mostly) black on BGMI
- [ ] Notification Stop and END SESSION both save a session with real frame counts
- [ ] Killing the app during capture shows FAILED on next launch

## How to run a capture
1. Tests tab, pick a test, fill what you know, LOCK CONDITIONS.
2. START SESSION. Allow notifications if asked (optional), then approve Android's screen-capture dialog.
3. Switch to BGMI and do the test.
4. Come back (or use the notification) and END SESSION. Detail screen shows the real capture numbers.

## Session states
DRAFT (form only, never stored) -> READY -> CAPTURE_PENDING -> CAPTURING -> CAPTURE_COMPLETE -> ANALYSIS_PENDING -> ANALYZING -> ANALYZED. Open states can go to CANCELLED, capture states can go to FAILED.
Phase 3 ends sessions in CAPTURE_COMPLETE (frames received, analysis pending), FAILED (no frames / error), or CANCELLED.

## Build (no PC needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Actions tab: "Build APK" builds the APK, then runs the unit tests.
3. Download artifact `AimForge-debug-apk`, unzip, install `app-debug.apk`. Test reports are in artifact `unit-test-reports`.
