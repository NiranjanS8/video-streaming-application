package com.stream_app.dto.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsSummaryResponse {
    private int totalVideos;
    private int measuredVideos;
    private List<MetricsBucketSummary> buckets;
}

