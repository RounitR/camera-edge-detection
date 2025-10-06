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

extern "C" JNIEXPORT void JNICALL
Java_com_edgedetection_MainActivity_processFrameNative(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray frameData,
        jint width,
        jint height,
        jint rowStride,
        jint pixelStride) {
    
    LOGI("Processing frame: %dx%d, rowStride=%d, pixelStride=%d", width, height, rowStride, pixelStride);
    
    // Get the frame data from Java byte array
    jbyte* frameBytes = env->GetByteArrayElements(frameData, nullptr);
    if (!frameBytes) {
        LOGE("Failed to get frame data");
        return;
    }
    
    jsize frameSize = env->GetArrayLength(frameData);
    LOGI("Frame data size: %d bytes", frameSize);
    
    // Process the frame data with EdgeProcessor
    EdgeProcessor::processFrameData(
        reinterpret_cast<uint8_t*>(frameBytes),
        width,
        height,
        rowStride,
        pixelStride
    );
    
    // Release the frame data
    env->ReleaseByteArrayElements(frameData, frameBytes, JNI_ABORT);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_edgedetection_MainActivity_processFrameAndReturn(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray frameData,
        jint width,
        jint height,
        jint rowStride,
        jint pixelStride) {
    
    // Get the frame data from Java byte array
    jbyte* frameBytes = env->GetByteArrayElements(frameData, nullptr);
    if (!frameBytes) {
        LOGE("Failed to get frame data");
        return nullptr;
    }
    
    // Process the frame data with EdgeProcessor and get result
    uint8_t* processedData = EdgeProcessor::processFrameDataAndReturn(
        reinterpret_cast<uint8_t*>(frameBytes),
        width,
        height,
        rowStride,
        pixelStride
    );
    
    // Release the input frame data
    env->ReleaseByteArrayElements(frameData, frameBytes, JNI_ABORT);
    
    if (!processedData) {
        LOGE("Failed to process frame data");
        return nullptr;
    }
    
    // Create Java byte array for the processed data
    jsize resultSize = width * height; // Grayscale output
    jbyteArray result = env->NewByteArray(resultSize);
    if (!result) {
        LOGE("Failed to create result byte array");
        return nullptr;
    }
    
    env->SetByteArrayRegion(result, 0, resultSize, reinterpret_cast<jbyte*>(processedData));
    
    return result;
}