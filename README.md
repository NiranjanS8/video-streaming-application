# HLS Video Pipeline Using Spring Boot

Video upload and streaming platform with authentication, user-based ownership, async HLS processing, and performance metrics.

## Features

- JWT authentication (`/auth/register`, `/auth/login`)
- User-owned videos (`user_id` relationship in `videos` table)
- Protected upload/list/delete endpoints
- Public HLS and stream playback endpoints
- Async FFmpeg processing to HLS (360p/720p/1080p)
- Optional custom thumbnail + auto thumbnail generation
- Processing badge (`Processing...`) with auto-refresh
- Metrics tracking:
  - upload throughput (MB/s)
  - processing latency (seconds)
  - realtime factor (RTF)
- Metrics summary API + dashboard card in UI

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Security + JWT
- Spring Web MVC
- Spring Data JPA
- MySQL
- FFmpeg + ffprobe
- Vanilla HTML/CSS/JS frontend

## Project Structure

```text
src/main/java/com/stream_app
  config/SecurityConfig.java
  controllers/AuthController.java
  controllers/VideoController.java
  dto/auth/*
  dto/metrics/*
  entities/AppUser.java
  entities/Video.java
  repositories/UserRepo.java
  repositories/VideoRepo.java
  security/*
  services/*
  services/implementation/*

src/main/resources
  application.properties
  static/index.html
  static/login.html
  static/app.js
  static/login.js
  static/style.css
  static/login.css
```

## Runtime Storage

- `videos/` original uploaded files
- `videos_hsl/` HLS playlists + segments
- `thumbnails/` uploaded/generated thumbnails

## Authentication

### Register

`POST /auth/register`

```json
{
  "username": "demo",
  "password": "secret123"
}
```

### Login

`POST /auth/login`

```json
{
  "username": "demo",
  "password": "secret123"
}
```

Response includes JWT token:

```json
{
  "token": "...",
  "userId": 1,
  "username": "demo"
}
```

Use for protected routes:

`Authorization: Bearer <token>`

## Video API

Base paths supported: `/api/v1/videos` and `/videos`

### Protected

- `POST /api/v1/videos`
- `GET /api/v1/videos/my`
- `GET /api/v1/videos/allVideos`
- `DELETE /api/v1/videos/{videoId}`
- `GET /api/v1/videos/metrics/summary`

### Public

- `GET /api/v1/videos/thumbnail/{videoId}`
- `GET /api/v1/videos/stream/{videoId}`
- `GET /api/v1/videos/stream/range/{videoId}`
- `GET /api/v1/videos/hls/{videoId}/master.m3u8`
- `GET /api/v1/videos/hls/{videoId}/{quality}/{fileName}`

## Ownership Model

- `users` table stores application users.
- `videos.user_id` links each video to its owner.
- `/my` and delete operations enforce owner-only access.

## Metrics

Each video stores:

- `fileSizeBytes`
- `durationSec`
- `uploadStartedAtMs`, `uploadCompletedAtMs`, `uploadThroughputMBps`
- `processingStartedAtMs`, `processingCompletedAtMs`, `processingLatencySec`
- `realtimeFactor`
- `processingSucceeded`

Summary endpoint:

- `GET /api/v1/videos/metrics/summary`
- Buckets: `<1 min`, `1-5 min`, `5-15 min`, `15+ min`, `Unknown`

UI:

- Library section includes a **Performance Metrics** card with bucket-wise averages.

## Frontend Flow

- `login.html` handles login/register tabs, validation, loading states, and password visibility toggle icons.
- JWT is stored in `sessionStorage` (tab-scoped).
- `index.html` requires auth; unauthorized calls redirect to login.
- Upload/delete/list/metrics use authenticated API requests.

## Processing Flow

1. User uploads video (authenticated).
2. Video metadata saved with owner.
3. Async processing starts (`@Async`):
   - FFmpeg generates HLS outputs.
   - Thumbnail generated if missing.
4. UI shows `Processing...` until `master.m3u8` exists.
5. Metrics are updated and exposed in summary API.

## Architecture Diagram

```text
[login.html / index.html]
         |
         | Bearer JWT
         v
[Spring Security + JWT Filter]
         |
         v
[AuthController] ---> [users table]
         |
         v
[VideoController] ---> [videos table (user_id + metrics)]
     |           \
     |            \---> [videos/]
     |            \---> [thumbnails/]
     |            \---> [videos_hsl/]
     |
     v
[VideoProcessingService @Async + FFmpeg/ffprobe]
```

