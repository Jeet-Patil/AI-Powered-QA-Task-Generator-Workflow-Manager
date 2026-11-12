package com.example.taskmanager.dto;

import com.example.taskmanager.entity.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class CreateTaskRequest {
    @NotBlank(message = "Title is mandatory") // Rule 1
    private String title;

    private String description;

    @NotNull(message = "Priority is mandatory") // Rule 2
    private Priority priority;

    private String assignee;

    @FutureOrPresent(message = "Due date cannot be in the past") // Rule 5
    private LocalDate dueDate;

    public CreateTaskRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}