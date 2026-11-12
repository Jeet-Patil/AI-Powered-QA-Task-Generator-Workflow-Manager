package com.example.taskmanager.service;

import com.example.taskmanager.dto.ExecuteTestCaseRequest;
import com.example.taskmanager.entity.*;
import com.example.taskmanager.exception.BusinessRuleViolationException;
import com.example.taskmanager.exception.TaskNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.TestCaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class TestCaseServiceImpl implements TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TaskRepository taskRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    // Local LLM (Ollama) config
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:gemma:2b}")
    private String ollamaModel;

    @Value("${ollama.enabled:false}")
    private boolean ollamaEnabled;

    // Groq (OpenAI-compatible). Generous free tier, unlike Gemini's 20/day.
    @Value("${groq.api.key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    @Value("${groq.base-url:https://api.groq.com/openai}")
    private String groqBaseUrl;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    public TestCaseServiceImpl(TestCaseRepository testCaseRepository, TaskRepository taskRepository,
                               RestTemplate restTemplate) {
        this.testCaseRepository = testCaseRepository;
        this.taskRepository = taskRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<TestCase> getTestCasesByTaskId(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new TaskNotFoundException("Task not found with ID: " + taskId);
        }
        return testCaseRepository.findByTaskId(taskId);
    }

    @Override
    public List<TestCase> generateTestCases(Long taskId, String customPrompt, String provider) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with ID: " + taskId));

        String selected = provider == null ? "auto" : provider.trim().toLowerCase();
        if (!Set.of("auto", "ollama", "groq", "gemini").contains(selected)) {
            throw new IllegalArgumentException("Unsupported provider: " + selected);
        }
        System.out.println(">>> Starting test case generation | Provider: " + selected);

        // "auto" -> Ollama, then Groq, then Gemini. Explicit choice -> only that provider.
        boolean groqReady = groqApiKey != null && !groqApiKey.isBlank()
                && !groqApiKey.toLowerCase().contains("your_groq_api_key");
        boolean tryOllama = ollamaEnabled && (selected.equals("auto") || selected.equals("ollama"));
        boolean tryGroq = groqReady && (selected.equals("auto") || selected.equals("groq"));
        boolean tryGemini = selected.equals("auto") || selected.equals("gemini");

        if (selected.equals("ollama") && !ollamaEnabled) {
            throw new IllegalStateException("Ollama is disabled. Set OLLAMA_ENABLED=true to use it.");
        }

        if (tryOllama) {
            try {
                System.out.println(">>> Calling local Ollama model: " + ollamaModel);
                List<TestCase> localCases = callOllamaForTestCases(task, customPrompt);
                if (localCases != null && !localCases.isEmpty()) {
                    System.out.println(">>> Successfully generated " + localCases.size() + " test cases from local LLM!");
                    return testCaseRepository.saveAll(localCases);
                }
            } catch (Exception e) {
                System.err.println(">>> [ERROR] Ollama failed: " + e.getMessage());
                if (selected.equals("ollama")) {
                    throw new IllegalStateException("Local LLM (Ollama) generation failed: " + e.getMessage());
                }
            }
        }

        if (tryGroq) {
            try {
                System.out.println(">>> Calling Groq model: " + groqModel);
                List<TestCase> groqCases = callGroqForTestCases(task, customPrompt);
                if (groqCases != null && !groqCases.isEmpty()) {
                    System.out.println(">>> Successfully generated " + groqCases.size() + " test cases from Groq!");
                    return testCaseRepository.saveAll(groqCases);
                }
            } catch (Exception e) {
                System.err.println(">>> [ERROR] Groq failed: " + e.getMessage());
                if (selected.equals("groq")) {
                    throw new IllegalStateException("Groq generation failed: " + e.getMessage());
                }
            }
        } else if (selected.equals("groq")) {
            throw new IllegalStateException("No valid Groq API key configured. Set GROQ_API_KEY to use Groq.");
        }

        if (tryGemini && geminiApiKey != null && !geminiApiKey.isBlank()
                && !geminiApiKey.toLowerCase().contains("your_gemini_api_key")) {
            try {
                System.out.println(">>> Calling Gemini model: " + geminiModel);
                List<TestCase> aiCases = callGeminiForTestCases(task, customPrompt);
                if (aiCases != null && !aiCases.isEmpty()) {
                    System.out.println(">>> Successfully generated " + aiCases.size() + " test cases from Gemini LLM!");
                    return testCaseRepository.saveAll(aiCases);
                }
            } catch (Exception e) {
                System.err.println(">>> [ERROR] Gemini API failed with exception: " + e.getMessage());
                if (selected.equals("gemini")) {
                    throw new IllegalStateException("Gemini generation failed: " + e.getMessage());
                }
            }
        } else if (selected.equals("gemini")) {
            throw new IllegalStateException("No valid Gemini API key configured. Set GEMINI_API_KEY to use Gemini.");
        }

        // No template fallback: a silent canned response is worse than an error,
        // because it looks like the LLM worked. ponytail: fail loudly until fixed.
        throw new IllegalStateException(
                "No LLM produced test cases. Tried " + selected
                + ". Check the backend log for the provider error, or set a valid GEMINI_API_KEY.");
    }

// --- OpenAI-compatible providers (Ollama, Groq) ---

    // Ollama and Groq both speak the OpenAI chat-completions protocol, so they share
    // this one client. Groq differs only by base URL and a Bearer token.
    private List<TestCase> callOpenAiCompatible(String baseUrl, String model, String apiKey,
                                               String label, Task task, String customPrompt) throws Exception {
        String url = baseUrl + "/v1/chat/completions";

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", buildPrompt(task, customPrompt))),
                "temperature", 0.2);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        String responseBody = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                ResponseEntity<String> response =
                        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
                responseBody = response.getBody();
                System.out.println(">>> " + label + " HTTP " + response.getStatusCode() + " (attempt " + attempt + ")");
                break;
            } catch (org.springframework.web.client.HttpServerErrorException
                    | org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                if (attempt == 4) throw e;
                long backoff = 1000L * attempt * attempt;
                System.err.println(">>> " + label + " throttled, retry " + attempt + "/4 in " + backoff + "ms");
                Thread.sleep(backoff);
            }
        }

        JsonNode root = readProviderResponse(responseBody, label);
        String text = requireText(root.path("choices").path(0).path("message").path("content"), label);
        List<TestCase> cases = parseCases(task, text);
        if (cases.isEmpty()) {
            throw new IllegalStateException(label + " response contained no usable test cases.");
        }
        return cases;
    }

    private List<TestCase> callOllamaForTestCases(Task task, String customPrompt) throws Exception {
        return callOpenAiCompatible(ollamaBaseUrl, ollamaModel, null, "Ollama", task, customPrompt);
    }

    private List<TestCase> callGroqForTestCases(Task task, String customPrompt) throws Exception {
        return callOpenAiCompatible(groqBaseUrl, groqModel, groqApiKey, "Groq", task, customPrompt);
    }

    // --- Gemini ---

    private List<TestCase> callGeminiForTestCases(Task task, String customPrompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent";

        Map<String, Object> requestBody = Map.of("contents",
                List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(task, customPrompt))))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // ponytail: retry 503/429 only. Google's own guidance says these are transient
        // capacity errors; 404/400 are permanent config bugs and must fail fast.
        String responseBody = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
                responseBody = response.getBody();
                System.out.println(">>> Gemini HTTP " + response.getStatusCode() + " (attempt " + attempt + ")");
                break;
            } catch (org.springframework.web.client.HttpServerErrorException
                    | org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                if (attempt == 4) throw e;
                long backoff = 1000L * attempt * attempt;
                System.err.println(">>> Gemini throttled, retry " + attempt + "/4 in " + backoff + "ms");
                Thread.sleep(backoff);
            }
        }

        JsonNode root = readProviderResponse(responseBody, "Gemini");
        String textResponse = requireText(
                root.path("candidates").path(0).path("content").path("parts").path(0).path("text"),
                "Gemini");

        return parseCases(task, textResponse);
    }

    private JsonNode readProviderResponse(String responseBody, String provider) throws Exception {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException(provider + " returned an empty response.");
        }
        return objectMapper.readTree(responseBody);
    }

    private String requireText(JsonNode node, String provider) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalStateException(provider + " returned no generated content.");
        }
        return node.asText();
    }

    private String buildPrompt(Task task, String customPrompt) {
        return String.format(
            "QA engineer. Write 5 test cases for this feature: %s | %s | priority %s.%n" +
            "One per type: POSITIVE, NEGATIVE, VALIDATION, BOUNDARY, BUSINESS_RULE.%n" +
            "Be specific to the feature above, not generic.%n%s%n" +
            "%n" +
            "Reply with JSON only, no prose, no code fences:%n" +
            "{\"testCases\":[{\"title\":\"TC-001: ...\",\"type\":\"POSITIVE\"," +
            "\"preconditions\":\"...\",\"steps\":\"1. ...\",\"expectedResult\":\"...\"}]}",
            task.getTitle(),
            task.getDescription() != null ? task.getDescription() : "",
            task.getPriority(),
                customPrompt != null && !customPrompt.isBlank()
                    ? "Extra instruction: " + customPrompt.trim()
                    : ""
        );
    }

    private List<TestCase> parseCases(Task task, String textResponse) throws Exception {
        // Sanitize code blocks
        String text = textResponse.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();

        // Small models may add prose around JSON, emit adjacent objects, or ignore
        // the requested wrapper. Extract balanced JSON values wherever they appear.
        List<JsonNode> roots = extractJsonRoots(text);

        List<TestCase> list = new ArrayList<>();
        for (JsonNode root : roots) {
            JsonNode parsedCases = firstArray(root, "testCases", "test_cases", "testcases", "cases");
            if (parsedCases == null && looksLikeCase(root)) {
                parsedCases = objectMapper.createArrayNode().add(root);
            }
            if (parsedCases == null || !parsedCases.isArray()) {
                continue;
            }

            for (JsonNode node : parsedCases) {
                TestCase tc = new TestCase();
                tc.setTask(task);
                tc.setTitle(text(node, "title", "test_case_name", "test_case_title", "name"));
                tc.setDescription(text(node, "description", "expected_result", "expectedResult"));
                tc.setPreconditions(node.path("preconditions").asText("System is operational"));
                tc.setSteps(text(node, "steps", "test_steps", "procedure"));
                tc.setExpectedResult(text(node, "expectedResult", "expected_result"));

                if (tc.getTitle().isBlank() || tc.getSteps().isBlank() || tc.getExpectedResult().isBlank()) {
                    continue;
                }

                String typeStr = text(node, "type", "test_type", "testType").toUpperCase();
                if (typeStr.isBlank()) {
                    typeStr = "POSITIVE";
                }
                try {
                    tc.setTestType(TestType.valueOf(typeStr));
                } catch (IllegalArgumentException e) {
                    tc.setTestType(TestType.POSITIVE);
                }

                tc.setStatus(TestCaseStatus.DRAFT);
                tc.setExecutionResult(ExecutionResult.NOT_RUN);
                list.add(tc);
            }
        }
        return list;
    }

    private List<JsonNode> extractJsonRoots(String text) {
        List<JsonNode> roots = new ArrayList<>();
        for (int startIndex = 0; startIndex < text.length(); startIndex++) {
            char firstCharacter = text.charAt(startIndex);
            if (firstCharacter != '{' && firstCharacter != '[') {
                continue;
            }

            int endIndex = findJsonEnd(text, startIndex);
            if (endIndex < 0) {
                continue;
            }

            try {
                JsonNode root = objectMapper.readTree(text.substring(startIndex, endIndex + 1));
                if (root != null) {
                    roots.add(root);
                    startIndex = endIndex;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            }
        }
        return roots;
    }

    private int findJsonEnd(String text, int startIndex) {
        java.util.ArrayDeque<Character> expectedClosers = new java.util.ArrayDeque<>();
        boolean insideString = false;
        boolean escaped = false;

        for (int index = startIndex; index < text.length(); index++) {
            char currentCharacter = text.charAt(index);
            if (insideString) {
                if (escaped) {
                    escaped = false;
                } else if (currentCharacter == '\\') {
                    escaped = true;
                } else if (currentCharacter == '"') {
                    insideString = false;
                }
                continue;
            }

            if (currentCharacter == '"') {
                insideString = true;
            } else if (currentCharacter == '{') {
                expectedClosers.push('}');
            } else if (currentCharacter == '[') {
                expectedClosers.push(']');
            } else if (currentCharacter == '}' || currentCharacter == ']') {
                if (expectedClosers.isEmpty() || expectedClosers.pop() != currentCharacter) {
                    return -1;
                }
                if (expectedClosers.isEmpty()) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** First array field present under any of the given names, or null. */
    private JsonNode firstArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode n = root.path(name);
            if (n.isArray()) {
                return n;
            }
        }
        return null;
    }

    /** True when this object looks like a single test case rather than a wrapper. */
    private boolean looksLikeCase(JsonNode node) {
        return hasAny(node, "title", "test_case_name", "test_case_title", "name");
    }

    private boolean hasAny(JsonNode node, String... names) {
        for (String name : names) {
            if (!node.path(name).asText("").isBlank()) {
                return true;
            }
        }
        return false;
    }

    /** First non-blank text value among the given field names; "" when none present. */
    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode n = node.path(name);
            if (n.isObject() || n.isArray()) {
                return n.toString();
            }
            if (n.isTextual() || n.isNumber() || n.isBoolean()) {
                String s = n.asText();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return "";
    }

    private List<TestCase> generateFallbackTestCases(Task task) {
        List<TestCase> generatedCases = new ArrayList<>();

        TestCase tc1 = new TestCase();
        tc1.setTask(task);
        tc1.setTitle("TC-001: Create task '" + task.getTitle() + "' with valid details");
        tc1.setDescription("Verify successful task creation when all valid details are supplied for " + task.getTitle());
        tc1.setPreconditions("Open task creation page");
        tc1.setSteps("1. Enter valid title\n2. Select priority " + task.getPriority() + "\n3. Select assignee\n4. Select future due date\n5. Click Save");
        tc1.setExpectedResult("Task should be created successfully");
        tc1.setTestType(TestType.POSITIVE);
        tc1.setStatus(TestCaseStatus.DRAFT);
        tc1.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc1);

        TestCase tc2 = new TestCase();
        tc2.setTask(task);
        tc2.setTitle("TC-002: Create task without title");
        tc2.setDescription("Verify validation error when title is empty");
        tc2.setPreconditions("Open task creation page");
        tc2.setSteps("1. Leave title empty\n2. Fill remaining valid fields\n3. Click Save");
        tc2.setExpectedResult("Validation error: Title is mandatory");
        tc2.setTestType(TestType.VALIDATION);
        tc2.setStatus(TestCaseStatus.DRAFT);
        tc2.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc2);

        TestCase tc3 = new TestCase();
        tc3.setTask(task);
        tc3.setTitle("TC-003: Create task without priority");
        tc3.setDescription("Verify validation error when priority is omitted");
        tc3.setPreconditions("Open task creation page");
        tc3.setSteps("1. Enter valid title\n2. Do not select priority\n3. Click Save");
        tc3.setExpectedResult("Validation error: Priority is mandatory");
        tc3.setTestType(TestType.VALIDATION);
        tc3.setStatus(TestCaseStatus.DRAFT);
        tc3.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc3);

        TestCase tc4 = new TestCase();
        tc4.setTask(task);
        tc4.setTitle("TC-004: Create task without assignee");
        tc4.setDescription("Verify task is created successfully even when assignee is left unassigned");
        tc4.setPreconditions("Open task creation page");
        tc4.setSteps("1. Enter title and priority\n2. Leave assignee empty\n3. Click Save");
        tc4.setExpectedResult("Task is created successfully with unassigned state");
        tc4.setTestType(TestType.POSITIVE);
        tc4.setStatus(TestCaseStatus.DRAFT);
        tc4.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc4);

        TestCase tc5 = new TestCase();
        tc5.setTask(task);
        tc5.setTitle("TC-005: Create task with past due date");
        tc5.setDescription("Verify rejection when due date is set in the past");
        tc5.setPreconditions("Open task creation page");
        tc5.setSteps("1. Enter valid title and priority\n2. Select a past calendar date\n3. Click Save");
        tc5.setExpectedResult("Validation error: Due date cannot be in the past");
        tc5.setTestType(TestType.NEGATIVE);
        tc5.setStatus(TestCaseStatus.DRAFT);
        tc5.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc5);

        TestCase tc6 = new TestCase();
        tc6.setTask(task);
        tc6.setTitle("TC-006: Create task with today's due date");
        tc6.setDescription("Verify boundary validation when due date equals current date");
        tc6.setPreconditions("Open task creation page");
        tc6.setSteps("1. Fill valid title\n2. Set due date to today\n3. Click Save");
        tc6.setExpectedResult("Task should be accepted without errors");
        tc6.setTestType(TestType.BOUNDARY);
        tc6.setStatus(TestCaseStatus.DRAFT);
        tc6.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc6);

        TestCase tc7 = new TestCase();
        tc7.setTask(task);
        tc7.setTitle("TC-007: Create task with maximum title length");
        tc7.setDescription("Verify boundary constraint on title character capacity");
        tc7.setPreconditions("Open task creation page");
        tc7.setSteps("1. Enter 100-character string into title\n2. Click Save");
        tc7.setExpectedResult("Task is saved successfully without truncation");
        tc7.setTestType(TestType.BOUNDARY);
        tc7.setStatus(TestCaseStatus.DRAFT);
        tc7.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc7);

        TestCase tc8 = new TestCase();
        tc8.setTask(task);
        tc8.setTitle("TC-008: Verify invalid status transition TODO to COMPLETED");
        tc8.setDescription("Verify business rule blocking direct completion without IN_PROGRESS");
        tc8.setPreconditions("Task in TODO status exists");
        tc8.setSteps("1. Attempt direct status update to COMPLETED\n2. Check response");
        tc8.setExpectedResult("HTTP 400 Bad Request: Task can only be marked COMPLETED when IN_PROGRESS");
        tc8.setTestType(TestType.BUSINESS_RULE);
        tc8.setStatus(TestCaseStatus.DRAFT);
        tc8.setExecutionResult(ExecutionResult.NOT_RUN);
        generatedCases.add(tc8);

        return generatedCases;
    }

    @Override
    public TestCase reviewTestCase(Long testCaseId, TestCaseStatus newStatus) {
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new RuntimeException("Test Case not found with ID: " + testCaseId));
        tc.setStatus(newStatus);
        return testCaseRepository.save(tc);
    }

    @Override
    public TestCase executeTestCase(Long testCaseId, ExecuteTestCaseRequest request) {
        TestCase tc = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new RuntimeException("Test Case not found with ID: " + testCaseId));

        if (tc.getStatus() != TestCaseStatus.APPROVED) {
            throw new BusinessRuleViolationException(
                    "Only APPROVED test cases can be executed. Current status: " + tc.getStatus());
        }

        tc.setExecutionResult(request.getResult());
        tc.setActualResult(request.getActualResult());
        tc.setExecutionNotes(request.getExecutionNotes());
        tc.setExecutedAt(LocalDateTime.now());
        return testCaseRepository.save(tc);
    }

    @Override
    public void deleteTestCase(Long id) {
        if (!testCaseRepository.existsById(id)) {
            throw new RuntimeException("Test Case not found with ID: " + id);
        }
        testCaseRepository.deleteById(id);
    }
}