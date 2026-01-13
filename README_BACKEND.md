# MoodCam Backend & Website Setup

This guide explains how to host the MoodCam backend and website on your Linux machine with Cloudflare Tunnel.

## 1. Local Setup

The project contains a `backend/` folder and a `website/` folder.

1. **Install Dependencies:**
   ```bash
   cd backend
   npm install
   ```

2. **Start the Server:**
   ```bash
   npm start
   ```
   The server runs on port 3000 by default. It serves the API at `/api` and the website at `/`.

## 2. Cloudflare Tunnel Setup (Public Access)

To make your local server accessible publicly (and for the app to connect), use Cloudflare Tunnel.

1. **Install `cloudflared`** on your Linux machine.
2. **Authenticate:** `cloudflared tunnel login`
3. **Create a Tunnel:** `cloudflared tunnel create moodcam`
4. **Configure DNS:** `cloudflared tunnel route dns moodcam moodcam.yourdomain.com` (Replace with your actual domain)
5. **Run the Tunnel:**
   ```bash
   cloudflared tunnel run --url http://localhost:3000 moodcam
   ```

Now `https://moodcam.yourdomain.com` will serve both the website and the API.

## 3. App Configuration

In `gallery/GalleryUploadManager.kt` and `MainActivity.kt`, update the `API_BASE` URL with your public domain:

```kotlin
private const val API_BASE = "https://moodcam.yourdomain.com/api"
```

## 4. Features

- **Public Gallery:** `/gallery` - Shows photos uploaded by users.
- **Profiles:** `/profiles` - Download presets. Mobile users are deep-linked to the app (`moodcam://profile/...`).
- **Upload API:** Users upload photos with a unique Device Key (no login required).
- **Username:** Users can set a custom username in the app or get a random one (e.g., "VintageLens42").
