package com.aimforge.app.domain

enum class ScopeType(val label: String) {
    TPP_NO_SCOPE("TPP No Scope"),
    FPP_NO_SCOPE("FPP No Scope"),
    RED_DOT("Red Dot / Holo"),
    X2("2x"),
    X3("3x"),
    X4("4x"),
    X6("6x"),
    X8("8x")
}

enum class SensType(val label: String) {
    CAMERA("Camera"),
    ADS("ADS"),
    GYRO("Gyroscope"),
    ADS_GYRO("ADS Gyroscope")
}

/** Classifier output states. Classifier itself arrives in Phase 5/6; enum is fixed now so the DB stays stable. */
enum class AimErrorState(val label: String) {
    VERTICAL_HIGH("Vertical high"),
    VERTICAL_LOW("Vertical low"),
    HORIZONTAL_LEFT("Horizontal left"),
    HORIZONTAL_RIGHT("Horizontal right"),
    OVER_CORRECTION("Over-correction"),
    UNDER_CORRECTION("Under-correction"),
    TRACKING_LAG("Tracking lag"),
    TRACKING_OVERSHOOT("Tracking overshoot"),
    RANDOM_SHAKE("Random shake"),
    RECOIL_INSTABILITY("Recoil instability"),
    GOOD("Good"),
    INSUFFICIENT_DATA("Insufficient data")
}
