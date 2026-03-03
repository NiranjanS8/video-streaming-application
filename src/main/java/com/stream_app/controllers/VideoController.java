package com.stream_app.controllers;

import com.stream_app.AppConstants;
import com.stream_app.entities.Video;
import com.stream_app.playload.CustomMessage;
import com.stream_app.services.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/videos")
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    @Autowired
    private VideoService videoService;

    // Video upload endpoint
    @PostMapping
    public ResponseEntity<CustomMessage> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description) throws IOException {

        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setVideoId(UUID.randomUUID().toString());
        Video savedVideo = videoService.save(video, file);

        if (savedVideo != null) {
            return ResponseEntity.ok(new CustomMessage("Video uploaded successfully", true));
        } else {
            return ResponseEntity.status(500).body(new CustomMessage("Failed to upload video", false));
        }
    }

    // Get all videos endpoint
    @GetMapping("allVideos")
    public List<Video> getAllVideos() {
        return videoService.getAll();
    }

    // Streaming endpoint

    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String videoId) {
        Video video = videoService.get(videoId);

        String contentType = video.getContentType();
        String filePath = video.getFilePath();

        Resource resource = new FileSystemResource(filePath);

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    // Stream video in chunks endpoint
    @GetMapping("/stream/range/{videoId}")
    public ResponseEntity<Resource> streamVideoInChunks(
            @PathVariable String videoId,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        log.info(rangeHeader);
        // System.err.println("THIS IS ERROR STREAM");
        Video video = videoService.get(videoId);
        Path filePath = Paths.get(video.getFilePath());

        Resource resource = new FileSystemResource(filePath);
        String contentType = video.getContentType();

        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        long filLength = filePath.toFile().length();

        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        }

        long rangeStart;
        long rangeEnd;

        String[] ranges = rangeHeader.replace("bytes=", "").split("-");
        rangeStart = Long.parseLong(ranges[0]);

        rangeEnd = rangeStart + AppConstants.CHUNK_SIZE - 1; // Stream 1MB at a time

        if (rangeEnd >= filLength) {
            rangeEnd = filLength - 1;
        }

        // if (ranges.length > 1) {
        // rangeEnd = Long.parseLong(ranges[1]);
        // } else {
        // rangeEnd = filLength - 1;
        // }
        //
        // if (rangeEnd >= filLength) {
        // rangeEnd = filLength - 1;
        // }
        InputStream inputStream;
        HttpHeaders headers;
        try {
            inputStream = Files.newInputStream(filePath);
            inputStream.skip(rangeStart);

            log.info("Range Start: {}, Range End: {}", rangeStart, rangeEnd);
            long contentLength = rangeEnd - rangeStart + 1;

            byte[] data = new byte[(int) contentLength];
            int read = inputStream.read(data, 0, data.length);
            System.out.println("read(number of bytes read) = " + read);

            headers = new HttpHeaders();
            headers.add("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + filLength);
            headers.add("Cache-Control", "no-cache");
            headers.setContentLength(contentLength);

            return ResponseEntity.status(206)
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new ByteArrayResource(data));

        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }

    }

    // serve HLS playlist endpoint
    @GetMapping("/hls/{videoId}/master.m3u8")
    public ResponseEntity<Resource> getMasterPlaylist(@PathVariable String videoId) {

        Path path = Paths.get("videos_hsl", videoId, "master.m3u8");

        Resource resource = new FileSystemResource(path.toFile());

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .body(resource);
    }

    // Serve HLS variant playlists and segments from quality subdirectories
    // Handles: /hls/{videoId}/0/prog_index.m3u8, /hls/{videoId}/1/segment_000.ts,
    // etc.
    @GetMapping("/hls/{videoId}/{quality}/{fileName}")
    public ResponseEntity<Resource> serveHlsFile(
            @PathVariable String videoId,
            @PathVariable String quality,
            @PathVariable String fileName) {

        Path path = Paths.get("videos_hsl", videoId, quality, fileName);
        Resource resource = new FileSystemResource(path.toFile());

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType;
        if (fileName.endsWith(".m3u8")) {
            mediaType = MediaType.parseMediaType("application/vnd.apple.mpegurl");
        } else if (fileName.endsWith(".ts")) {
            mediaType = MediaType.parseMediaType("video/MP2T");
        } else {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }

    // Delete video endpoint
    @DeleteMapping("/{videoId}")
    public ResponseEntity<CustomMessage> deleteVideo(@PathVariable String videoId) {
        try {
            videoService.delete(videoId);
            return ResponseEntity.ok(new CustomMessage("Video deleted successfully", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new CustomMessage("Failed to delete video", false));
        }
    }

}
