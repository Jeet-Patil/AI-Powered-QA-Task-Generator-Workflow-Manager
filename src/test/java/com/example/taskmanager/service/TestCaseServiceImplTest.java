package com.example.taskmanager.service;

import com.example.taskmanager.dto.ExecuteTestCaseRequest;
import com.example.taskmanager.entity.*;
import com.example.taskmanager.exception.BusinessRuleViolationException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestCaseServiceImplTest {

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TestCaseServiceImpl testCaseService;

    private Task sampleTask;
    private TestCase sampleTestCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sampleTask = new Task();
        sampleTask.setId(1L);
        sampleTask.setTitle("Auth Feature");

        sampleTestCase = new TestCase();
        sampleTestCase.setId(10L);
        sampleTestCase.setTitle("Verify Login");
        sampleTestCase.setTask(sampleTask);
        sampleTestCase.setStatus(TestCaseStatus.DRAFT);
        sampleTestCase.setExecutionResult(ExecutionResult.NOT_RUN);
    }

    @Test
    @DisplayName("Generation fails loudly when no LLM is configured (no silent template fallback)")
    void generateTestCases_NoProviderConfigured_Throws() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testCaseService.generateTestCases(1L, "Generate all", "auto"));
        assertTrue(ex.getMessage().contains("No LLM produced test cases"));
        verify(testCaseRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Generation: use the selected Ollama provider")
    void generateTestCases_UsesOllama() throws Exception {
        ReflectionTestUtils.setField(testCaseService, "ollamaEnabled", true);
        ReflectionTestUtils.setField(testCaseService, "ollamaBaseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(testCaseService, "ollamaModel", "gemma:2b");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(testCaseRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.postForEntity(eq("http://localhost:11434/v1/chat/completions"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(openAiResponse()));

        List<TestCase> generated = testCaseService.generateTestCases(1L, null, "ollama");

        assertEquals(1, generated.size());
        assertEquals(TestType.POSITIVE, generated.get(0).getTestType());
    }

    @Test
    @DisplayName("Generation: use Groq and include the custom prompt")
    void generateTestCases_UsesGroqAndCustomPrompt() throws Exception {
        ReflectionTestUtils.setField(testCaseService, "groqApiKey", "test-key");
        ReflectionTestUtils.setField(testCaseService, "groqBaseUrl", "https://api.groq.com/openai");
        ReflectionTestUtils.setField(testCaseService, "groqModel", "test-model");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(testCaseRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.postForEntity(eq("https://api.groq.com/openai/v1/chat/completions"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(openAiResponse()));

        List<TestCase> generated = testCaseService.generateTestCases(1L, "Include an accessibility case", "groq");

        assertEquals(1, generated.size());
        org.mockito.ArgumentCaptor<HttpEntity> request = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.groq.com/openai/v1/chat/completions"),
                request.capture(), eq(String.class));
        Map<?, ?> body = (Map<?, ?>) request.getValue().getBody();
        List<?> messages = (List<?>) body.get("messages");
        assertTrue(messages.get(0).toString().contains("Include an accessibility case"));
    }

    @Test
    @DisplayName("Generation: use the selected Gemini provider")
    void generateTestCases_UsesGemini() throws Exception {
        ReflectionTestUtils.setField(testCaseService, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(testCaseService, "geminiModel", "test-model");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(testCaseRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.postForEntity(contains("/v1beta/models/test-model:generateContent"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(geminiResponse()));

        List<TestCase> generated = testCaseService.generateTestCases(1L, null, "gemini");

        assertEquals(1, generated.size());
        assertEquals("TC-001: Created", generated.get(0).getTitle());
        org.mockito.ArgumentCaptor<HttpEntity> request = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(contains("/v1beta/models/test-model:generateContent"),
            request.capture(), eq(String.class));
        assertEquals("test-key", request.getValue().getHeaders().getFirst("x-goog-api-key"));
    }

        @Test
        @DisplayName("Generation: reject malformed provider response without saving")
        void generateTestCases_RejectsMalformedResponse() {
        ReflectionTestUtils.setField(testCaseService, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(testCaseService, "geminiModel", "test-model");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(restTemplate.postForEntity(contains("/v1beta/models/test-model:generateContent"),
            any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{}"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> testCaseService.generateTestCases(1L, null, "gemini"));

        assertTrue(error.getMessage().contains("no generated content"));
        verify(testCaseRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Generation: reject unknown providers")
        void generateTestCases_RejectsUnknownProvider() {
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));

        assertThrows(IllegalArgumentException.class,
            () -> testCaseService.generateTestCases(1L, null, "unknown"));
        }

    @Test
    @DisplayName("Review: Transition status from DRAFT to APPROVED")
    void reviewTestCase_Approve() {
        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(sampleTestCase));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        TestCase result = testCaseService.reviewTestCase(10L, TestCaseStatus.APPROVED);
        assertEquals(TestCaseStatus.APPROVED, result.getStatus());
    }

    @Test
    @DisplayName("Governance Rule: Block execution if test case is not APPROVED")
    void executeTestCase_BlockedWhenNotApproved() {
        sampleTestCase.setStatus(TestCaseStatus.DRAFT);
        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(sampleTestCase));

        ExecuteTestCaseRequest req = new ExecuteTestCaseRequest();
        req.setResult(ExecutionResult.PASS);

        BusinessRuleViolationException ex = assertThrows(
            BusinessRuleViolationException.class,
                () -> testCaseService.executeTestCase(10L, req)
        );
        assertTrue(ex.getMessage().contains("Only APPROVED test cases can be executed"));
    }

    @Test
    @DisplayName("Execution: Record PASS result when test case is APPROVED")
    void executeTestCase_SuccessWhenApproved() {
        sampleTestCase.setStatus(TestCaseStatus.APPROVED);
        when(testCaseRepository.findById(10L)).thenReturn(Optional.of(sampleTestCase));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(i -> i.getArgument(0));

        ExecuteTestCaseRequest req = new ExecuteTestCaseRequest();
        req.setResult(ExecutionResult.PASS);
        req.setActualResult("Executed successfully");

        TestCase executed = testCaseService.executeTestCase(10L, req);
        assertEquals(ExecutionResult.PASS, executed.getExecutionResult());
        assertNotNull(executed.getExecutedAt());
    }

    @Test
    @DisplayName("Parsing: small models that emit standalone objects with renamed fields still parse")
    void generateTestCases_ParsesLooseSmallModelOutput() throws Exception {
        // Real gemma:2b shape: several standalone JSON objects, no testCases wrapper,
        // and its own field names instead of the requested ones.
        String loose = """
            Here are the requested test cases:
                {
                  "test_case_id": 1,
                  "test_case_name": "Apply SAVE20 coupon at checkout",
                  "steps": "Enter SAVE20 in the coupon field",
                  "expected_result": "Discount applied"
                }

                {
                  "test_case_id": 2,
                  "test_case_name": "Empty coupon code",
                  "steps": "Submit with the coupon field blank",
                                    "expected_result": {
                                        "assertion": "No discount is applied",
                                        "message": "Coupon field was empty"
                                    }
                                }

                                {
                                    "test_case_id": 3,
                                    "test_case_name": "Expired coupon code",
                                    "steps": "Submit an expired coupon code",
                                    "expected_result": {
                                        "assertion": "Coupon is rejected"
                                    }
                }
                                These cases cover the feature requirements.
                """;
        ReflectionTestUtils.setField(testCaseService, "ollamaEnabled", true);
        ReflectionTestUtils.setField(testCaseService, "ollamaBaseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(testCaseService, "ollamaModel", "gemma:2b");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(sampleTask));
        when(testCaseRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.postForEntity(eq("http://localhost:11434/v1/chat/completions"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(objectMapper.writeValueAsString(Map.of(
                        "choices", List.of(Map.of("message", Map.of("content", loose)))))));

        List<TestCase> generated = testCaseService.generateTestCases(1L, null, "ollama");

        assertEquals(3, generated.size(), "expected all standalone objects to be parsed");
        assertEquals("Apply SAVE20 coupon at checkout", generated.get(0).getTitle());
        assertEquals("Discount applied", generated.get(0).getExpectedResult());
        assertEquals("Empty coupon code", generated.get(1).getTitle());
        assertEquals("{\"assertion\":\"No discount is applied\",\"message\":\"Coupon field was empty\"}",
            generated.get(1).getExpectedResult());
        assertEquals("Expired coupon code", generated.get(2).getTitle());
    }

    private String openAiResponse() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", generatedCasesJson())))));
    }

    private String geminiResponse() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of("content", Map.of(
                        "parts", List.of(Map.of("text", generatedCasesJson())))))));
    }

    private String generatedCasesJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of("testCases", List.of(Map.of(
                "title", "TC-001: Created",
                "type", "POSITIVE",
                "preconditions", "System is ready",
                "steps", "Submit the form",
                "expectedResult", "The task is created"))));
    }
}