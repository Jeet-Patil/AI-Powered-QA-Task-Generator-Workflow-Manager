package com.example.taskmanager.service;

import com.example.taskmanager.dto.ExecuteTestCaseRequest;
import com.example.taskmanager.entity.TestCase;
import com.example.taskmanager.entity.TestCaseStatus;

import java.util.List;

public interface TestCaseService {
    List<TestCase> getTestCasesByTaskId(Long taskId);
    List<TestCase> generateTestCases(Long taskId, String customPrompt, String provider);
    TestCase reviewTestCase(Long testCaseId, TestCaseStatus newStatus);
    TestCase executeTestCase(Long testCaseId, ExecuteTestCaseRequest request);
    void deleteTestCase(Long id);
}