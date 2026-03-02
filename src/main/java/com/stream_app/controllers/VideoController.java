package com.stream_app.controllers;


import com.stream_app.entities.Video;
import com.stream_app.playload.CustomMessage;
import com.stream_app.services.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/videos")
public class VideoController {

    @Autowired
    private VideoService videoService;

//    Video upload endpoint
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

        if(savedVideo != null) {
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

        if(contentType == null){
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

}
