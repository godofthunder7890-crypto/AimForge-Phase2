# AimForge
Personal BGMI aim coach. Observes only what you share via Android screen capture. No game modification, no input simulation, no anti-cheat interaction, no server, no ads, no accounts, no INTERNET permission.

## Status
- Phase 1, Phase 2, Phase 3: complete and verified on a real realme GT 6T. Phase 3 evidence: 2805 frames received over a 1:45 capture, measured rate 33.1 fps, resolution 580x1280, 178/187 non-black samples.
- Phase 4 (real computer vision, first pass): **code written, NOT yet verified on device.** See "Phase 4 verification" below.

## Phase 4: what is implemented
- A real CV pipeline, package `domain/cv`: `FramePreprocessor` -> `CrosshairVisionDetector` -> `TemporalTracker` -> `CvAnalysisEngine`, plus `AimFeatureAnalyzer` and a `TargetVisionDetector` interface for later phases.
- `HeuristicCrosshairDetector` (v1, `center-bright-cluster-v1`): looks for a small, bright, centered, high-contrast cluster in the central 50% of the frame. Returns NOT_DETECTED with no guessed position when there is no confident cluster.
- `CrosshairTemporalTracker`: turns detections into real movement (delta, distance, speed, direction) using real frame timestamps. Rejects gaps over 1.5s and implausible jumps (>6 normalized units/sec) instead of fabricating movement across them.
- `NoOpTargetDetector`: Phase 4 ships no real target detector — reliable target detection needs more than a single-frame heuristic, so every frame is honestly NOT_DETECTED rather than guessed. `AimFeatureAnalyzer`'s error math is implemented and unit-tested so Phase 5/6 can use it once a real target detector exists.
- `GraySampler` (capture package): downsamples a real captured RGBA frame to a small grayscale grid for analysis (long side 160px), independent of Android types so it is plain-JVM testable.
- Wired into `CaptureService`: a fresh `CvAnalysisEngine` is created when a capture starts; every 3rd real captured frame is analyzed (throttled for CPU/battery); the result is finalized and saved once, from real accumulated counts, when the capture ends. Frames that were captured but not analyzed are reported honestly as `droppedFrames`.
- New table `cv_analyses` (DB v4, additive migration only). One row per session. Session Detail screen has a new COMPUTER VISION card showing frames processed, detection rate, confidence, movement samples, rejected jumps, missed frames, algorithm version — "Not analyzed" if no row exists yet.
- Analysis states used honestly: NO_DATA, INSUFFICIENT_DATA, CROSSHAIR_NOT_DETECTED, LOW_CONFIDENCE, PARTIAL_ANALYSIS, ANALYZED (PROCESSING and TARGET_NOT_DETECTED are defined for future use; the current pipeline does not need PROCESSING for a stored result and never blocks on target since none is implemented).

## Phase 4: what is NOT implemented
- Sensitivity recommendations, Aim Score, AI coaching, before/after experiments (Phase 5/6).
- A real target detector (see above) — crosshair-to-target error is therefore never computed today.
- Persisting frames or video; only metadata is stored, as in Phase 3.

## Phase 4 verification (fill in with evidence)
- [ ] GitHub Actions: APK build green
- [ ] GitHub Actions: unit tests green
- [ ] APK installs on realme GT 6T
- [ ] Run a capture over real BGMI gameplay, end the session
- [ ] Session Detail -> COMPUTER VISION shows Frames processed > 0 and a plausible detection rate
- [ ] With the crosshair visible on screen, detection rate is clearly above 0% (confirms the detector sees something real, not just noise)
- [ ] Movement samples appear when you move the view during the test
- [ ] A capture with the screen mostly static (main menu, not BGMI) still shows an honest state (CROSSHAIR_NOT_DETECTED / LOW_CONFIDENCE / INSUFFICIENT_DATA), not a fabricated ANALYZED

## Phase 5: crosshair detector fix + real aim metrics
**Status: NOT COMPLETE. Crosshair detection is not yet verified on real BGMI footage.** Code written; not built, not tested in CI, not run on a device.

