# Keep JNI-called methods in AutomatedDecisionEngine
-keep class com.example.engine.AutomatedDecisionEngine {
    @androidx.annotation.Keep *;
    native <methods>;
    public void onCriticalFrameEvent(int, int, double);
}

# Keep AccessibilityService subclass
-keep class com.example.engine.TouchSimulationService { *; }
