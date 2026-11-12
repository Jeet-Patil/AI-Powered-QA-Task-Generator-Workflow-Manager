import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  timeout: 150000,
  use: {
    baseURL: 'http://127.0.0.1:4201',
    channel: 'chrome',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'off'
  },
  webServer: {
    command: 'npm run start -- --host 127.0.0.1 --port 4201',
    url: 'http://127.0.0.1:4201',
    reuseExistingServer: !process.env.CI,
    timeout: 120000
  },
  projects: [
    {
      name: 'Google Chrome',
      use: { 
        channel: 'chrome',
        video: 'off'
      },
    },
  ],
});