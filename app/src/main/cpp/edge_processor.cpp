#include "edge_processor.h"

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