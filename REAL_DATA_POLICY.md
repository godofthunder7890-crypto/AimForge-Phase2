# AimForge real-data policy

Priority: real functionality > UI, accurate analysis > animation, real data > demo data, honest limits > fake features.

## Rules (apply to every phase)
- First install = empty database. No seeding, no demo rows, no dummy sessions, ever.
- Every number on screen comes from: (1) captured gameplay frames, (2) values the user typed, (3) Android device/API data, or (4) a calculation on those.
- Sessions are inserted only by a completed test run. Nothing else writes to `test_sessions`.
- Scores, graphs, recommendations, history are derived from stored sessions and must be reproducible from them.
- No data -> "No analysis yet" / "Run your first test."
- Low confidence -> "Not enough visual evidence. Retest." and no recommendation.
- Feature not implemented or not reliable -> "Not available yet" + the technical reason. No fake or disabled-looking-working controls.
- Test data allowed only behind an explicit developer mode that is not in the release build. (None exists yet.)

## Where each Phase 1 value comes from
| Screen value | Source |
|---|---|
| Device name | `Build.MANUFACTURER` + `Build.MODEL`, user-editable |
| Control layout, gyro | user choice in onboarding |
| Sensitivity values | typed by user in Lab |
| Session list, duration, conditions | `test_sessions` rows created by the user locking a real setup |
| FPS, refresh rate | typed/selected by the user (screen Hz shown as a device API hint) |
| Sensitivity snapshot | user-entered, stored with the session, NULL if unknown |
| Aim score, pattern, stability | only when `analysisStatus = ANALYZED` (no engine exists yet) |
| Strongest/weakest scope | avg of stored scores, only with >= 3 scored tests per scope |
| Trend | last 3 vs previous 3 stored scores, needs >= 6 |

## Feature-complete checklist (must all be YES before a phase is closed)
1. Does the button do the advertised action?
2. Does the result come from real data?
3. Can it be reproduced from the stored test?
4. Is the calculation actually implemented?
5. Verified on a real realme GT 6T?
6. Behaviour with insufficient data defined?
7. Behaviour when screen capture fails defined?
8. Behaviour when CV cannot find crosshair/target defined?

## Phase 3 data sources
| Screen value | Source |
|---|---|
| Frames received, measured FPS, last frame age | counters updated only when Android delivers a frame to ImageReader |
| Resolution | size of the VirtualDisplay actually created |
| Non-black samples | pixel grid read from real delivered frames |
| Capture status "Capturing" | engine state RUNNING for that session (VirtualDisplay exists) |
| Permission denied / failure reasons | Android result code or the real exception message |

## Phase 4 data sources
| Screen value | Source |
|---|---|
| Frames processed, frames with crosshair, detection rate | Real captured frames submitted to the CV pipeline |
| Movement samples, speed, distance | Accepted crosshair detections with real frame timestamps |
| Confidence | Detector evidence from contrast, compactness, and centeredness |
| Frames skipped | Real capture frame count minus frames submitted for analysis |
| Frames with target | Always 0 in this phase; no real target detector is shipped |
