# hls video pipeline using springboot

Video upload and streaming platform built with Spring Boot.  
It supports:

- Video upload with metadata (`title`, `description`)
- Optional custom thumbnail upload
- Background FFmpeg processing to multi-bitrate HLS
- HLS + byte-range fallback streaming
- Processing-status aware frontend (`Processing...` badge + auto refresh)
- Video delete with cleanup of source, HLS files, and thumbnail

## Tech Stack

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- MySQL
- FFmpeg
- Vanilla HTML/CSS/JS frontend (served from `src/main/resources/static`)

## Project Structure

```text
src/main/java/com/stream_app
  controllers/VideoController.java
  entities/Video.java
  repositories/VideoRepo.java
  services/VideoService.java
  services/implementation/VideoServiceImpl.java
  services/implementation/VideoProcessingService.java

src/main/resources
  application.properties
  static/index.html
  static/app.js
  static/style.css
```

Runtime storage directories (created automatically if missing):

- `videos/` for original uploads
- `videos_hsl/` for HLS playlists + segments
- `thumbnails/` for generated or uploaded thumbnails

## API Endpoints

Base path: `/api/v1/videos`

### 1) Upload Video

`POST /api/v1/videos`

Form fields:

- `file` (required, video)
- `title` (required)
- `description` (required by controller contract; can be empty string)
- `thumbnail` (optional image)

Example:

```bash
curl -X POST "http://localhost:9090/api/v1/videos" \
  -F "file=@sample.mp4" \
  -F "title=Demo Video" \
  -F "description=Test upload" \
  -F "thumbnail=@thumb.jpg"
```

### 2) List All Videos

`GET /api/v1/videos/allVideos`

Returns each video record, including transient `processing` flag:

- `true` => HLS manifest not ready (`videos_hsl/{videoId}/master.m3u8` missing)
- `false` => HLS ready

### 3) Thumbnail by Video ID

`GET /api/v1/videos/thumbnail/{videoId}`

### 4) Direct Stream (full file)

`GET /api/v1/videos/stream/{videoId}`

### 5) Range Stream

`GET /api/v1/videos/stream/range/{videoId}`

Supports `Range` header for chunked playback.

### 6) HLS Master Playlist

`GET /api/v1/videos/hls/{videoId}/master.m3u8`

### 7) HLS Variants and Segments

`GET /api/v1/videos/hls/{videoId}/{quality}/{fileName}`

### 8) Delete Video

`DELETE /api/v1/videos/{videoId}`

Also removes source file, HLS directory, and thumbnail.

## Processing Flow

1. Video metadata + file are saved.
2. Async task starts (`@EnableAsync` + `@Async`).
3. FFmpeg creates 3 HLS renditions:
   - 360p (`800k`)
   - 720p (`2800k`)
   - 1080p (`5000k`)
4. If no custom thumbnail was uploaded, thumbnail is auto-generated.
5. Frontend polls library while any video is `processing`.

## Architecture Diagram

```text
[Browser UI: index.html + app.js]
          |
          | HTTP
          v
[VideoController: /api/v1/videos/*]
    |                 |                   |
    | save metadata   | save source file  | save optional thumbnail
    v                 v                   v
[MySQL videos]      [videos/]          [thumbnails/]
          |
          | trigger async
          v
[VideoProcessingService @Async]
    | read source from videos/
    | run FFmpeg transcode (360p/720p/1080p)
    v
[videos_hsl/{videoId}/master.m3u8 + segments]
    |
    | update processing flag via manifest existence
    v
[Frontend library shows Processing... until ready]

Playback path:
Browser -> VideoController -> HLS endpoints
Fallback:
Browser -> VideoController -> /stream/range/{videoId}
```

## Frontend Behavior

- Upload panel with drag/drop support
- Library cards show `Processing...` badge until HLS is available
- Library auto-refreshes while processing jobs are active
- Player attempts HLS first, then falls back to range streaming
