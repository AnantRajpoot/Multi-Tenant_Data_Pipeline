package com.example.pipeline.model;

import lombok.Data;
import jakarta.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "job_runs")
public class JobRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String pipelineId;

    private String status; // PENDING, RUNNING, SUCCESS, FAILED, CANCELLED

    @Temporal(TemporalType.TIMESTAMP)
    private Date startTime;

    @Temporal(TemporalType.TIMESTAMP)
    private Date endTime;

    private Long recordsRead = 0L;
    private Long recordsWritten = 0L;
    private Long recordsFiltered = 0L;
    private Long recordsFailed = 0L;

    @Column(length = 1000)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String errorLog;

    /**
     * Computes elapsed time for this run in milliseconds.
     */
    public Long getDurationMillis() {
        if (startTime != null && endTime != null) {
            return endTime.getTime() - startTime.getTime();
        }
        return 0L;
    }
}
