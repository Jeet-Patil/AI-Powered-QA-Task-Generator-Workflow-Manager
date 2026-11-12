package com.example.taskmanager.service;

import com.example.taskmanager.dto.CreateTaskRequest;
import com.example.taskmanager.dto.UpdateTaskRequest;
import com.example.taskmanager.entity.Priority;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.exception.BusinessRuleViolationException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private Task sampleTask;

    @BeforeEach
    void setUp() {
        sampleTask = new Task();
        sampleTask.setId(1L);
        sampleTask.setTitle("Initial Task");
        sampleTask.setDescription("Test Description");
        sampleTask.setPriority(Priority.HIGH);
        sampleTask.setStatus(TaskStatus.TODO);
        sampleTask.setDueDate(LocalDate.now().plusDays(5));
    }

    // --- RULE 1: Mandatory Fields ---
    @Test
    @DisplayName("Create Task: Success when mandatory fields (title, priority) are present")
    void createTask_Success() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Valid Task");
        req.setPriority(Priority.MEDIUM);
        req.setDueDate(LocalDate.now().plusDays(2));

        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setId(10L);
            return t;
        });

        Task created = taskService.createTask(req);
        assertNotNull(created);
        assertEquals(TaskStatus.TODO, created.getStatus());
        assertEquals("Valid Task", created.getTitle());
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    @DisplayName("Rule 1: Fail when title is blank")
    void createTask_Fail_BlankTitle() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("   ");
        req.setPriority(Priority.HIGH);

        assertThrows(BusinessRuleViolationException.class, () -> taskService.createTask(req));
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    @DisplayName("Rule 1: Fail when priority is missing")
    void createTask_Fail_NullPriority() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Task Missing Priority");
        req.setPriority(null);

        assertThrows(BusinessRuleViolationException.class, () -> taskService.createTask(req));
        verify(taskRepository, never()).save(any(Task.class));
    }

    // --- RULE 2: Due Date Cannot Be In The Past ---
    @Test
    @DisplayName("Rule 2: Fail when create task with past due date")
    void createTask_Fail_PastDueDate() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Past Task");
        req.setPriority(Priority.LOW);
        req.setDueDate(LocalDate.now().minusDays(1));

        assertThrows(BusinessRuleViolationException.class, () -> taskService.createTask(req));
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    @DisplayName("Rule 2: Allow task creation when due date is today or future")
    void createTask_Success_FutureDueDate() {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle("Future Task");
        req.setPriority(Priority.LOW);
        req.setDueDate(LocalDate.now());

        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask);
        assertDoesNotThrow(() -> taskService.createTask(req));
    }

    @Test
    @DisplayName("Rule 2: Fail when updating task with past due date")
    void updateTask_Fail_PastDueDate() {
        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Updated Title");
        req.setPriority(Priority.HIGH);
        req.setDueDate(LocalDate.now().minusDays(3));

        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        assertThrows(BusinessRuleViolationException.class, () -> taskService.updateTask(1L, req));
    }

    // --- RULE 3: TODO cannot transition directly to COMPLETED ---
    @Test
    @DisplayName("Rule 3: Fail when transitioning status directly from TODO to COMPLETED")
    void updateStatus_Fail_TodoToCompleted() {
        sampleTask.setStatus(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> taskService.updateStatus(1L, TaskStatus.COMPLETED)
        );
        assertTrue(ex.getMessage().contains("must be IN_PROGRESS"));
    }

    @Test
    @DisplayName("Rule 3: Success when transitioning status from IN_PROGRESS to COMPLETED")
    void updateStatus_Success_InProgressToCompleted() {
        sampleTask.setStatus(TaskStatus.IN_PROGRESS);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task result = taskService.updateStatus(1L, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, result.getStatus());
    }

    // --- RULE 4: COMPLETED task cannot transition to CANCELLED ---
    @Test
    @DisplayName("Rule 4: Fail when transitioning COMPLETED task to CANCELLED")
    void updateStatus_Fail_CompletedToCancelled() {
        sampleTask.setStatus(TaskStatus.COMPLETED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> taskService.updateStatus(1L, TaskStatus.CANCELLED)
        );
        assertTrue(ex.getMessage().contains("cannot be cancelled"));
    }

    @Test
    @DisplayName("Rule 4: Allow cancelling task if in TODO or IN_PROGRESS")
    void updateStatus_Success_CancelTodoTask() {
        sampleTask.setStatus(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Task result = taskService.updateStatus(1L, TaskStatus.CANCELLED);
        assertEquals(TaskStatus.CANCELLED, result.getStatus());
    }

    // --- RULE 6: COMPLETED tasks cannot be deleted ---
    @Test
    @DisplayName("Rule 6: Fail deletion when task is COMPLETED")
    void deleteTask_Fail_WhenCompleted() {
        sampleTask.setStatus(TaskStatus.COMPLETED);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        BusinessRuleViolationException ex = assertThrows(
                BusinessRuleViolationException.class,
                () -> taskService.deleteTask(1L)
        );
        assertTrue(ex.getMessage().contains("Completed tasks cannot be deleted"));
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    @DisplayName("Rule 6: Success deletion when task is TODO or IN_PROGRESS")
    void deleteTask_Success_WhenTodo() {
        sampleTask.setStatus(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        doNothing().when(taskRepository).delete(sampleTask);

        assertDoesNotThrow(() -> taskService.deleteTask(1L));
        verify(taskRepository, times(1)).delete(sampleTask);
    }

    // --- GENERAL SERVICE CHECKS ---
    @Test
    @DisplayName("Get Task By ID: Throws TaskNotFoundException when id does not exist")
    void getTaskById_NotFound() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(TaskNotFoundException.class, () -> taskService.getTaskById(999L));
    }

    @Test
    @DisplayName("Get All Tasks: Returns collection of tasks")
    void getAllTasks_Success() {
        when(taskRepository.findAll()).thenReturn(List.of(sampleTask));
        List<Task> list = taskService.getAllTasks();
        assertEquals(1, list.size());
        assertEquals("Initial Task", list.get(0).getTitle());
    }
}