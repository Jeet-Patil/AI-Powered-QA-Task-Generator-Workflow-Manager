import { test, expect } from '@playwright/test';

test.describe('Task Management & AI QA Testing Lifecycle', () => {

  test('Complete QA Flow: Create Task -> Generate AI Tests -> Approve -> PASS', async ({ page }) => {
    let task: Record<string, unknown> | null = null;
    let testCase: Record<string, unknown> | null = null;

    await page.route('**/api/tasks', async (route) => {
      if (route.request().method() === 'POST') {
        const request = route.request().postDataJSON();
        task = { ...request, id: 42, status: 'TODO' };
        await route.fulfill({ status: 201, json: task });
        return;
      }
      await route.fulfill({ json: task ? [task] : [] });
    });
    await page.route('**/api/tasks/42', async (route) => {
      await route.fulfill({ json: task ?? { id: 42, title: 'Task not created' } });
    });
    await page.route('**/api/testcases/task/42', async (route) => {
      await route.fulfill({ json: testCase ? [testCase] : [] });
    });
    await page.route('**/api/testcases/generate', async (route) => {
      testCase = {
        id: 100,
        title: 'TC-001: Generated test case',
        description: 'Mock provider result',
        preconditions: 'Task exists',
        steps: 'Submit the task form',
        expectedResult: 'Task is created',
        testType: 'POSITIVE',
        status: 'DRAFT',
        executionResult: 'NOT_RUN'
      };
      await route.fulfill({ status: 201, json: [testCase] });
    });
    await page.route('**/api/testcases/100/review', async (route) => {
      testCase = { ...testCase, status: 'APPROVED' };
      await route.fulfill({ json: testCase });
    });
    await page.route('**/api/testcases/100/execute', async (route) => {
      const request = route.request().postDataJSON();
      testCase = { ...testCase, executionResult: request.result, executionNotes: request.executionNotes };
      await route.fulfill({ json: testCase });
    });

    // 1. Visit Tasks page
    await page.goto('/tasks');
    await expect(page.locator('h2')).toContainText('Tasks');

    // 2. Open Add Task Form
    await page.click('button:has-text("+ Add Task")');
    await page.waitForURL('**/tasks/new');

    // 3. Fill Form with valid business rule values
    const testTitle = `Auto QA Task ${Date.now()}`;
    await page.fill('input[formControlName="title"]', testTitle);
    await page.fill('textarea[formControlName="description"]', 'E2E automated flow verification');
    await page.selectOption('select[formControlName="priority"]', 'HIGH');

    // Due date (2 days in the future to satisfy Rule 5)
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 2);
    const dateStr = [futureDate.getFullYear(), String(futureDate.getMonth() + 1).padStart(2, '0'),
      String(futureDate.getDate()).padStart(2, '0')].join('-');
    await page.fill('input[formControlName="dueDate"]', dateStr);

    // 4. Save and return to list
    await page.click('button:has-text("Save")');
    await page.waitForURL('**/tasks');

    // 5. Locate newly created task and open details
    const row = page.locator(`tr:has-text("${testTitle}")`);
    await expect(row).toBeVisible();
    await row.locator('button:has-text("View")').click();
    await page.waitForURL(/.*\/tasks\/\d+/);

    // 6. Generate AI Test Cases (matches "Generate AI Test Cases", "Generate Test Cases", etc.)
    const genButton = page.getByRole('button', { name: /generate.*test/i });
    await expect(genButton).toBeVisible({ timeout: 10000 });
    await genButton.click();

    // 7. Verify test cases rendered (POSITIVE, NEGATIVE, BOUNDARY, etc.)
    const testCaseHeading = page.getByRole('heading', { name: 'TC-001: Generated test case' });
    await expect(testCaseHeading).toBeVisible();

    // 8. Tester Review: Approve the first test case
    const approveBtn = page.getByRole('button', { name: /approve/i }).first();
    await expect(approveBtn).toBeVisible();
    await approveBtn.click();

    // 9. Execute: Verify PASS button is unlocked and execute
    page.on('dialog', async dialog => {
      await dialog.accept('Automated execution confirmed working');
    });

    const passBtn = page.getByRole('button', { name: /pass/i }).first();
    await expect(passBtn).toBeVisible();
    await passBtn.click();

    // 10. Verify execution state updated
    await expect(page.getByText('PASS', { exact: true })).toBeVisible();
  });

});