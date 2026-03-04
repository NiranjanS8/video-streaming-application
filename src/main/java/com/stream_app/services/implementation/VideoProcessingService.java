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
            if (exitCode == 0) {
                log.info("HLS processing completed successfully for video: {}", videoId);
            } else {
                log.error("HLS processing failed for video: {} with exit code {}", videoId, exitCode);
            }
        } catch (Exception e) {
            log.error("Error during HLS processing for video: {}", videoId, e);
        }
    }
}
