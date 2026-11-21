import { test, expect } from '@playwright/test';

test.describe('Authentication Flow', () => {
  const testEmail = `test-${Date.now()}@example.com`;
  const testPassword = 'SecureP@ssw0rd123!';
  const testDisplayName = 'Test User';

  test.beforeEach(async ({ page }) => {
    // Go to login page
    await page.goto('/login');
    // Wait for page to load
    await page.waitForLoadState('networkidle');
  });

  test('should register a new user', async ({ page }) => {
    await page.goto('/register');
    await page.waitForLoadState('networkidle');

    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);

    await page.click('[data-testid="register-button"]');

    // Should redirect to dashboard/workspace after successful registration
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Verify user is logged in by checking for user menu or profile
    await expect(page.locator('[data-testid="user-menu"]')).toBeVisible({ timeout: 5000 });
  });

  test('should login with existing credentials', async ({ page }) => {
    // First register
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);
    await page.click('[data-testid="register-button"]');
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Logout
    await page.click('[data-testid="user-menu"]');
    await page.click('[data-testid="logout-button"]');
    await expect(page).toHaveURL('/login', { timeout: 5000 });

    // Login again
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.click('[data-testid="login-button"]');

    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });
    await expect(page.locator('[data-testid="user-menu"]')).toBeVisible({ timeout: 5000 });
  });

  test('should reject login with wrong password', async ({ page }) => {
    // First register a user
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);
    await page.click('[data-testid="register-button"]');
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Logout
    await page.click('[data-testid="user-menu"]');
    await page.click('[data-testid="logout-button"]');
    await expect(page).toHaveURL('/login', { timeout: 5000 });

    // Try wrong password
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', 'WrongPassword123!');
    await page.click('[data-testid="login-button"]');

    // Should show error message
    await expect(page.locator('[data-testid="error-message"]')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('[data-testid="error-message"]')).toContainText('Invalid credentials');

    // Should stay on login page
    await expect(page).toHaveURL('/login', { timeout: 5000 });
  });

  test('should reject login for non-existent user', async ({ page }) => {
    await page.fill('[data-testid="email-input"]', 'nonexistent@example.com');
    await page.fill('[data-testid="password-input"]', 'SecureP@ssw0rd123!');
    await page.click('[data-testid="login-button"]');

    await expect(page.locator('[data-testid="error-message"]')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('[data-testid="error-message"]')).toContainText('Invalid credentials');
  });

  test('should enforce rate limiting on failed login attempts', async ({ page }) => {
    // Register a user first
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);
    await page.click('[data-testid="register-button"]');
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Logout
    await page.click('[data-testid="user-menu"]');
    await page.click('[data-testid="logout-button"]');
    await expect(page).toHaveURL('/login', { timeout: 5000 });

    // Make 5 failed attempts
    for (let i = 0; i < 5; i++) {
      await page.fill('[data-testid="email-input"]', testEmail);
      await page.fill('[data-testid="password-input"]', `WrongPassword${i}!`);
      await page.click('[data-testid="login-button"]');
      await expect(page.locator('[data-testid="error-message"]')).toBeVisible({ timeout: 5000 });
      // Clear for next attempt
      await page.fill('[data-testid="password-input"]', '');
    }

    // 6th attempt should be rate limited
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', 'WrongPassword6!');
    await page.click('[data-testid="login-button"]');

    await expect(page.locator('[data-testid="error-message"]')).toContainText('Too many login attempts');
  });

  test('should logout and clear session', async ({ page }) => {
    // Register and login first
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);
    await page.click('[data-testid="register-button"]');
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Logout
    await page.click('[data-testid="user-menu"]');
    await page.click('[data-testid="logout-button"]');

    // Should redirect to login
    await expect(page).toHaveURL('/login', { timeout: 5000 });

    // Try to access protected route - should redirect to login
    await page.goto('/dashboard');
    await expect(page).toHaveURL('/login', { timeout: 5000 });
  });

  test('should persist session across page refresh', async ({ page }) => {
    // Register and login
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);
    await page.click('[data-testid="register-button"]');
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Refresh page
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Should still be logged in
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });
    await expect(page.locator('[data-testid="user-menu"]')).toBeVisible({ timeout: 5000 });
  });

  test('should handle organization switching', async ({ page }) => {
    // Register and login
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);
    await page.click('[data-testid="register-button"]');
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Check if organization switcher exists and works (user menu button)
    const orgSwitcher = page.locator('[data-testid="user-menu"]');
    if (await orgSwitcher.isVisible()) {
      await orgSwitcher.click();
      // Should show organization options
      await expect(page.locator('[data-testid="org-option"]')).toBeVisible();
    }
  });
});

test.describe('Security Headers and CSRF', () => {
  test('should have security headers on backend responses', async ({ page }) => {
    // Make a direct request to the backend to check security headers
    const response = await page.request.get('http://localhost:8081/api/v1/actuator/health');
    const headers = response.headers();

    // Check for security headers
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['x-frame-options']).toBe('DENY');
    expect(headers['x-xss-protection']).toBe('0'); // Spring Security sets this to 0 by default
    expect(headers['referrer-policy']).toBe('strict-origin-when-cross-origin');
    expect(headers['content-security-policy']).toContain("default-src 'self'");
    expect(headers['permissions-policy']).toContain('geolocation=()');
  });
});

test.describe('Session Restoration', () => {
  test('should restore session from HttpOnly cookie', async ({ page, context }) => {
    const testEmail = `restore-${Date.now()}@example.com`;
    const testPassword = 'SecureP@ssw0rd123!';

    // Register and login
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    await page.fill('[data-testid="email-input"]', testEmail);
    await page.fill('[data-testid="password-input"]', testPassword);
    await page.fill('[data-testid="display-name-input"]', testDisplayName);
    await page.click('[data-testid="register-button"]');
    await expect(page).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });

    // Get cookies - refresh token should be HttpOnly
    const cookies = await context.cookies();
    const refreshCookie = cookies.find(c => c.name === 'refresh_token');
    expect(refreshCookie).toBeTruthy();
    expect(refreshCookie?.httpOnly).toBe(true);
    expect(refreshCookie?.secure).toBe(true);
    expect(refreshCookie?.sameSite).toBe('Strict');

    // Create new page in same context (simulates new tab)
    const newPage = await context.newPage();
    await newPage.goto('/dashboard');
    await newPage.waitForLoadState('networkidle');

    // Should be logged in without re-entering credentials
    await expect(newPage).toHaveURL(/.*dashboard|.*workspace/, { timeout: 10000 });
    await expect(newPage.locator('[data-testid="user-menu"]')).toBeVisible({ timeout: 5000 });
  });
});