/**
 * Post CRUD E2E — full create → list → edit → delete cycle.
 *
 * These tests touch the live backend via /api/* proxy. They expect the
 * backend to be reachable and the DB to be writable. Tests run serially
 * (workers: 1) so they don't conflict on the same data.
 */

import { test, expect } from '@playwright/test';

const TEST_TITLE = `E2E ${Date.now()}`;
const TEST_TITLE_EDIT = `${TEST_TITLE} (edited)`;

test.describe.serial('帖子 CRUD 流程', () => {
  test('创建新帖子', async ({ page }) => {
    await page.goto('/');

    // Wait for initial load
    await expect(page.locator('.create-btn')).toBeVisible();

    // Open create form
    await page.click('.create-btn');
    await expect(page.locator('.form-modal')).toBeVisible();

    // Fill form
    await page.fill('[data-testid="form-author"]', 'E2E Tester');
    await page.fill('[data-testid="form-title"]', TEST_TITLE);
    await page.fill('[data-testid="form-content"]', 'This is an E2E test post body.');

    // Submit
    await page.locator('.form-modal button[type="submit"]').click();

    // Form should close (proves submit succeeded)
    await expect(page.locator('.form-modal')).toHaveCount(0, { timeout: 5_000 });

    // New posts default to isPublished=false (drafts), so they're not in the
    // default listPublished view. Switch to "显示全部" to see them.
    await page.click('.checkbox-label input[type="checkbox"]');
    await expect(page.locator('.post-card').filter({ hasText: TEST_TITLE })).toBeVisible({
      timeout: 10_000,
    });
  });

  test('列表显示刚创建的帖子', async ({ page }) => {
    await page.goto('/');
    // listPublished by default — newly created posts are draft, so might not show
    // Switch to "显示全部" to see all
    await page.click('.checkbox-label input[type="checkbox"]');
    await expect(page.locator('.post-card').filter({ hasText: TEST_TITLE })).toBeVisible({
      timeout: 10_000,
    });
  });

  test('编辑帖子', async ({ page }) => {
    await page.goto('/');
    await page.click('.checkbox-label input[type="checkbox"]'); // showAll
    await expect(page.locator('.post-card').filter({ hasText: TEST_TITLE })).toBeVisible();

    // Click edit on the matching post card
    const card = page.locator('.post-card').filter({ hasText: TEST_TITLE });
    await card.locator('button:has-text("编辑")').click();
    await expect(page.locator('.form-modal')).toBeVisible();

    // Modify title
    await page.fill('[data-testid="form-title"]', TEST_TITLE_EDIT);

    // Submit
    await page.locator('.form-modal button[type="submit"]').click();
    await expect(page.locator('.form-modal')).toHaveCount(0);

    // Edited title should now appear
    await expect(page.locator('.post-card').filter({ hasText: TEST_TITLE_EDIT })).toBeVisible({
      timeout: 10_000,
    });
  });

  test('删除帖子', async ({ page }) => {
    await page.goto('/');
    await page.click('.checkbox-label input[type="checkbox"]'); // showAll
    await expect(page.locator('.post-card').filter({ hasText: TEST_TITLE_EDIT })).toBeVisible();

    // Auto-confirm the JS confirm dialog
    page.on('dialog', (dialog) => dialog.accept());

    const card = page.locator('.post-card').filter({ hasText: TEST_TITLE_EDIT });
    await card.locator('button:has-text("删除")').click();

    // Should disappear
    await expect(page.locator('.post-card').filter({ hasText: TEST_TITLE_EDIT })).toHaveCount(
      0,
      { timeout: 10_000 },
    );
  });
});
