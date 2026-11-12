package com.example.taskmanager.dto;

import com.example.taskmanager.entity.ExecutionResult;
import jakarta.validation.constraints.NotNull;

public class ExecuteTestCaseRequest {
    @NotNull(message = "Execution result is required")
    private ExecutionResult result;
    private String actualResult;
    private String executionNotes;

    public ExecuteTestCaseRequest() {}
    public ExecutionResult getResult() { return result; }
    public void setResult(ExecutionResult result) { this.result = result; }
    public String getActualResult() { return actualResult; }
    public void setActualResult(String actualResult) { this.actualResult = actualResult; }
    public String getExecutionNotes() { return executionNotes; }
    public void setExecutionNotes(String executionNotes) { this.executionNotes = executionNotes; }
}