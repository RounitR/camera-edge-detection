#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/bitmap.h>
#include "edge_processor.h"

#define LOG_TAG "EdgeDetection"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_edgedetection_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_edgedetection_EdgeProcessor_initializeOpenCV(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("Initializing OpenCV");
    return EdgeProcessor::initialize();
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_edgedetection_EdgeProcessor_processFrame(
        JNIEnv* env,
        jobject /* this */,
        jobject bitmap) {
    
    AndroidBitmapInfo info;
    void* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed to get bitmap info");
        return nullptr;
    }
    
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        LOGE("Failed to lock bitmap pixels");
        return nullptr;
    }
    
    // Create a copy of the bitmap for processing
    jobject resultBitmap = EdgeProcessor::processFrameAndReturn(env, pixels, info.width, info.height, info.format);
    
    AndroidBitmap_unlockPixels(env, bitmap);
    
    return resultBitmap ? resultBitmap : bitmap;
}