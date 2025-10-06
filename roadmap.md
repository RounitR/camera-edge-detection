# Real-Time Edge Detection Viewer - Project Roadmap

## Project Vision
Build a minimal Android application that captures camera frames, processes them using OpenCV in C++ via JNI, displays the processed output using OpenGL ES, and includes a TypeScript web viewer for demonstration purposes. This project demonstrates integration skills across Android development, native C++ processing, OpenGL rendering, and web technologies.

## Tech Stack Choices

### Android Layer
- **Language**: Kotlin (modern, concise, excellent JNI interoperability)
- **Camera API**: Camera2 with TextureView and ImageReader
- **UI Framework**: Native Android Views (TextureView, GLSurfaceView)
- **Build System**: Gradle with NDK integration

### Native Processing Layer
- **Language**: C++ with NDK
- **Computer Vision**: OpenCV 4.x (Android SDK)
- **Build System**: CMake
- **Target ABI**: arm64-v8a (single ABI for simplicity)

### Rendering Layer
- **Graphics API**: OpenGL ES 2.0
- **Surface**: GLSurfaceView
- **Shaders**: Basic vertex/fragment shaders for texture rendering

### Web Viewer
- **Language**: TypeScript
- **Build Tool**: tsc (TypeScript compiler)
- **Framework**: Vanilla HTML/CSS/JS (no frameworks)

### Development Tools
- **Version Control**: Git with meaningful commit messages
- **IDE**: Android Studio / Trae AI
- **Testing**: Physical Android device (recommended for camera performance)

## Development Roadmap

### Phase 1: Project Setup & Foundation (Day 1 Morning)
**Duration**: 3-4 hours

#### Checkpoint 1.1: Initial Project Structure
- Create Android project with Kotlin
- Set up basic project structure (/app, /jni, /gl, /web)
- Configure Gradle for NDK integration
- Add OpenCV Android SDK dependency
- **Commit**: "Initial project setup with NDK and OpenCV integration"

#### Checkpoint 1.2: Basic Camera Integration
- Implement Camera2 permission handling
- Set up TextureView for camera preview
- Create basic activity layout
- Test camera preview functionality
- **Commit**: "Add camera preview with Camera2 and TextureView"

#### Checkpoint 1.3: JNI Bridge Foundation
- Create basic JNI interface
- Set up CMakeLists.txt for native build
- Implement simple native function (test connectivity)
- Verify JNI communication works
- **Commit**: "Establish JNI bridge with basic native function"

### Phase 2: Core Processing Pipeline (Day 1 Afternoon - Day 2 Morning)
**Duration**: 6-8 hours

#### Checkpoint 2.1: Frame Capture Pipeline
- Implement ImageReader for frame capture
- Extract Y plane from YUV_420_888 format
- Pass frame data to native layer via JNI
- Handle frame metadata (width, height, stride)
- **Commit**: "Implement frame capture and JNI data transfer"

#### Checkpoint 2.2: OpenCV Processing
- Set up OpenCV in native code
- Implement grayscale processing (Y plane direct use)
- Add Canny edge detection with tuned parameters
- Optimize buffer reuse and memory management
- **Commit**: "Add OpenCV grayscale and Canny edge detection"

#### Checkpoint 2.3: Processing Pipeline Optimization
- Implement frame processing thread
- Add frame rate limiting and queue management
- Optimize for 10-15 FPS target performance
- Test on device with performance monitoring
- **Commit**: "Optimize processing pipeline for target FPS"

### Phase 3: OpenGL Rendering (Day 2 Afternoon)
**Duration**: 4-5 hours

#### Checkpoint 3.1: OpenGL Setup
- Implement GLSurfaceView and Renderer
- Create basic vertex and fragment shaders
- Set up texture creation and management
- Implement basic quad rendering
- **Commit**: "Set up OpenGL ES renderer with basic shaders"

#### Checkpoint 3.2: Texture Upload Pipeline
- Implement texture upload from processed frames
- Add double-buffering for smooth rendering
- Handle different texture formats (grayscale/RGBA)
- Test rendering pipeline end-to-end
- **Commit**: "Complete texture upload and rendering pipeline"

#### Checkpoint 3.3: Rendering Optimization
- Optimize texture uploads and GL state management
- Add frame synchronization between processing and rendering
- Implement smooth frame display
- **Commit**: "Optimize OpenGL rendering performance"

### Phase 4: Feature Integration & Polish (Day 2 Evening - Day 3 Morning)
**Duration**: 4-5 hours

#### Checkpoint 4.1: UI Controls
- Add toggle button for raw vs processed view
- Implement view switching functionality
- Add basic error handling and user feedback
- **Commit**: "Add UI controls for view switching"

#### Checkpoint 4.2: Performance Monitoring
- Implement FPS counter display
- Add frame processing time logging
- Create performance overlay
- **Commit**: "Add FPS counter and performance monitoring"

#### Checkpoint 4.3: Error Handling & Stability
- Add comprehensive error handling
- Implement proper lifecycle management
- Handle edge cases and device variations
- **Commit**: "Improve error handling and app stability"

### Phase 5: Web Viewer & Documentation (Day 3 Afternoon)
**Duration**: 3-4 hours

#### Checkpoint 5.1: TypeScript Web Viewer
- Set up TypeScript project structure
- Create basic HTML layout
- Implement image display functionality
- Add frame statistics overlay
- **Commit**: "Create TypeScript web viewer with basic functionality"

#### Checkpoint 5.2: Web Viewer Enhancement
- Add base64 image loading from Android export
- Implement stats display (FPS, resolution)
- Style the interface cleanly
- Test web viewer functionality
- **Commit**: "Complete web viewer with stats display"

#### Checkpoint 5.3: Final Documentation
- Create comprehensive README.md
- Add setup instructions and dependencies
- Include architecture explanation
- Add screenshots and usage examples
- **Commit**: "Add comprehensive documentation and README"

## Execution Plan Details

### Daily Breakdown

#### Day 1: Foundation & Core Pipeline
- **Morning (4 hours)**: Project setup, camera integration, JNI foundation
- **Afternoon (4 hours)**: Frame capture, OpenCV processing
- **Evening (1 hour)**: Testing and debugging

#### Day 2: Rendering & Features
- **Morning (3 hours)**: Complete processing pipeline optimization
- **Afternoon (4 hours)**: OpenGL rendering implementation
- **Evening (2 hours)**: UI controls and performance monitoring

#### Day 3: Polish & Delivery
- **Morning (3 hours)**: Error handling, stability improvements
- **Afternoon (4 hours)**: Web viewer, documentation, final testing
- **Evening (1 hour)**: Final review and submission preparation

### Testing Strategy
- Test on physical Android device throughout development
- Verify performance at each checkpoint
- Test different lighting conditions for edge detection
- Validate memory usage and stability
- Cross-check all requirements against implementation

### Risk Mitigation
- Keep frame resolution moderate (640x480) for consistent performance
- Use single ABI (arm64-v8a) to reduce complexity
- Implement fallbacks for device-specific issues
- Maintain simple, readable code structure
- Regular commits to prevent work loss

## Success Criteria
- ✅ Camera feed displays in TextureView
- ✅ Frame processing works via JNI + OpenCV
- ✅ Processed frames render smoothly in OpenGL
- ✅ Achieves 10-15 FPS minimum performance
- ✅ Toggle between raw and processed views
- ✅ Web viewer displays sample processed frame
- ✅ Clean project structure and documentation
- ✅ Meaningful Git commit history
- ✅ All requirements met within 3-day timeframe