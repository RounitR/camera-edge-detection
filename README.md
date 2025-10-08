# Edge Detection Camera App and Web Viewer

A minimal Android application that captures camera frames, processes them using OpenCV in C++ via JNI, displays the processed output using OpenGL ES, and serves frames to a TypeScript web viewer via an embedded HTTP server. This project demonstrates integration across Android development, native C++ processing, OpenGL rendering, and web technologies.

## Features
- Real-time camera frame capture using Camera2 API
- Edge detection processing in native C++ via JNI (OpenCV)
- OpenGL ES renderer showing original and processed frames
- Embedded HTTP server (NanoHTTPD) on the device to serve:
  - `/status` (JSON)
  - `/frame.jpg` (latest processed frame as JPEG)
  - `/settings` (accepts JSON for thresholds and toggle)
- TypeScript web viewer to connect to the device, preview frames, and adjust settings

## Project Structure
- `app/` — Android application module
  - `src/main/java/com/edgedetection/MainActivity.kt` — Activity, camera pipeline, JNI calls, server lifecycle
  - `src/main/java/com/edgedetection/FrameServer.kt` — Embedded HTTP server (NanoHTTPD)
  - `src/main/java/com/edgedetection/EdgeRenderer.kt` — OpenGL ES renderer
  - `src/main/cpp/` — Native code (OpenCV) and JNI integration
- `web/` — Web viewer (TypeScript)
  - `index.html` — UI with device URL input, stream image, controls
  - `src/main.ts` — Connects to device server, polls `/status` and `/frame.jpg`, posts `/settings`

## Prerequisites
- Android SDK and a device (or emulator with camera support)
- `adb` installed and available in PATH
- Node.js (LTS recommended)

## Android App: Build, Install, Run
1. Build the debug APK:
   - `./gradlew :app:assembleDebug`
2. Install to a connected device:
   - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Run the app on the device and keep it in the foreground. The embedded server starts in `onResume` and listens on port `8081`.
4. Find the device IP address (IPv4) on Wi‑Fi (e.g. `192.168.x.x`).
   - Android: Settings → Network & Internet → Wi‑Fi → (your network) → Advanced → IP address
5. Test endpoints in a browser:
   - `http://<device-ip>:8081/status`
   - `http://<device-ip>:8081/frame.jpg` (will show frames when edge detection is enabled)
   - `http://<device-ip>:8081/settings` (POST only)

### Settings API
- Endpoint: `POST http://<device-ip>:8081/settings`
- Content-Type: `application/json`
- Body:
```
{
  "lowThreshold": <number>,
  "highThreshold": <number>,
  "edgesEnabled": <boolean>
}
```
- The app applies thresholds and toggles processed frame visibility upon receiving settings.

## Web Viewer: Build and Run
1. Install dependencies (first time):
   - `cd web && npm install`
2. Build TypeScript:
   - `npm run build`
3. Start local server:
   - `npm run start` (serves at `http://127.0.0.1:5173/`)
4. Connect to the device:
   - Open `http://127.0.0.1:5173/`
   - Enter `http://<device-ip>:8081` in "Device Server URL" and click **Connect**
   - Move either threshold slider or toggle edge detection once to send settings; frames will begin updating from `/frame.jpg` every second

### Notes
- Ensure the phone and computer are on the same Wi‑Fi/LAN
- Frames are published to `/frame.jpg` only when edge detection is enabled
- The server stops when the app is paused or backgrounded
- CORS headers are enabled on the device server for GET/POST/OPTIONS

## Screenshots
(Place your project screenshots here.)
- Screenshot 1: [add image]
- Screenshot 2: [add image]

## Video Demos
(Place your demo video links or embeds here.)
- Demo 1: [add link]
- Demo 2: [add link]

## Development
Potential enhancements:
- Stream optimization (e.g., MJPEG endpoint or WebSocket streaming)
- Error handling and resiliency improvements
- Performance overlay refinements

## License
Add your license information here if applicable.