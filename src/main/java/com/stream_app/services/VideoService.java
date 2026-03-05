package com.stream_app.services;

import com.stream_app.entities.Video;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface VideoService {

     Video get(String videoId);

     Video save(Video video, MultipartFile file, MultipartFile thumbnail, Long userId) throws IOException;

     Video getByTitle(String title);

     List<Video> getAll();

     List<Video> getAllByUserId(Long userId);

     // video processing method (runs async in background)
     void processVideo(String videoId);

     // delete video
     void delete(String videoId, Long userId);
}
