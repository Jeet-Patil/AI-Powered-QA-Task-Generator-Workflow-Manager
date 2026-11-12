package com.example.taskmanager.service;

import com.example.taskmanager.dto.CreateTaskRequest;
import com.example.taskmanager.dto.UpdateTaskRequest;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskStatus;
import java.util.List;

public interface TaskService {
    Task createTask(CreateTaskRequest request);
    List<Task> getAllTasks();
    Task getTaskById(Long id);
    Task updateTask(Long id, UpdateTaskRequest request);
    void deleteTask(Long id);
    Task updateStatus(Long id, TaskStatus newStatus);
}