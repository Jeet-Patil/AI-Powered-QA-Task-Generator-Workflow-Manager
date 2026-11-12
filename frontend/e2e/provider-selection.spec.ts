import { test, expect } from '@playwright/test';

test('provider selection generates, approves, and executes test cases', async ({ page }) => {
  const task = {
    id: 42,
    title: 'Provider selection test task',
    description: 'Verify provider selection in the QA flow',
    priority: 'HIGH',
    status: 'TODO',
    assignee: 'QA',
    dueDate: '2026-10-03'
  };
  const generatedCases: Array<Record<string, unknown>> = [];
  const requestedProviders: string[] = [];

  await page.route('**/api/tasks/42', (route) => route.fulfill({ json: task }));
  await page.route('**/api/testcases/task/42', (route) => route.fulfill({ json: [] }));
  await page.route('**/api/testcases/generate', async (route) => {
    const request = route.request().postDataJSON();
    requestedProviders.push(request.provider);
    const generatedCase = {
      id: generatedCases.length + 1,
      title: `TC-00${generatedCases.length + 1}: Provider response`,
      description: 'Generated response',
      preconditions: 'Task exists',
      steps: 'Submit the task',
      expectedResult: 'The task is saved',
      testType: 'POSITIVE',
      status: 'DRAFT',
      executionResult: 'NOT_RUN'
    };
    generatedCases.push(generatedCase);
    await route.fulfill({ status: 201, json: [generatedCase] });
  });
  await page.route('**/api/testcases/1/review', async (route) => {
    await route.fulfill({ json: { ...generatedCases[0], status: 'APPROVED' } });
  });
  await page.route('**/api/testcases/1/execute', async (route) => {
    const request = route.request().postDataJSON();
    await route.fulfill({
      json: {
        ...generatedCases[0],
        status: 'APPROVED',
        executionResult: request.result,
        actualResult: request.actualResult,
        executionNotes: request.executionNotes
      }
    });
  });

  await page.goto('/tasks/42');
  const providerSelect = page.getByRole('combobox', { name: 'Which LLM generates the test cases' });
  const generateButton = page.getByRole('button', { name: /generate.*test/i });

  for (const provider of ['gemini', 'ollama', 'groq']) {
    await providerSelect.selectOption(provider);
    await generateButton.click();
    await expect(page.getByText(`TC-00${requestedProviders.length}: Provider response`)).toBeVisible();
  }

  expect(requestedProviders).toEqual(['gemini', 'ollama', 'groq']);
  await expect(page.getByText('3 Total')).toBeVisible();

  await page.getByRole('button', { name: 'Approve' }).first().click();
  await expect(page.getByText('APPROVED', { exact: true }).first()).toBeVisible();

  page.on('dialog', (dialog) => dialog.accept('Execution verified'));
  await page.getByRole('button', { name: 'Pass', exact: true }).first().click();
  await expect(page.getByText('PASS', { exact: true })).toBeVisible();
});