# HSUI Engine — High-Speed UI Automation Engine

## Структура проекта

```
app/src/main/
├── kotlin/com/example/engine/
│   ├── AutomatedDecisionEngine.kt   — управление потоком кадров и TaskChain
│   └── TouchSimulationService.kt   — AccessibilityService, диспатч жестов
├── cpp/
│   ├── frame_processor.cpp          — JNI + OpenCV: zero-copy обработка YUV, Frame Skipping
│   └── CMakeLists.txt               — сборка нативной библиотеки
└── res/
    └── xml/accessibility_service_config.xml
```

## Сборка

1. Установить [OpenCV Android SDK](https://opencv.org/releases/) (4.x).
2. Указать путь к SDK в `gradle.properties`:
   ```
   OPENCV_DIR=/path/to/OpenCV-android-sdk/sdk/native/jni
   ```
3. Собрать проект:
   ```bash
   ./gradlew assembleRelease
   ```

## Требования

- Android API 24+ (GestureDescription.StrokeDescription)
- NDK r25+
- CMake 3.22.1+
- OpenCV 4.x (статическая сборка для Android)

## Ключевые параметры

| Параметр | Значение по умолчанию | Метод настройки |
|---|---|---|
| Delta threshold | 15.0 | `engine.setDeltaThreshold(value)` |
| ROI | (0,0,320,320) | `engine.setRoi(x, y, w, h)` |
| Min stroke duration | 1 мс | `MIN_STROKE_DURATION_MS` |
| Coord step (120 Hz) | 8 мс | `COORD_UPDATE_STEP_MS` |

## Разрешения AndroidManifest.xml (добавить вручную)

```xml
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<service
    android:name=".TouchSimulationService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```
