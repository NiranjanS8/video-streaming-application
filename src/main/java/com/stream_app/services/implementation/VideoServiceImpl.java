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
    private VideoRepo videoRepo;

    @Autowired
    private VideoProcessingService videoProcessingService;

    @Value("${files.video}")
    String DIR;

    @Value("${files.video.hsl}")
    String HLS_DIR;

    @PostConstruct
    public void init() {

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

        // Save to database first
        Video savedVideo = videoRepo.save(video);

        // Process HLS in the background (non-blocking, runs on separate thread)
        videoProcessingService.processVideoAsync(savedVideo.getVideoId());

        return savedVideo;
    }

    @Override
    public Video getByTitle(String title) {
        return null;
    }

    @Override
    public List<Video> getAll() {
        return videoRepo.findAll();
    }

    @Override
    public void processVideo(String videoId) {
        videoProcessingService.processVideoAsync(videoId);
    }

    @Override
    public void delete(String videoId) {
        Video video = this.get(videoId);

        // Delete original video file if it exists
        try {
            Path videoPath = Paths.get(video.getFilePath());
            Files.deleteIfExists(videoPath);
        } catch (IOException e) {
            System.err.println("Could not delete video file: " + e.getMessage());
        }

        // Delete HLS directory for this video
        try {
            Path hlsPath = Paths.get(HLS_DIR, videoId);
            if (Files.exists(hlsPath)) {
                Files.walk(hlsPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                /* ignore */ }
                        });
            }
        } catch (IOException e) {
            System.err.println("Could not delete HLS files: " + e.getMessage());
        }

        // Delete from database
        videoRepo.deleteById(videoId);
    }
}
