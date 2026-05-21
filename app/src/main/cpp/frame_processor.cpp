#include <jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <cmath>
#include <atomic>

#define LOG_TAG "FrameProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static cv::Mat prevRoiMat;
static std::atomic<int> roiX{0}, roiY{0}, roiW{320}, roiH{320};
static std::atomic<double> deltaThreshold{15.0};

static int frameWidth = 0;
static int frameHeight = 0;

/**
 * Notifies the Kotlin AutomatedDecisionEngine of a critical frame event.
 */
static void notifyDecisionEngine(JNIEnv* env, jobject thiz, int x, int y, double delta) {
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID mid = env->GetMethodID(clazz, "onCriticalFrameEvent", "(IID)V");
    if (mid) {
        env->CallVoidMethod(thiz, mid, (jint)x, (jint)y, (jdouble)delta);
    }
    env->DeleteLocalRef(clazz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_AutomatedDecisionEngine_processFrameNative(
        JNIEnv* env, jobject thiz, jobject imageObj) {

    uint8_t* yData = nullptr;
    int32_t width = 0, height = 0;

    // Reflect into Java to get plane buffer (zero-copy path)
    jclass imgClass = env->GetObjectClass(imageObj);
    jmethodID getWidth  = env->GetMethodID(imgClass, "getWidth",  "()I");
    jmethodID getHeight = env->GetMethodID(imgClass, "getHeight", "()I");

    width  = env->CallIntMethod(imageObj, getWidth);
    height = env->CallIntMethod(imageObj, getHeight);
    env->DeleteLocalRef(imgClass);

    if (width <= 0 || height <= 0) return;
    frameWidth  = width;
    frameHeight = height;

    // Get planes array
    jclass imageClass = env->FindClass("android/media/Image");
    jmethodID getPlanesMethod = env->GetMethodID(imageClass, "getPlanes",
        "()[Landroid/media/Image$Plane;");
    jobjectArray planes = (jobjectArray)env->CallObjectMethod(imageObj, getPlanesMethod);
    env->DeleteLocalRef(imageClass);

    jobject yPlane = env->GetObjectArrayElement(planes, 0);
    jclass planeClass = env->GetObjectClass(yPlane);
    jmethodID getBuffer = env->GetMethodID(planeClass, "getBuffer",
        "()Ljava/nio/ByteBuffer;");
    jobject yBuffer = env->CallObjectMethod(yPlane, getBuffer);

    yData = (uint8_t*)env->GetDirectBufferAddress(yBuffer);
    yLen  = (int)env->GetDirectBufferCapacity(yBuffer);

    env->DeleteLocalRef(yBuffer);
    env->DeleteLocalRef(planeClass);
    env->DeleteLocalRef(yPlane);
    env->DeleteLocalRef(planes);

    if (!yData || yLen <= 0) return;

    // Build Y-plane Mat — zero-copy (points to ImageReader buffer)
    cv::Mat yMat(height, width, CV_8UC1, yData);

    // Clamp ROI to frame bounds
    int rx = std::min(roiX.load(), width  - 1);
    int ry = std::min(roiY.load(), height - 1);
    int rw = std::min(roiW.load(), width  - rx);
    int rh = std::min(roiH.load(), height - ry);
    if (rw <= 0 || rh <= 0) return;

    cv::Mat roi = yMat(cv::Rect(rx, ry, rw, rh));

    // ── Frame Skipping ────────────────────────────────────────────────────────
    if (prevRoiMat.empty() || prevRoiMat.size() != roi.size()) {
        roi.copyTo(prevRoiMat);
        return;
    }

    cv::Mat diff;
    cv::absdiff(roi, prevRoiMat, diff);
    double delta = cv::mean(diff)[0];

    if (delta < deltaThreshold.load()) {
        // Identical enough — skip without notifying Kotlin
        return;
    }

    // ── Critical change detected ──────────────────────────────────────────────
    double maxVal;
    cv::Point maxLoc;
    cv::minMaxLoc(diff, nullptr, &maxVal, nullptr, &maxLoc);

    // Map ROI-local coords back to full-frame coords
    int globalX = rx + maxLoc.x;
    int globalY = ry + maxLoc.y;

    LOGI("Critical frame event at (%d, %d), delta=%.2f", globalX, globalY, delta);
    notifyDecisionEngine(env, thiz, globalX, globalY, delta);

    roi.copyTo(prevRoiMat);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_AutomatedDecisionEngine_setRoi(
        JNIEnv*, jobject, jint x, jint y, jint width, jint height) {
    roiX = x; roiY = y; roiW = width; roiH = height;
    prevRoiMat.release(); // force re-baseline on next frame
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_AutomatedDecisionEngine_setDeltaThreshold(
        JNIEnv*, jobject, jdouble threshold) {
    deltaThreshold = threshold;
}
