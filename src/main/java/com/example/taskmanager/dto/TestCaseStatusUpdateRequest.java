package com.example.taskmanager.dto;

import com.example.taskmanager.entity.TestCaseStatus;
import jakarta.validation.constraints.NotNull;

public class TestCaseStatusUpdateRequest {
    @NotNull(message = "Status is required")
    private TestCaseStatus status;

    public TestCaseStatusUpdateRequest() {}
    public TestCaseStatus getStatus() { return status; }
    public void setStatus(TestCaseStatus status) { this.status = status; }
}