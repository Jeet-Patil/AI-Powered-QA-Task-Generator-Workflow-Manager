package com.example.taskmanager.controller;

import com.example.taskmanager.entity.Priority;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.TaskStatus;
import com.example.taskmanager.exception.BusinessRuleViolationException;
import com.example.taskmanager.exception.GlobalExceptionHandler;
import com.example.taskmanager.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TaskControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private TaskController taskController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("API POST /api/tasks - 201 Created on valid task")
    void createTask_Returns201() throws Exception {
        Task created = new Task();
        created.setId(1L);
        created.setTitle("Integration Task");
        created.setPriority(Priority.HIGH);
        created.setStatus(TaskStatus.TODO);
        created.setDueDate(LocalDate.now().plusDays(5));

        when(taskService.createTask(any())).thenReturn(created);

        String jsonPayload = """
            {
              "title": "Integration Task",
              "priority": "HIGH",
              "dueDate": "%s"
            }
            """.formatted(LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Integration Task"));
    }

    @Test
    @DisplayName("API POST /api/tasks - 400 Bad Request on past due date")
    void createTask_Returns400_PastDueDate() throws Exception {
        String jsonPayload = """
            {
              "title": "Past Due Task",
              "priority": "LOW",
              "dueDate": "2020-01-01"
            }
            """;

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Due date cannot be in the past")));
    }

    @Test
    @DisplayName("API GET /api/tasks - 200 OK with list")
    void getAllTasks_Returns200() throws Exception {
        Task t = new Task();
        t.setId(10L);
        t.setTitle("Sample Task");
        t.setPriority(Priority.MEDIUM);
        t.setStatus(TaskStatus.TODO);

        when(taskService.getAllTasks()).thenReturn(List.of(t));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Sample Task"));
    }
}