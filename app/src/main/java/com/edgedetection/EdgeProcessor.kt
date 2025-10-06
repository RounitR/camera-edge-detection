package com.edgedetection

import android.graphics.Bitmap

class EdgeProcessor {
    
    external fun initializeOpenCV(): Boolean
    external fun processFrame(bitmap: Bitmap)
    
    companion object {
        init {
            System.loadLibrary("edgedetection")
        }
    }
}