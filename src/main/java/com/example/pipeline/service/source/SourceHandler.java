package com.example.pipeline.service.source;

import com.example.pipeline.model.Pipeline;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstraction for a pipeline source that can fetch rows and provide them to the pipeline processing logic.
 */
public interface SourceHandler {
    /**
     * Type string this handler supports, e.g. "api" or "database".
     */
    String getType();

    /**
     * Fetch rows from the configured source and call rowConsumer.accept(rowMap) for each row.
     *
     * @param pipeline the pipeline definition (contains source config)
     * @param rowConsumer a consumer that processes each row (will ultimately call processRow)
     * @throws Exception on unrecoverable errors
     */
    void fetchRowsAndProcess(Pipeline pipeline, Consumer<Map<String,Object>> rowConsumer) throws Exception;
}
