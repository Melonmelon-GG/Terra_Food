# Password reset regression

Run builds and tests on the mini-host in a disposable checkout. Do not point these
tests at the existing deployment. No production SMTP or account is used.

1. Build the unchanged backend with Java 21/Maven (`mvn -B -DskipTests package`)
   and the changed frontend with the locked npm dependencies (`npm ci`, `npm run build`).
2. Create a private environment file containing a random `RESET_DB_PASSWORD`.
   Use `docker compose --env-file /private/runtime.env -p tf-reset-20261004
   -f web/tests/password-reset/compose.yml up -d`. On Snap installations the Compose
   plugin may need to be invoked directly. Never print the environment file.
3. Wait for backend startup/migrations. Run the scripts from `web/` using the
   project's Playwright dependency and an installed Chromium executable:

   ```sh
   RESET_REPORT_DIR=/evidence BROWSER_EXECUTABLE=/path/to/chrome node scripts/password-reset-smoke.cjs
   RESET_REPORT_DIR=/evidence BROWSER_EXECUTABLE=/path/to/chrome node scripts/password-reset-integration.cjs
   ```

   The browser/API runner must reach host loopback ports (for a container use host
   networking on Linux). The smoke script accepts `RESET_BASE_URL` for another
   isolated loopback preview; every API call is intercepted. The integration script
   uses only ports 18481 and 18425, sends real test mail to Mailpit, and creates a
   unique account through the normal registration API. Its 31-second wait tests the
   deliberately shortened **30-second** code lifetime configured only in this stack.
   These settings are not deployment recommendations.
4. For legacy compatibility, in a separate test copy route `/login` to `LoginView`
   instead of `AuthView`, build, serve on loopback and run the same smoke suite.
   Do not commit this temporary route change. Also build/test the audit branch after
   cherry-picking the fix, preserving its authentication interceptor changes.
5. Save JSON and screenshots, including failed attempts. A script failure returns
   nonzero. `KNOWN_EXISTING_ISSUE` in the old-session observation is reported separately
   from the reset flow; it is not a security acceptance result.
6. With the same environment/project/file arguments run `down -v --remove-orphans`.
   Check that no project-labelled containers, networks or volumes remain and compare
   existing deployment container IDs/start times with the pre-test snapshot.

The SMTP receiver is a deterministic local substitute. Browser interception tests
cannot prove backend correctness; integration results cannot prove real SMTP
delivery, the reporter's phone/browser, or all session revocation scenarios.
