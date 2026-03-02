package com.stream_app.services;

import com.stream_app.entities.Video;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


public interface VideoService {

     Video get(String videoId);

     Video save(Video video, MultipartFile file);

     Video getByTitle(String title);


     List<Video> getAll();
}
