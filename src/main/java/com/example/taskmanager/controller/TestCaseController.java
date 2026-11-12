package com.example.taskmanager.controller;

import com.example.taskmanager.dto.ExecuteTestCaseRequest;
import com.example.taskmanager.dto.GenerateTestCasesRequest;
import com.example.taskmanager.dto.TestCaseStatusUpdateRequest;
import com.example.taskmanager.entity.TestCase;
import com.example.taskmanager.service.TestCaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/testcases")
@CrossOrigin(originPatterns = "*")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @PostMapping("/generate")
    public ResponseEntity<List<TestCase>> generate(@Valid @RequestBody GenerateTestCasesRequest request) {
        return new ResponseEntity<>(testCaseService.generateTestCases(request.getTaskId(), request.getCustomPrompt(), request.getProvider()), HttpStatus.CREATED);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<TestCase>> getByTaskId(@PathVariable Long taskId) {
        return ResponseEntity.ok(testCaseService.getTestCasesByTaskId(taskId));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<TestCase> review(@PathVariable Long id, @Valid @RequestBody TestCaseStatusUpdateRequest request) {
        return ResponseEntity.ok(testCaseService.reviewTestCase(id, request.getStatus()));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<TestCase> execute(@PathVariable Long id, @Valid @RequestBody ExecuteTestCaseRequest request) {
        return ResponseEntity.ok(testCaseService.executeTestCase(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        testCaseService.deleteTestCase(id);
        return ResponseEntity.noContent().build();
    }
}