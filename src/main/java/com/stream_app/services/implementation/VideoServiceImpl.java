package com.stream_app.services.implementation;

import com.stream_app.entities.Video;
import com.stream_app.repositories.VideoRepo;
import com.stream_app.services.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class VideoServiceImpl implements VideoService {

    @Autowired
    VideoRepo videoRepo;

    @Override
    public Video get(String videoId) {
        return null;
    }

    @Override
    public Video save(Video video, MultipartFile file) {
        return null;
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
