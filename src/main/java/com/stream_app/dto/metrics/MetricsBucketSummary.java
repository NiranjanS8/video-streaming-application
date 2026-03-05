package com.stream_app.dto.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsBucketSummary {
    private String bucket;
    private int samples;
    private double avgUploadThroughputMBps;
    private double avgProcessingLatencySec;
    private double avgRealtimeFactor;
}

