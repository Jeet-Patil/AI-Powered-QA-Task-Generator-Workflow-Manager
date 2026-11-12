package com.example.taskmanager.dto;

import jakarta.validation.constraints.NotNull;

public class GenerateTestCasesRequest {
    @NotNull(message = "Task ID is required")
    private Long taskId;
    private String customPrompt;
    private String provider;

    public GenerateTestCasesRequest() {}
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getCustomPrompt() { return customPrompt; }
    public void setCustomPrompt(String customPrompt) { this.customPrompt = customPrompt; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}