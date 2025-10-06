#ifndef EDGE_PROCESSOR_H
#define EDGE_PROCESSOR_H

#include <opencv2/opencv.hpp>
#include <android/log.h>

class EdgeProcessor {
public:
    static bool initialize();
    static void processFrame(void* pixels, int width, int height);
    static void setCannyThresholds(double lowThreshold, double highThreshold);
    
private:
    static double lowThreshold;
    static double highThreshold;
    static bool isInitialized;
};

#endif // EDGE_PROCESSOR_H