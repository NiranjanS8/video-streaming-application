package com.stream_app.repositories;

import com.stream_app.entities.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepo extends JpaRepository<Video, String> {

    Optional<Video> findByTitle(String title);

    List<Video> findByUserId(Long userId);

}
