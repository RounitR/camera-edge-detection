#include "edge_processor.h"
#include <android/bitmap.h>

#define LOG_TAG "EdgeProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Static member initialization
double EdgeProcessor::lowThreshold = 50.0;
double EdgeProcessor::highThreshold = 150.0;
bool EdgeProcessor::isInitialized = false;

bool EdgeProcessor::initialize() {
    try {
        LOGI("OpenCV version: %s", cv::getVersionString().c_str());
        isInitialized = true;
        return true;
    } catch (const std::exception& e) {
        LOGE("Failed to initialize OpenCV: %s", e.what());
        return false;
    }
}

void EdgeProcessor::processFrame(void* pixels, int width, int height) {
    if (!isInitialized) {
        LOGE("EdgeProcessor not initialized");
        return;
    }
    
    try {
        // Create OpenCV Mat from RGBA pixels
        cv::Mat rgba(height, width, CV_8UC4, pixels);
        cv::Mat gray, edges;
        
        // Convert to grayscale
        cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);
        
        // Apply Gaussian blur to reduce noise
        cv::GaussianBlur(gray, gray, cv::Size(5, 5), 1.4);
        
        // Apply Canny edge detection
        cv::Canny(gray, edges, lowThreshold, highThreshold);
        
        // Convert edges back to RGBA for display
        cv::cvtColor(edges, rgba, cv::COLOR_GRAY2RGBA);
        
    } catch (const std::exception& e) {
        LOGE("Error processing frame: %s", e.what());
    }
}

void EdgeProcessor::setCannyThresholds(double low, double high) {
    lowThreshold = low;
    highThreshold = high;
    LOGI("Updated Canny thresholds: low=%.1f, high=%.1f", low, high);
}

jobject EdgeProcessor::processFrameAndReturn(JNIEnv* env, void* pixels, int width, int height, int format) {
    if (!isInitialized) {
        LOGE("EdgeProcessor not initialized");
        return nullptr;
    }
    
    try {
        // Create OpenCV Mat from RGBA pixels
        cv::Mat rgba(height, width, CV_8UC4, pixels);
        cv::Mat gray, edges, result;
        
        // Convert to grayscale
        cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);
        
        // Apply Gaussian blur to reduce noise
        cv::GaussianBlur(gray, gray, cv::Size(5, 5), 1.4);
        
        // Apply Canny edge detection
        cv::Canny(gray, edges, lowThreshold, highThreshold);
        
        // Convert edges back to RGBA for display
        cv::cvtColor(edges, result, cv::COLOR_GRAY2RGBA);
        
        // Create and return new bitmap
        return createBitmapFromMat(env, result);
        
    } catch (const std::exception& e) {
        LOGE("Error processing frame: %s", e.what());
        return nullptr;
    }
}

jobject EdgeProcessor::createBitmapFromMat(JNIEnv* env, const cv::Mat& mat) {
    // Find Bitmap class and createBitmap method
    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Obj = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);
    
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap", 
        "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    
    // Create bitmap
    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID, 
        mat.cols, mat.rows, argb8888Obj);
    
    // Lock pixels and copy data
    AndroidBitmapInfo info;
    void* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) >= 0 && 
        AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0) {
        
        // Copy mat data to bitmap
        memcpy(pixels, mat.data, mat.total() * mat.elemSize());
        AndroidBitmap_unlockPixels(env, bitmap);
    }
    
    return bitmap;
}