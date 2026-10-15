import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e', timeout: 30000, expect: { timeout: 10000 }, retries: 0, workers: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH } : {},
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'], defaultBrowserType: 'chromium' } },
  ],
});
