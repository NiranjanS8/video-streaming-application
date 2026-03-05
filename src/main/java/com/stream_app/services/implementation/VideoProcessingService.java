package com.stream_app.services.implementation;

import com.stream_app.entities.Video;
import com.stream_app.repositories.VideoRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class VideoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);

    @Autowired
    private VideoRepo videoRepo;

    @Value("${files.video.hsl}")
    private String HLS_DIR;

    @Async
    public void processVideoAsync(String videoId) {

        try {
            Video video = videoRepo.findById(videoId)
                    .orElseThrow(() -> new RuntimeException("Video not found: " + videoId));
            Path videoPath = Paths.get(video.getFilePath());
            long processingStart = System.currentTimeMillis();
            video.setProcessingStartedAtMs(processingStart);
            videoRepo.save(video);

            log.info("Starting HLS processing for video: {} ({})", videoId, videoPath);

            // Create base HLS directory
            Path baseDir = Paths.get(HLS_DIR, videoId);
            Files.createDirectories(baseDir);

            StringBuilder ffmpegCmd = new StringBuilder();

            ffmpegCmd.append("ffmpeg -i ")
                    .append("\"").append(videoPath.toString()).append("\" ")

                    // map video + audio 3 times
                    .append("-map 0:v:0 -map 0:a:0 ")
                    .append("-map 0:v:0 -map 0:a:0 ")
                    .append("-map 0:v:0 -map 0:a:0 ")

                    // set resolutions + bitrates
                    .append("-s:v:0 640x360 -b:v:0 800k ")
                    .append("-s:v:1 1280x720 -b:v:1 2800k ")
                    .append("-s:v:2 1920x1080 -b:v:2 5000k ")

                    // stream mapping
                    .append("-var_stream_map ")
                    .append("\"v:0,a:0 v:1,a:1 v:2,a:2\" ")

                    .append("-master_pl_name master.m3u8 ")

                    .append("-f hls ")
                    .append("-hls_time 10 ")
                    .append("-hls_list_size 0 ")
                    .append("-hls_flags independent_segments ")

                    .append("-hls_segment_filename ")
                    .append("\"")
                    .append(HLS_DIR).append("/").append(videoId)
                    .append("/%v/segment_%03d.ts\" ")

                    .append("\"")
                    .append(HLS_DIR).append("/").append(videoId)
                    .append("/%v/prog_index.m3u8\"");

            log.info("FFmpeg command: {}", ffmpegCmd);

            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c", ffmpegCmd.toString());
            pb.inheritIO();
            Process process = pb.start();

            int exitCode = process.waitFor();
            long processingEnd = System.currentTimeMillis();
            video = videoRepo.findById(videoId).orElse(null);
            if (video != null) {
                video.setProcessingCompletedAtMs(processingEnd);
                double processingSec = Math.max((processingEnd - processingStart) / 1000.0, 0.001);
                video.setProcessingLatencySec(processingSec);
                if (video.getDurationSec() != null && video.getDurationSec() > 0) {
                    video.setRealtimeFactor(processingSec / video.getDurationSec());
                }
                video.setProcessingSucceeded(exitCode == 0);
                videoRepo.save(video);
            }
            if (exitCode == 0) {
                log.info("HLS processing completed successfully for video: {}", videoId);
            } else {
                log.error("HLS processing failed for video: {} with exit code {}", videoId, exitCode);
            }

            // Auto-generate thumbnail if not already set (no custom thumbnail uploaded)
            video = videoRepo.findById(videoId).orElse(null);
            if (video != null && (video.getThumbnailPath() == null || video.getThumbnailPath().isEmpty())) {
                generateThumbnail(video, videoPath);
            }

        } catch (Exception e) {
            Video video = videoRepo.findById(videoId).orElse(null);
            if (video != null) {
                video.setProcessingSucceeded(false);
                videoRepo.save(video);
            }
            log.error("Error during HLS processing for video: {}", videoId, e);
        }
    }

    private void generateThumbnail(Video video, Path videoPath) {
        try {
            Path thumbDir = Paths.get("thumbnails");
            Files.createDirectories(thumbDir);
            String thumbName = video.getVideoId() + ".jpg";
            Path thumbPath = thumbDir.resolve(thumbName);

            // ffmpeg: capture a frame at 2 seconds, scale to 640x360
            String cmd = String.format(
                    "ffmpeg -i \"%s\" -ss 00:00:02 -vframes 1 -vf scale=640:360 -q:v 2 \"%s\" -y",
                    videoPath.toString(), thumbPath.toString());

            log.info("Generating thumbnail: {}", cmd);

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", cmd);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                video.setThumbnailPath(thumbPath.toString());
                videoRepo.save(video);
                log.info("Thumbnail generated for video: {}", video.getVideoId());
            } else {
                log.error("Thumbnail generation failed for video: {}", video.getVideoId());
            }
        } catch (Exception e) {
            log.error("Error generating thumbnail for video: {}", video.getVideoId(), e);
        }
    }
}
