# Spring Stream Backend

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

## Prerequisites

1. Java 21 installed
2. Maven installed (or use `mvnw.cmd`)
3. MySQL running locally
4. FFmpeg installed and available in PATH

Quick checks:

```powershell
java -version
mvn -version
mysql --version
ffmpeg -version
```

## Database Setup

Create the database:

```sql
CREATE DATABASE streaming_app;
```

Default DB config from `application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/streaming_app
spring.datasource.username=root
spring.datasource.password=admin@123
spring.jpa.hibernate.ddl-auto=update
```

Update username/password in `src/main/resources/application.properties` as needed.

## Run Locally

```powershell
# from project root
mvn spring-boot:run
```

App runs on:

- Backend + UI: `http://localhost:9090`

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

```mermaid
flowchart LR
    U[User Browser\nindex.html + app.js] -->|HTTP| VC[VideoController\n/api/v1/videos/*]

    VC -->|save metadata| DB[(MySQL\nvideos table)]
    VC -->|upload file| VDIR[(videos/)]
    VC -->|optional thumb upload| TDIR[(thumbnails/)]

    VC -->|trigger async job| VPS[VideoProcessingService\n@Async]
    VPS -->|read source| VDIR
    VPS -->|FFmpeg transcode| HLS[(videos_hsl/{videoId}/...)]
    VPS -->|auto thumbnail if missing| TDIR
    VPS -->|update thumbnailPath| DB

    U -->|list videos| VC
    VC -->|video + processing flag| U

    U -->|HLS playback| VC
    VC -->|master.m3u8 + segments| HLS

    U -->|fallback stream/range| VC
    VC -->|video bytes| VDIR
```

## Frontend Behavior

- Upload panel with drag/drop support
- Library cards show `Processing...` badge until HLS is available
- Library auto-refreshes while processing jobs are active
- Player attempts HLS first, then falls back to range streaming

## Configuration

`src/main/resources/application.properties`:

```properties
server.port=9090
files.video=videos/
files.video.hsl=videos_hsl/
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB
```

## Build

```powershell
mvn clean compile
```

## Troubleshooting

- FFmpeg not found:
  - Ensure `ffmpeg` command is available in PATH.
- Upload works but HLS never appears:
  - Check application logs for FFmpeg command/exit code.
  - Verify write access to `videos_hsl/`.
- Database connection failure:
  - Verify MySQL is running and credentials in `application.properties` are correct.
- Thumbnail 404:
  - Expected when custom thumbnail was not uploaded and auto-generation failed.

## Notes

- `description` is currently received as required request param in the controller.
- Uploaded files keep original filename in `videos/`; in production, prefer unique storage names.
- No authentication/authorization is enabled yet.

## License

No license file is currently defined in this repository.
