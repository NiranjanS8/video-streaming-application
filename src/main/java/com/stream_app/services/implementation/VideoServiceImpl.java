package com.stream_app.services.implementation;

import ch.qos.logback.core.util.StringUtil;
import com.stream_app.entities.Video;
import com.stream_app.repositories.VideoRepo;
import com.stream_app.services.VideoService;
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

    public void  init() {

        File dir = new File(DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("Directory created successfully.");
        }
        System.out.println("Folder Exists = " + DIR);
    }

    @Override
    public Video get(String videoId) {
        return null;
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
        return List.of();
    }
}
