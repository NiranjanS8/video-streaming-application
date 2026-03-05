package com.stream_app.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    private String videoId;
    private String title;
    private String description;
    private String contentType;
    private String filePath;
    private String thumbnailPath;
    private Long fileSizeBytes;
    private Double durationSec;
    private Long uploadStartedAtMs;
    private Long uploadCompletedAtMs;
    private Double uploadThroughputMBps;
    private Long processingStartedAtMs;
    private Long processingCompletedAtMs;
    private Double processingLatencySec;
    private Double realtimeFactor;
    private Boolean processingSucceeded;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    @JsonIgnore
    private AppUser user;

    @Transient
    private boolean processing;

}
