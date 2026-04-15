package com.example.pipeline.controller;

import com.example.pipeline.model.JobRun;
import com.example.pipeline.model.Pipeline;
import com.example.pipeline.repository.JobRunRepository;
import com.example.pipeline.repository.PipelineRepository;
import com.example.pipeline.service.IngestionService;
import com.example.pipeline.service.LoadingService;
import com.example.pipeline.service.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pipelines")
public class PipelineController {
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private PipelineRepository repository;
    @Autowired
    private JobRunRepository jobRunRepository;
    @Autowired
    private ValidationService validationService;
    @Autowired
    private LoadingService loadingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Creates and persists a pipeline definition after validation.
     */
    @PostMapping
    public ResponseEntity<Pipeline> createPipeline(@RequestBody Pipeline pipeline) {
        // Validate pipeline
        if (!validationService.validatePipeline(pipeline)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(repository.save(pipeline));
    }

    /**
     * Returns all configured pipelines.
     */
    @GetMapping
    public List<Pipeline> getAllPipelines() {
        return repository.findAll();
    }

    /**
     * Returns one pipeline by id, or 404 when not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pipeline> getPipeline(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates mutable fields of an existing pipeline and re-validates the result.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Pipeline> updatePipeline(@PathVariable String id, @RequestBody Pipeline pipeline) {
        return repository.findById(id).map(existingPipeline -> {
            // Update fields
            existingPipeline.setPipelineName(pipeline.getPipelineName());
            existingPipeline.setTenantId(pipeline.getTenantId());
            existingPipeline.setSource(pipeline.getSource());
            existingPipeline.setDestination(pipeline.getDestination());
            existingPipeline.setTransformations(pipeline.getTransformations());

            // Validate updated pipeline
            if (!validationService.validatePipeline(existingPipeline)) {
                return ResponseEntity.badRequest().<Pipeline>build();
            }

            return ResponseEntity.ok(repository.save(existingPipeline));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deletes a pipeline definition by id.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePipeline(@PathVariable String id) {
        return repository.findById(id).map(pipeline -> {
            repository.delete(pipeline);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Runs a pipeline end-to-end and records lifecycle and counters in JobRun.
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<JobRun> executePipeline(@PathVariable String id) {
        return repository.findById(id).map(pipeline -> {
            JobRun jobRun = new JobRun();
            jobRun.setPipelineId(pipeline.getPipelineId());
            jobRun.setStatus("PENDING");
            jobRun.setStartTime(new Date());
            jobRun = jobRunRepository.save(jobRun);

            try {
                // Move job from queued to active execution state.
                jobRun.setStatus("RUNNING");
                jobRunRepository.save(jobRun);

                // Run ingestion + transformations + destination loading.
                IngestionService.IngestionMetrics metrics = ingestionService.startIngestion(pipeline);

                // Persist any rows left in memory buffers.
                loadingService.flushBatch();

                // Update job run with results
                jobRun.setEndTime(new Date());
                jobRun.setStatus(metrics.success ? "SUCCESS" : "FAILED");
                jobRun.setRecordsRead(metrics.recordsRead);
                jobRun.setRecordsWritten(metrics.recordsWritten);
                jobRun.setRecordsFiltered(metrics.recordsFiltered);
                jobRun.setRecordsFailed(metrics.recordsFailed);

                if (!metrics.errorLogs.isEmpty()) {
                    jobRun.setMessage("Completed with " + metrics.errorLogs.size() + " errors");
                    jobRun.setErrorLog(String.join("\n", metrics.errorLogs));
                } else {
                    jobRun.setMessage("Pipeline executed successfully");
                }

                jobRunRepository.save(jobRun);
                return ResponseEntity.ok(jobRun);

            } catch (Exception e) {
                jobRun.setEndTime(new Date());
                jobRun.setStatus("FAILED");
                jobRun.setMessage("Execution failed: " + e.getMessage());
                jobRunRepository.save(jobRun);
                return ResponseEntity.internalServerError().body(jobRun);
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Convenience endpoint to inspect loaded customer rows.
     */
    @GetMapping("/results")
    public List<Map<String, Object>> getResults() {
        return jdbcTemplate.queryForList("SELECT * FROM processed_customers");
    }
}
