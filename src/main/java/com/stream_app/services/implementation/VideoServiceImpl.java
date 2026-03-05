package com.stream_app.services.implementation;

import com.stream_app.entities.AppUser;
import com.stream_app.entities.Video;
import com.stream_app.repositories.UserRepo;
import com.stream_app.repositories.VideoRepo;
import com.stream_app.services.VideoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    @Autowired
    private UserRepo userRepo;

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
    public Video save(Video video, MultipartFile file, MultipartFile thumbnail, Long userId) throws IOException {

        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        AppUser owner = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        Path uploadPath = Paths.get(DIR);
        Files.createDirectories(uploadPath); // extra safety

        Path filePath = uploadPath.resolve(fileName);
        long uploadStartMs = System.currentTimeMillis();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        long uploadEndMs = System.currentTimeMillis();

        video.setContentType(file.getContentType());
        video.setFilePath(filePath.toString());
        video.setUser(owner);
        video.setFileSizeBytes(file.getSize());
        video.setUploadStartedAtMs(uploadStartMs);
        video.setUploadCompletedAtMs(uploadEndMs);
        double uploadSec = Math.max((uploadEndMs - uploadStartMs) / 1000.0, 0.001);
        video.setUploadThroughputMBps((file.getSize() / 1024.0 / 1024.0) / uploadSec);
        video.setDurationSec(extractDurationSec(filePath));
        video.setProcessingSucceeded(false);

        // Save custom thumbnail if provided
        if (thumbnail != null && !thumbnail.isEmpty()) {
            Path thumbDir = Paths.get("thumbnails");
            Files.createDirectories(thumbDir);
            String thumbName = video.getVideoId() + ".jpg";
            Path thumbPath = thumbDir.resolve(thumbName);
            try (InputStream is = thumbnail.getInputStream()) {
                Files.copy(is, thumbPath, StandardCopyOption.REPLACE_EXISTING);
            }
            video.setThumbnailPath(thumbPath.toString());
        }

        // Save to database first
        Video savedVideo = videoRepo.save(video);

        // Process HLS + auto-thumbnail in the background (non-blocking)
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

    private Double extractDurationSec(Path filePath) {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                filePath.toString());
        try {
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.readLine();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 || output == null || output.isBlank()) {
                return null;
            }
            return Double.parseDouble(output.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public List<Video> getAllByUserId(Long userId) {
        return videoRepo.findByUserId(userId);
    }

    @Override
    public void processVideo(String videoId) {
        videoProcessingService.processVideoAsync(videoId);
    }

    @Override
    public void delete(String videoId, Long userId) {
        Video video = this.get(videoId);
        if (video.getUser() == null || !video.getUser().getId().equals(userId)) {
            throw new RuntimeException("You are not allowed to delete this video");
        }

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

        // Delete thumbnail if it exists
        try {
            if (video.getThumbnailPath() != null && !video.getThumbnailPath().isEmpty()) {
                Files.deleteIfExists(Paths.get(video.getThumbnailPath()));
            }
        } catch (IOException e) {
            System.err.println("Could not delete thumbnail: " + e.getMessage());
        }

        // Delete from database
        videoRepo.deleteById(videoId);
    }
}
