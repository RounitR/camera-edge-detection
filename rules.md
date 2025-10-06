# Project Rules & Guidelines

## Code Quality Standards

### 1. Assignment-Appropriate Code
- **Keep code minimal and purposeful** - Only implement what's required for the assessment
- **Avoid over-engineering** - Use simple, direct solutions rather than complex abstractions
- **No unnecessary libraries** - Stick to the required tech stack (Android SDK, NDK, OpenCV, OpenGL ES)
- **Clear variable names** - Use descriptive but concise naming (e.g., `frameProcessor`, `edgeTexture`)
- **Minimal comments** - Only comment complex logic or non-obvious decisions
- **No boilerplate code** - Remove unused imports, methods, or generated code that isn't needed

### 2. Natural Code Style
- **Human-like patterns** - Use common Android/Kotlin patterns and conventions
- **Realistic error handling** - Basic try-catch blocks, not exhaustive error scenarios
- **Standard formatting** - Follow Kotlin/Java conventions, use Android Studio's default formatting
- **Practical solutions** - Choose straightforward implementations over clever optimizations
- **Consistent naming** - Follow camelCase for variables, PascalCase for classes

## Commit Strategy

### 3. Natural Commit Messages
- **Keep messages short and descriptive** (under 50 characters for title)
- **Use present tense** ("Add camera preview" not "Added camera preview")
- **Be specific but not verbose** ("Fix texture upload bug" not "Implement comprehensive error handling for texture upload pipeline with fallback mechanisms")
- **Avoid technical jargon overload** - Use simple terms
- **Examples of good commits**:
  - "Add camera permission handling"
  - "Set up basic OpenGL renderer"
  - "Fix frame processing thread"
  - "Add toggle for edge detection"

### 4. Commit Frequency
- **Commit at each checkpoint** as defined in roadmap.md
- **Ask for commit message approval** before each commit
- **Small, focused commits** - Each commit should represent one logical change
- **Working state commits** - Only commit when code compiles and basic functionality works
- **No "WIP" or "temp" commits** - Each commit should be meaningful

## External Setup Instructions

### 5. Android Development Environment

#### Step 1: Install Android Studio
1. Go to https://developer.android.com/studio
2. Click "Download Android Studio"
3. Choose your operating system (Windows/Mac/Linux)
4. Download the installer (approximately 1GB)
5. Run the installer and follow the setup wizard
6. When prompted, choose "Standard" installation
7. Accept all license agreements
8. Wait for initial setup to complete (downloads SDK components)

#### Step 2: Configure Android SDK
1. Open Android Studio
2. Go to "Tools" → "SDK Manager"
3. In "SDK Platforms" tab:
   - Check "Android 13 (API 33)" or latest stable version
   - Check "Android 10 (API 29)" for broader compatibility
4. In "SDK Tools" tab, ensure these are installed:
   - Android SDK Build-Tools
   - NDK (Side by side) - version 25.0 or newer
   - CMake
   - Android SDK Platform-Tools
5. Click "Apply" and wait for downloads to complete

#### Step 3: Set Up Physical Device (Recommended)
1. Enable Developer Options on your Android phone:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times until "Developer mode enabled" appears
2. Enable USB Debugging:
   - Go to Settings → Developer Options
   - Turn on "USB Debugging"
3. Connect phone to computer via USB cable
4. When prompted on phone, allow USB debugging for this computer
5. In Android Studio, verify device appears in device dropdown

#### Alternative: Set Up Android Emulator
1. In Android Studio, go to "Tools" → "AVD Manager"
2. Click "Create Virtual Device"
3. Choose "Phone" category, select "Pixel 4" or similar
4. Choose system image (API 29 or 33) and download if needed
5. Click "Next" → "Finish"
6. Start the emulator (may take 2-3 minutes first time)

### 6. OpenCV Setup

