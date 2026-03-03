package com.stream_app.services.implementation;

import ch.qos.logback.core.util.StringUtil;
import com.stream_app.entities.Video;
import com.stream_app.repositories.VideoRepo;
import com.stream_app.services.VideoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class VideoServiceImpl implements VideoService {

    @Autowired
    VideoRepo videoRepo;

    @Value("${files.video}")
    String DIR;

    @Value("${files.video.hsl}")
    String HLS_DIR;

    @PostConstruct
    public void  init() {

        File dir = new File(DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Directory created successfully.");
        }
        try {
            Files.createDirectories(Paths.get(HLS_DIR));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Folder Exists = " + DIR);
        System.out.println("HLS Folder Exists = " + HLS_DIR);
    }

    @Override
    public Video get(String videoId) {
        Video video = videoRepo.findById(videoId).orElseThrow(() -> new RuntimeException("Video not found "));
        return video;
    }

    @Override
    public Video save(Video video, MultipartFile file) throws IOException {

        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        Path uploadPath = Paths.get(DIR);
        Files.createDirectories(uploadPath); // extra safety

        Path filePath = uploadPath.resolve(fileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        video.setContentType(file.getContentType());
        video.setFilePath(filePath.toString());

        String contentType = file.getContentType();
        System.out.println(contentType); System.out.println("path = " + uploadPath);
        return videoRepo.save(video);
    }

    @Override
    public Video getByTitle(String title) {
        return null;
    }

    @Override
    public List<Video> getAll() {
        return videoRepo.findAll();
    }


    // Placeholder for video processing method
    @Override
    public String processVideo(String videoId, MultipartFile file) throws IOException {

        Video video = this.get(videoId);
        Path videoPath = Paths.get(video.getFilePath());

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

                // FIXED stream mapping
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

        System.out.println(ffmpegCmd.toString());

        ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c", ffmpegCmd.toString()
        );
        pb.inheritIO(); // To see ffmpeg output in console
        Process process = pb.start();
        try {
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Video processed successfully");
            } else {
                System.err.println("Video processing failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "";
    }
}
