/**
 * Home page E2E — verify the SPA boots, header renders, no JS errors.
 *
 * Assumes backend is reachable at :8080 (proxied through Express :5174).
 * If backend is down, this test may show a "网络错误" banner — that's
 * expected behavior, not a test failure.
 */

import { test, expect } from '@playwright/test';

test.describe('首页加载', () => {
  test('页面加载 + header 显示', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });

    await page.goto('/');
    await expect(page.locator('.app-title')).toBeVisible();
    await expect(page.locator('.app-title')).toHaveText('帖子管理系统');

    // Header subtitle (proves we're past initial render)
    await expect(page.locator('.app-subtitle')).toBeVisible();

    // No JS console errors
    expect(errors, `console errors: ${errors.join(', ')}`).toHaveLength(0);
  });

  test('控件区可见：搜索框 + 新建按钮', async ({ page }) => {
    await page.goto('/');
    await expect(page.locator('.search-input')).toBeVisible();
    await expect(page.locator('.search-btn')).toBeVisible();
    await expect(page.locator('.create-btn')).toBeVisible();
    await expect(page.locator('.create-btn')).toHaveText('+ 新建帖子');
  });

  test('初始加载后帖子列表或空状态出现', async ({ page }) => {
    await page.goto('/');
    // Wait for either .post-list or .empty
    await expect(page.locator('.post-list, .empty')).toBeVisible({ timeout: 10_000 });
  });
});
