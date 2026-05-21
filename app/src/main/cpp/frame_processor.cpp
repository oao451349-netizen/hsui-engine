#include <jni.h>
#include <android/log.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
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

    jclass imgClass = env->GetObjectClass(imageObj);
    jmethodID getWidth  = env->GetMethodID(imgClass, "getWidth",  "()I");
    jmethodID getHeight = env->GetMethodID(imgClass, "getHeight", "()I");

    int32_t width  = env->CallIntMethod(imageObj, getWidth);
    int32_t height = env->CallIntMethod(imageObj, getHeight);
    env->DeleteLocalRef(imgClass);

    if (width <= 0 || height <= 0) return;
    frameWidth  = width;
    frameHeight = height;

    jclass imageClass = env->FindClass("android/media/Image");
    jmethodID getPlanesMethod = env->GetMethodID(imageClass, "getPlanes",
        "()[Landroid/media/Image$Plane;");
    jobjectArray planes = (jobjectArray)env->CallObjectMethod(imageObj, getPlanesMethod);
    env->DeleteLocalRef(imageClass);

    int planeCount = (int)env->GetArrayLength(planes);

    jobject plane0 = env->GetObjectArrayElement(planes, 0);
    jclass planeClass = env->GetObjectClass(plane0);
    jmethodID getBuffer      = env->GetMethodID(planeClass, "getBuffer",      "()Ljava/nio/ByteBuffer;");
    jmethodID getRowStride   = env->GetMethodID(planeClass, "getRowStride",   "()I");
    jmethodID getPixelStride = env->GetMethodID(planeClass, "getPixelStride", "()I");

    jobject buf     = env->CallObjectMethod(plane0, getBuffer);
    int rowStride   = env->CallIntMethod(plane0, getRowStride);
    int pixelStride = env->CallIntMethod(plane0, getPixelStride);

    uint8_t* data = (uint8_t*)env->GetDirectBufferAddress(buf);
    int dataLen   = (int)env->GetDirectBufferCapacity(buf);

    env->DeleteLocalRef(buf);
    env->DeleteLocalRef(planeClass);
    env->DeleteLocalRef(plane0);
    env->DeleteLocalRef(planes);

    if (!data || dataLen <= 0) return;

    // ── Convert to grayscale depending on format ──────────────────────────────
    cv::Mat grayMat;

    if (planeCount == 1) {
        // RGBA_8888 from VirtualDisplay / screen capture
        int stridePixels = rowStride / 4;
        cv::Mat rgbaMat(height, stridePixels, CV_8UC4, data);
        cv::Mat cropped = rgbaMat(cv::Rect(0, 0, width, height));
        cv::cvtColor(cropped, grayMat, cv::COLOR_RGBA2GRAY);
    } else {
        // YUV_420_888 — use Y plane (luminance) directly
        cv::Mat yMat(height, width, CV_8UC1, data);
        grayMat = yMat.clone();
    }

    // ── Clamp ROI ─────────────────────────────────────────────────────────────
    int rx = std::min(roiX.load(), width  - 1);
    int ry = std::min(roiY.load(), height - 1);
    int rw = std::min(roiW.load(), width  - rx);
    int rh = std::min(roiH.load(), height - ry);
    if (rw <= 0 || rh <= 0) return;

    cv::Mat roi = grayMat(cv::Rect(rx, ry, rw, rh));

    // ── Frame delta ───────────────────────────────────────────────────────────
    if (prevRoiMat.empty() || prevRoiMat.size() != roi.size()) {
        roi.copyTo(prevRoiMat);
        return;
    }

    cv::Mat diff;
    cv::absdiff(roi, prevRoiMat, diff);
    double delta = cv::mean(diff)[0];

    if (delta < deltaThreshold.load()) {
        return;
    }

    // ── Critical change ───────────────────────────────────────────────────────
    double maxVal;
    cv::Point maxLoc;
    cv::minMaxLoc(diff, nullptr, &maxVal, nullptr, &maxLoc);

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
    prevRoiMat.release();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_engine_AutomatedDecisionEngine_setDeltaThreshold(
        JNIEnv*, jobject, jdouble threshold) {
    deltaThreshold = threshold;
}
