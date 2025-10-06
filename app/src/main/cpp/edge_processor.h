#ifndef EDGE_PROCESSOR_H
#define EDGE_PROCESSOR_H

#include <opencv2/opencv.hpp>
#include <android/log.h>
#include <jni.h>

class EdgeProcessor {
public:
    static bool initialize();
    static void processFrame(void* pixels, int width, int height);
    static jobject processFrameAndReturn(JNIEnv* env, void* pixels, int width, int height, int format);
    static void processFrameData(uint8_t* frameData, int width, int height, int rowStride, int pixelStride);
    static uint8_t* processFrameDataAndReturn(uint8_t* frameData, int width, int height, int rowStride, int pixelStride);
    static void setCannyThresholds(double lowThreshold, double highThreshold);
    
private:
    static double lowThreshold;
    static double highThreshold;
    static bool isInitialized;
    static jobject createBitmapFromMat(JNIEnv* env, const cv::Mat& mat);
};

#endif // EDGE_PROCESSOR_H