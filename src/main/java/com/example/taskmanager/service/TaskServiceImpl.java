package com.example.taskmanager.service;

import com.example.taskmanager.dto.CreateTaskRequest;
import com.example.taskmanager.dto.UpdateTaskRequest;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.exception.BusinessRuleViolationException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public Task createTask(CreateTaskRequest request) {
        // Rule 1: Title and Priority are mandatory
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Title is mandatory");
        }
        if (request.getPriority() == null) {
            throw new BusinessRuleViolationException("Priority is mandatory");
        }

        // Rule 2: Due date cannot be in the past
        if (request.getDueDate() != null && request.getDueDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleViolationException("Due date cannot be in the past");
        }

        Task task = new Task();
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setAssignee(request.getAssignee());
        task.setDueDate(request.getDueDate());
        task.setStatus(TaskStatus.TODO);

        return taskRepository.save(task);
    }

    @Override
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    @Override
    public Task getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task with id " + id + " not found"));
    }

    @Override
    public Task updateTask(Long id, UpdateTaskRequest request) {
        Task task = getTaskById(id);

        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BusinessRuleViolationException("Title is mandatory");
        }
        if (request.getPriority() == null) {
            throw new BusinessRuleViolationException("Priority is mandatory");
        }
        if (request.getDueDate() != null && request.getDueDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleViolationException("Due date cannot be in the past");
        }

        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        task.setAssignee(request.getAssignee());
        task.setDueDate(request.getDueDate());

        return taskRepository.save(task);
    }

    @Override
    public Task updateStatus(Long id, TaskStatus newStatus) {
        Task task = getTaskById(id);

        // Rule 3: Only IN_PROGRESS tasks can be marked as COMPLETED
        if (newStatus == TaskStatus.COMPLETED && task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException("Task must be IN_PROGRESS to be marked COMPLETED");
        }

        // Rule 4: Completed tasks cannot transition to CANCELLED
        if (task.getStatus() == TaskStatus.COMPLETED && newStatus == TaskStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Completed tasks cannot be cancelled");
        }

        task.setStatus(newStatus);
        return taskRepository.save(task);
    }

    @Override
    public void deleteTask(Long id) {
        Task task = getTaskById(id);

        // Rule 6: Completed tasks cannot be deleted
        if (task.getStatus() == TaskStatus.COMPLETED) {
            throw new BusinessRuleViolationException("Completed tasks cannot be deleted");
        }

        taskRepository.delete(task);
    }
}