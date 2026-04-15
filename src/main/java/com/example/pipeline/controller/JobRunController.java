package com.example.pipeline.controller;

import com.example.pipeline.model.JobRun;
import com.example.pipeline.repository.JobRunRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/jobruns")
public class JobRunController {
    @Autowired
    private JobRunRepository jobRunRepository;

    /**
     * Returns execution history across all pipelines.
     */
    @GetMapping
    public List<JobRun> getAllJobRuns() {
        return jobRunRepository.findAll();
    }

    /**
     * Returns execution history for a single pipeline id.
     */
    @GetMapping("/pipeline/{pipelineId}")
    public List<JobRun> getJobRunsByPipeline(@PathVariable String pipelineId) {
        return jobRunRepository.findByPipelineId(pipelineId);
    }
}