#### Step 1: Download OpenCV Android SDK
1. Go to https://opencv.org/releases/
2. Find "Android" section under latest stable version (4.8.x)
3. Click "Android" to download opencv-android-sdk.zip (approximately 200MB)
4. Extract the zip file to a folder (e.g., `~/opencv-android-sdk`)

#### Step 2: Note the Path
- Remember the extraction path - you'll need it for project configuration
- The folder should contain: `sdk/native/libs/arm64-v8a/` with OpenCV libraries

### 7. Git Repository Setup

#### Step 1: Create GitHub Repository
1. Go to https://github.com
2. Sign in or create account if needed
3. Click green "New" button or "+" → "New repository"
4. Repository name: `camera-edge-detection` (or similar)
5. Description: "Real-time edge detection Android app with OpenCV and OpenGL"
6. Choose "Public" (required for assessment)
7. Do NOT check "Add a README file" (we'll create our own)
8. Click "Create repository"

#### Step 2: Clone Repository Locally
1. Copy the repository URL from GitHub
2. Open terminal/command prompt
3. Navigate to your development folder
4. Run: `git clone [repository-url]`
5. Navigate into the cloned folder: `cd camera-edge-detection`

### 8. TypeScript Setup

#### Step 1: Install Node.js
1. Go to https://nodejs.org
2. Download LTS version (recommended)
3. Run installer with default settings
4. Verify installation: open terminal and run `node --version`

#### Step 2: Verify TypeScript
1. In terminal, run: `npm install -g typescript`
2. Verify: `tsc --version`
3. Should show TypeScript version (4.x or 5.x)

## Project Structure Rules

### 9. File Organization
- **Only essential files** - No extra documentation, examples, or unused assets
- **Clean directory structure** - Follow the /app, /jni, /gl, /web structure exactly
- **No IDE-specific files** - Add .gitignore for Android Studio and system files
- **Single README.md** - Only one documentation file in project root

### 10. Repository Cleanliness
- **No build artifacts** - Never commit APK files, build folders, or generated code
- **No sensitive data** - No API keys, passwords, or personal information
- **No large binaries** - Keep repository under 50MB total
- **Proper .gitignore** - Exclude build/, .gradle/, .idea/, local.properties

## Development Guidelines

### 11. Testing Strategy
- **Test on real device** when possible for camera functionality
- **Keep it simple** - Basic functionality testing, no unit test frameworks
- **Manual testing** - Verify each checkpoint works before committing
- **Performance awareness** - Monitor FPS and memory usage during development

### 12. Debugging Approach
- **Use Android Studio debugger** for Java/Kotlin code
- **Use Log.d()** for simple debugging output
- **Native debugging** only if absolutely necessary
- **Keep debug code minimal** - Remove debug logs before final commits

### 13. Performance Considerations
- **Target 640x480 resolution** for consistent performance
- **Single ABI (arm64-v8a)** to reduce complexity
- **Reuse buffers** - Avoid per-frame allocations in native code
- **Simple algorithms** - Prefer performance over advanced features

## Final Submission Rules

### 14. Pre-Submission Checklist
- [ ] All code compiles without warnings
- [ ] App runs smoothly on test device
- [ ] All commits have meaningful messages
- [ ] README.md is complete with setup instructions
- [ ] No unnecessary files in repository
- [ ] Repository is public and accessible
- [ ] Web viewer works independently

### 15. What NOT to Include
- ❌ Multiple documentation files
- ❌ Complex build scripts or automation
- ❌ Advanced features beyond requirements
- ❌ Extensive error handling for edge cases
- ❌ Performance profiling or analytics code
- ❌ Multiple language support
- ❌ Advanced UI animations or styling
- ❌ Third-party libraries beyond required stack

### 16. Assessment Focus Areas
Remember the evaluation criteria:
- **25%** - Native C++ integration (JNI)
- **20%** - OpenCV usage (correct & efficient)
- **20%** - OpenGL rendering
- **20%** - TypeScript web viewer
- **15%** - Project structure, documentation, and commit history

Focus your effort proportionally on these areas, with extra attention to JNI integration since it carries the highest weight.