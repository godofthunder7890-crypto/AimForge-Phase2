package com.aimforge.app.domain

enum class TestGroup(val title: String) {
    QUICK("Quick"),
    SPRAY("Spray tests"),
    TRACKING("Tracking"),
    OTHER("Other")
}

enum class TestMode(
    val title: String,
    val group: TestGroup,
    val scope: ScopeType?,
    val distance: String,
    val target: String,
    val doText: String,
    val keepText: String
) {
    QUICK_AIM_CHECK("Quick Aim Check", TestGroup.QUICK, null, "Medium", "Stationary", "1 spray, any scope", "Same weapon, attachment, sensitivity"),
    RED_DOT_SPRAY("Red Dot Spray", TestGroup.SPRAY, ScopeType.RED_DOT, "Close", "Stationary", "3 sprays", "Same weapon, attachment, sensitivity"),
    SPRAY_2X("2x Spray", TestGroup.SPRAY, ScopeType.X2, "Medium", "Stationary", "3 sprays", "Same weapon, attachment, sensitivity"),
    SPRAY_3X("3x Spray", TestGroup.SPRAY, ScopeType.X3, "Medium", "Stationary", "3 sprays", "Same weapon, attachment, sensitivity"),
    SPRAY_4X("4x Spray", TestGroup.SPRAY, ScopeType.X4, "Medium-long", "Stationary", "3 sprays", "Same weapon, attachment, sensitivity"),
    SPRAY_6X("6x Spray", TestGroup.SPRAY, ScopeType.X6, "Long", "Stationary", "3 sprays", "Same weapon, attachment, sensitivity"),
    PRECISION_8X("8x Precision", TestGroup.SPRAY, ScopeType.X8, "Long", "Stationary", "10 controlled shots", "Same weapon, attachment, sensitivity"),
    CLOSE_TRACKING("Close-Range Tracking", TestGroup.TRACKING, null, "Close", "Moving", "3 runs of 30 sec tracking", "Same weapon, scope, sensitivity"),
    MID_TRACKING("Mid-Range Tracking", TestGroup.TRACKING, null, "Medium", "Moving", "3 runs of 30 sec tracking", "Same weapon, scope, sensitivity"),
    LONG_TRACKING("Long-Range Tracking", TestGroup.TRACKING, null, "Long", "Moving", "3 runs of 30 sec tracking", "Same weapon, scope, sensitivity"),
    RECOIL_STABILITY("Recoil Stability", TestGroup.OTHER, null, "Medium", "Stationary", "3 full-magazine sprays", "Same weapon and attachments"),
    FLICK_CORRECTION("Flick / Correction", TestGroup.OTHER, null, "Medium", "Two targets", "10 flicks, target to target", "Same weapon, scope, sensitivity"),
    FULL_DIAGNOSTIC("Full Diagnostic", TestGroup.OTHER, null, "Guided", "Guided", "Follow the sequence step by step", "Nothing changes during the whole run");

    companion object {
        val quick: List<TestMode> = listOf(RED_DOT_SPRAY, SPRAY_3X, SPRAY_4X)
    }
}