### Why the old detector (center-bright-cluster-v1) got 0% on two real BGMI sessions (analysis from the code, not from frames)
1. It scored luma. A saturated red crosshair has luma ~76/255, so a "bright" threshold cannot see it.
2. It downsampled the whole frame to ~160px wide before detecting, using point sampling. A reticle a few pixels wide is skipped by that downsample.
3. It used one global ROI-mean threshold and one global centroid. One large bright region (sky, wall) raised the threshold and dragged the centroid; its own "reject large box" rule then rejected every frame. That gives a systematic 0%.
These are hypotheses that fit a systematic 0%. They have not been confirmed against real frames. If v2 still gives 0% or false detections on BGMI, the cause is something else and needs real frames (or dev diagnostics) to find.

### Detector v2: cross-structure-contrast-v2
- Input: central crop (55% of the frame, source-pixel-scaled, never upsampled) sampled as max(R,G,B) via `GraySampler.buildCentralCrop`. Positions are mapped back to full-frame normalized coordinates (`Frame.toSourceNormX/Y`), so portrait/landscape and resolution changes need no hardcoded pixels.
- Local adaptive threshold per 12px cell (pixel must beat its own cell mean by max(14, 22% of headroom)), so large uniform bright areas do not trigger.
- Connected components -> multiple candidates, each scored: 0.35 contrast + 0.30 compactness + 0.20 centeredness + shape bonus (hollow/thin 0.15, elongated 0.10). Candidates over 30% of the crop area are dropped. Best score >= 0.22 wins, otherwise NOT_DETECTED with `rejectionReason` and `candidateCount` (diagnostics only). Screen center is a scoring preference, never an automatic detection.
- Thresholds are documented starting values chosen from geometry reasoning, not tuned to any detection rate. They will probably need adjusting against real BGMI frames.
- Temporal validation is unchanged (`CrosshairTemporalTracker`: gaps, rejected jumps, out-of-order timestamps).
- v1 (`HeuristicCrosshairDetector`) is kept with its tests but is no longer the default.

### Observation retention (fixes the 1200-observation cap)
Phase 5 metrics are now computed by `StreamingAimMetricsAccumulator`, fed from `CvAnalysisEngine.onFrame` for every processed frame. Counts, sums, extremes, variance, direction changes, acceleration are exact over the whole session in O(1) memory. Medians (distance, speed, micro-adjustment, target error) come from bounded reservoir samples (2000 each, Algorithm R): exact when the session has <= 2000 samples, an unbiased sample of the whole session otherwise. Only a 300-observation diagnostic tail is retained, and no metric uses it. `AimMetricsEngine.analyze(list)` remains as a thin wrapper over the same accumulator.

### Tracking continuity (redefined)
`trackingContinuity` = sum of dt over accepted movement steps (two consecutive confident detections within maxGapMs) / (last - first processed timestamp). Null if there is no time span. `crosshairObservationCoverage` = confident crosshair frames / processed frames (the old formula, now under an accurate name). DB v6 adds the coverage column; old rows read as null, never backfilled.

### Frame terms
Received = frames Android delivered (Phase 3 counter). Processed = frames the CV engine saw (every 3rd). Skipped/throttled = received minus processed (`droppedFrames`). Every-3rd throttling is unchanged; it can be lowered later if real tracking needs more temporal resolution. Not benchmarked here.

### Target metrics
Still unavailable (null): no target detector exists. Target error, overshoot, correction, time-in-target stay null unless real target observations arrive.

### Known limitations
- The streaming accumulator and the original batch calculators (`MovementCalculator`, `KinematicsCalculator`, ...) implement the same math twice. The batch ones now only have their own unit tests. Unify later.
- `Migration56Test` hand-writes the v5 schema; if CI flags a schema mismatch, that test fixture is the first suspect.

### Phase 5 verification (nothing below is done yet)
- [ ] GitHub Actions green (build + all tests)
- [ ] APK on realme GT 6T
- [ ] Test A (red dot / normal) and Test B (another scope): record duration, frames received/processed/skipped, crosshair frames, detection rate, avg confidence, movement samples, rejected jumps, missed detections, CV state, metrics state
- [ ] Manually confirm detections sit on the real crosshair and false detections are rare
- [ ] Movement metrics follow real crosshair movement; results persist after reopening the app
- [ ] If detection is still 0% or unreliable: Phase 5 stays NOT COMPLETE

## Build (no PC needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Actions tab: "Build APK" builds the APK, then runs the unit tests.
3. Download artifact `AimForge-debug-apk`, unzip, install `app-debug.apk`. Test reports are in artifact `unit-test-reports`.
