package com.stream_app.controllers;

import com.stream_app.AppConstants;
import com.stream_app.dto.metrics.MetricsBucketSummary;
import com.stream_app.dto.metrics.MetricsSummaryResponse;
import com.stream_app.entities.Video;
import com.stream_app.playload.CustomMessage;
import com.stream_app.security.AuthenticatedUser;
import com.stream_app.services.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({ "/api/v1/videos", "/videos" })
public class VideoController {

    private static final Logger log = LoggerFactory.getLogger(VideoController.class);

    @Autowired
    private VideoService videoService;

    @Value("${files.video.hsl}")
    private String HLS_DIR;

    // Video upload endpoint (with optional custom thumbnail)
    @PostMapping
    public ResponseEntity<CustomMessage> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @AuthenticationPrincipal AuthenticatedUser user) throws IOException {

        Video video = new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setVideoId(UUID.randomUUID().toString());
        Video savedVideo = videoService.save(video, file, thumbnail, user.getId());

        if (savedVideo != null) {
            return ResponseEntity.ok(new CustomMessage("Video uploaded successfully", true));
        } else {
            return ResponseEntity.status(500).body(new CustomMessage("Failed to upload video", false));
        }
    }

    // Serve video thumbnail
    @GetMapping("/thumbnail/{videoId}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String videoId) {
        Video video = videoService.get(videoId);
        if (video.getThumbnailPath() == null || video.getThumbnailPath().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path thumbPath = Paths.get(video.getThumbnailPath());
        Resource resource = new FileSystemResource(thumbPath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    // Get all videos endpoint
    @GetMapping("allVideos")
    public List<Video> getAllVideos(@AuthenticationPrincipal AuthenticatedUser user) {
        return annotateProcessing(videoService.getAllByUserId(user.getId()));
    }

    // Get only videos owned by authenticated user
    @GetMapping("/my")
    public List<Video> getMyVideos(@AuthenticationPrincipal AuthenticatedUser user) {
        return annotateProcessing(videoService.getAllByUserId(user.getId()));
    }

    @GetMapping("/metrics/summary")
    public MetricsSummaryResponse getMetricsSummary(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Video> videos = videoService.getAllByUserId(user.getId());

        Map<String, StatsAccumulator> buckets = new LinkedHashMap<>();
        buckets.put("<1 min", new StatsAccumulator());
        buckets.put("1-5 min", new StatsAccumulator());
        buckets.put("5-15 min", new StatsAccumulator());
        buckets.put("15+ min", new StatsAccumulator());
        buckets.put("Unknown", new StatsAccumulator());

        for (Video video : videos) {
            String bucket = resolveBucket(video.getDurationSec());
            StatsAccumulator acc = buckets.get(bucket);
            acc.samples++;
            Double upload = getEffectiveUpload(video);
            Double latency = getEffectiveLatency(video);
            Double rtf = getEffectiveRtf(video, latency);

            if (upload != null) {
                acc.uploadSum += upload;
                acc.uploadCount++;
            }
            if (latency != null) {
                acc.latencySum += latency;
                acc.latencyCount++;
            }
            if (rtf != null) {
                acc.rtfSum += rtf;
                acc.rtfCount++;
            }
        }

        List<MetricsBucketSummary> summaries = new ArrayList<>();
        int measured = 0;
        for (Map.Entry<String, StatsAccumulator> entry : buckets.entrySet()) {
            StatsAccumulator b = entry.getValue();
            if (b.samples == 0) {
                summaries.add(MetricsBucketSummary.builder()
                        .bucket(entry.getKey())
                        .samples(0)
                        .avgUploadThroughputMBps(0.0)
                        .avgProcessingLatencySec(0.0)
                        .avgRealtimeFactor(0.0)
                        .build());
                continue;
            }

            int bucketMeasured = Math.max(b.uploadCount, Math.max(b.latencyCount, b.rtfCount));
            measured += bucketMeasured;
            summaries.add(MetricsBucketSummary.builder()
                    .bucket(entry.getKey())
                    .samples(b.samples)
                    .avgUploadThroughputMBps(round2(b.uploadCount == 0 ? 0.0 : b.uploadSum / b.uploadCount))
                    .avgProcessingLatencySec(round2(b.latencyCount == 0 ? 0.0 : b.latencySum / b.latencyCount))
                    .avgRealtimeFactor(round2(b.rtfCount == 0 ? 0.0 : b.rtfSum / b.rtfCount))
                    .build());
        }

        return MetricsSummaryResponse.builder()
                .totalVideos(videos.size())
                .measuredVideos(measured)
                .buckets(summaries)
                .build();
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
    public ResponseEntity<CustomMessage> deleteVideo(@PathVariable String videoId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            videoService.delete(videoId, user.getId());
            return ResponseEntity.ok(new CustomMessage("Video deleted successfully", true));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("not allowed")) {
                return ResponseEntity.status(403).body(new CustomMessage(e.getMessage(), false));
            }
            return ResponseEntity.status(500).body(new CustomMessage("Failed to delete video", false));
        }
    }

    private List<Video> annotateProcessing(List<Video> videos) {
        videos.forEach(video -> {
            Path masterPlaylist = Paths.get(HLS_DIR, video.getVideoId(), "master.m3u8");
            video.setProcessing(!Files.exists(masterPlaylist));
        });
        return videos;
    }

    private String resolveBucket(double durationSec) {
        if (Double.isNaN(durationSec) || Double.isInfinite(durationSec)) return "Unknown";
        if (durationSec < 60) return "<1 min";
        if (durationSec < 300) return "1-5 min";
        if (durationSec < 900) return "5-15 min";
        return "15+ min";
    }

    private String resolveBucket(Double durationSec) {
        if (durationSec == null) return "Unknown";
        return resolveBucket(durationSec.doubleValue());
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Double getEffectiveUpload(Video v) {
        if (v.getUploadThroughputMBps() != null) return v.getUploadThroughputMBps();
        if (v.getFileSizeBytes() == null || v.getUploadStartedAtMs() == null || v.getUploadCompletedAtMs() == null) return null;
        long ms = Math.max(v.getUploadCompletedAtMs() - v.getUploadStartedAtMs(), 1L);
        double sec = ms / 1000.0;
        return (v.getFileSizeBytes() / 1024.0 / 1024.0) / sec;
    }

    private Double getEffectiveLatency(Video v) {
        if (v.getProcessingLatencySec() != null) return v.getProcessingLatencySec();
        if (v.getProcessingStartedAtMs() == null || v.getProcessingCompletedAtMs() == null) return null;
        long ms = Math.max(v.getProcessingCompletedAtMs() - v.getProcessingStartedAtMs(), 1L);
        return ms / 1000.0;
    }

    private Double getEffectiveRtf(Video v, Double latencySec) {
        if (v.getRealtimeFactor() != null) return v.getRealtimeFactor();
        if (v.getDurationSec() == null || v.getDurationSec() <= 0 || latencySec == null) return null;
        return latencySec / v.getDurationSec();
    }

    private static class StatsAccumulator {
        int samples = 0;
        double uploadSum = 0.0;
        int uploadCount = 0;
        double latencySum = 0.0;
        int latencyCount = 0;
        double rtfSum = 0.0;
        int rtfCount = 0;
    }

}
