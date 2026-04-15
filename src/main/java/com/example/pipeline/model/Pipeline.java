package com.example.pipeline.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "pipelines")
public class Pipeline {
    @Id
    @Column(name = "pipeline_id")
    private String pipelineId;

    @Column(name = "pipeline_name")
    private String pipelineName;

    @Column(name = "tenant_id")
    private String tenantId;

    @Convert(converter = com.example.pipeline.converter.SourceConfigConverter.class)
    @Column(columnDefinition = "TEXT")
    private SourceConfig source;

    @Convert(converter = com.example.pipeline.converter.DestinationConfigConverter.class)
    @Column(columnDefinition = "TEXT")
    private DestinationConfig destination;

    @Convert(converter = com.example.pipeline.converter.TransformationConfigConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<TransformationConfig> transformations;

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
    public String getPipelineName() { return pipelineName; }
    public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public SourceConfig getSource() { return source; }
    public void setSource(SourceConfig source) { this.source = source; }
    public DestinationConfig getDestination() { return destination; }
    public void setDestination(DestinationConfig destination) { this.destination = destination; }
    public List<TransformationConfig> getTransformations() { return transformations; }
    public void setTransformations(List<TransformationConfig> transformations) { this.transformations = transformations; }
}
