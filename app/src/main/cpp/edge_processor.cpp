#include "edge_processor.h"
#include <android/bitmap.h>

#define LOG_TAG "EdgeProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Static member initialization
double EdgeProcessor::lowThreshold = 30.0;  // Optimized for mobile cameras
double EdgeProcessor::highThreshold = 80.0;  // Optimized for mobile cameras
bool EdgeProcessor::isInitialized = false;

// Static buffers for memory reuse
static cv::Mat grayBuffer;
static cv::Mat edgesBuffer;
static cv::Mat blurBuffer;

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
        
        // Ensure buffers are properly sized
        if (grayBuffer.size() != cv::Size(width, height)) {
            grayBuffer.create(height, width, CV_8UC1);
            blurBuffer.create(height, width, CV_8UC1);
            edgesBuffer.create(height, width, CV_8UC1);
        }
        
        // Convert to grayscale
        cv::cvtColor(rgba, grayBuffer, cv::COLOR_RGBA2GRAY);
        
        // Apply optimized Gaussian blur to reduce noise
        cv::GaussianBlur(grayBuffer, blurBuffer, cv::Size(3, 3), 0.8);
        
        // Apply Canny edge detection with optimized parameters
        cv::Canny(blurBuffer, edgesBuffer, lowThreshold, highThreshold, 3, false);
        
        // Convert edges back to RGBA for display
        cv::cvtColor(edgesBuffer, rgba, cv::COLOR_GRAY2RGBA);
        
    } catch (const std::exception& e) {
        LOGE("Error processing frame: %s", e.what());
    }
}

void EdgeProcessor::processFrameData(uint8_t* frameData, int width, int height, int rowStride, int pixelStride) {
    if (!isInitialized) {
        LOGE("EdgeProcessor not initialized");
        return;
    }
    
    try {
        // Create OpenCV Mat from Y plane data (grayscale)
        cv::Mat yPlane(height, width, CV_8UC1, frameData, rowStride);
        
        // Ensure buffers are properly sized (reuse for performance)
        if (grayBuffer.size() != cv::Size(width, height)) {
            grayBuffer.create(height, width, CV_8UC1);
            blurBuffer.create(height, width, CV_8UC1);
            edgesBuffer.create(height, width, CV_8UC1);
            LOGI("Allocated processing buffers for %dx%d", width, height);
        }
        
        // Copy to ensure contiguous memory if needed
        if (rowStride != width) {
            yPlane.copyTo(grayBuffer);
        } else {
            grayBuffer = yPlane.clone();  // Clone to avoid modifying original
        }
        
        // Apply optimized Gaussian blur to reduce noise
        cv::GaussianBlur(grayBuffer, blurBuffer, cv::Size(3, 3), 0.8);
        
        // Apply Canny edge detection with optimized parameters
        cv::Canny(blurBuffer, edgesBuffer, lowThreshold, highThreshold, 3, false);
        
        // Log processing info (limit frequency to avoid spam)
        static int frameCount = 0;
        if (frameCount % 60 == 0) {  // Log every 60 frames (every 2 seconds at 30fps)
            int edgePixels = cv::countNonZero(edgesBuffer);
            double edgeRatio = (double)edgePixels / (width * height) * 100.0;
            LOGI("Frame %d: %dx%d, %.1f%% edge pixels, thresholds: %.1f/%.1f", 
                 frameCount, width, height, edgeRatio, lowThreshold, highThreshold);
        }
        frameCount++;
        
    } catch (const std::exception& e) {
        LOGE("Error processing frame data: %s", e.what());
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
        cv::Mat result;
        
        // Ensure buffers are properly sized
        if (grayBuffer.size() != cv::Size(width, height)) {
            grayBuffer.create(height, width, CV_8UC1);
            blurBuffer.create(height, width, CV_8UC1);
            edgesBuffer.create(height, width, CV_8UC1);
        }
        
        // Convert to grayscale
        cv::cvtColor(rgba, grayBuffer, cv::COLOR_RGBA2GRAY);
        
        // Apply optimized Gaussian blur to reduce noise
        cv::GaussianBlur(grayBuffer, blurBuffer, cv::Size(3, 3), 0.8);
        
        // Apply Canny edge detection with optimized parameters
        cv::Canny(blurBuffer, edgesBuffer, lowThreshold, highThreshold, 3, false);
        
        // Convert edges back to RGBA for display
        cv::cvtColor(edgesBuffer, result, cv::COLOR_GRAY2RGBA);
        
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

uint8_t* EdgeProcessor::processFrameDataAndReturn(uint8_t* frameData, int width, int height, int rowStride, int pixelStride) {
    if (!isInitialized) {
        LOGE("EdgeProcessor not initialized");
        return nullptr;
    }
    
    try {
        // Create OpenCV Mat from Y plane data (grayscale)
        cv::Mat yPlane(height, width, CV_8UC1, frameData, rowStride);
        
        // Ensure we have proper buffer sizing
        if (grayBuffer.rows != height || grayBuffer.cols != width) {
            grayBuffer = cv::Mat::zeros(height, width, CV_8UC1);
            edgesBuffer = cv::Mat::zeros(height, width, CV_8UC1);
            blurBuffer = cv::Mat::zeros(height, width, CV_8UC1);
        }
        
        // Handle rowStride differences
        if (rowStride != width) {
            // Copy to contiguous buffer
            for (int i = 0; i < height; i++) {
                memcpy(grayBuffer.ptr(i), yPlane.ptr(i), width);
            }
        } else {
            grayBuffer = yPlane.clone();
        }
        
        // Apply Gaussian blur to reduce noise
        cv::GaussianBlur(grayBuffer, blurBuffer, cv::Size(5, 5), 1.4);
        
        // Apply Canny edge detection
        cv::Canny(blurBuffer, edgesBuffer, lowThreshold, highThreshold);
        
        // Allocate result buffer
        uint8_t* result = new uint8_t[width * height];
        
        // Copy processed data to result buffer
        if (edgesBuffer.isContinuous()) {
            memcpy(result, edgesBuffer.data, width * height);
        } else {
            for (int i = 0; i < height; i++) {
                memcpy(result + i * width, edgesBuffer.ptr(i), width);
            }
        }
        
        return result;
        
    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception in processFrameDataAndReturn: %s", e.what());
        return nullptr;
    } catch (const std::exception& e) {
        LOGE("Exception in processFrameDataAndReturn: %s", e.what());
        return nullptr;
    }
}