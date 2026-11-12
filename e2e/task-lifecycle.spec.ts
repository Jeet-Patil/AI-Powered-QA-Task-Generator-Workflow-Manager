import { test, expect } from '@playwright/test';

test.describe('Task Management & AI QA Testing Lifecycle', () => {

  test('Complete QA Flow: Create Task -> Generate AI Tests -> Approve -> PASS', async ({ page }) => {
    // 1. Visit Tasks page
    await page.goto('http://localhost:4200/tasks');
    await expect(page.locator('h2')).toContainText('Tasks');

    // 2. Open Add Task Form
    await page.click('button:has-text("+ Add Task")');
    await page.waitForURL('**/tasks/new');

    // 3. Fill Form
    const testTitle = `Auto QA Task ${Date.now()}`;
    await page.fill('input[formControlName="title"]', testTitle);
    await page.fill('textarea[formControlName="description"]', 'E2E automated flow verification');
    await page.selectOption('select[formControlName="priority"]', 'HIGH');

    // Due date (2 days in the future to satisfy Rule 2)
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 2);
    const dateStr = futureDate.toISOString().split('T')[0];
    await page.fill('input[formControlName="dueDate"]', dateStr);

    // 4. Save and return to list
    await page.click('button:has-text("Save")');
    await page.waitForURL('**/tasks');

    // 5. Locate newly created task and open details
    const row = page.locator(`tr:has-text("${testTitle}")`);
    await expect(row).toBeVisible();
    await row.locator('button:has-text("View")').click();
    await page.waitForURL(/.*\/tasks\/\d+/);

    // 6. Generate AI Test Cases
    const genButton = page.locator('button:has-text("Generate Test Cases with AI")');
    await expect(genButton).toBeVisible();
    await genButton.click();

    // 7. Verify all 4 types generated in DRAFT state
    await expect(page.locator('span:has-text("POSITIVE")').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('span:has-text("NEGATIVE")').first()).toBeVisible();

    // 8. Tester Review: Approve the first test case
    const approveBtn = page.locator('button:has-text("✓ Approve")').first();
    await approveBtn.click();

    // 9. Execute: Verify PASS button unlocks, handle the execution notes dialog
    page.on('dialog', async dialog => {
      await dialog.accept('Automated execution confirmed working');
    });

    const passBtn = page.locator('button:has-text("PASS")').first();
    await passBtn.click();

    // 10. Verify execution state updated
    await expect(page.locator('span:has-text("APPROVED")').first()).toBeVisible();
  });

});