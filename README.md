# Example Payment Plugin

An Eclipse plugin exposing a payment processor (`ExamplePaymentProcessor`) and payment
gateway (`ExamplePaymentGateway`) for a wallet-based payment flow (`ET_WALLET_ETTA`),
built and shipped to the Eclipse sandbox via GitHub Actions.

## Forking / getting started

1. Fork or clone this repo.
2. Open it in your IDE with a JDK 26 and Maven 3.9.16 available locally (matches what CI uses).
3. Point your local `~/.m2/settings.xml` at the `JGREL` Artifactory repo (needed to resolve
   the `com.ukheshe:*` parent POM and dependencies) — ask a teammate for the mirror/server
   config, or copy it from someone already set up. Never commit this file.
4. Build locally:
   ```
   mvn clean package
   ```
   The jar lands in `target/`.

## GitHub Secrets

The workflow (`.github/workflows/build.yml`) needs these repo secrets
(**Settings → Secrets and variables → Actions → New repository secret**):

| Secret | Used for |
|---|---|
| `JGREL_USERNAME` | Maven Artifactory login (resolving `com.ukheshe:*` deps/plugins during build) |
| `JGREL_PASSWORD` | Maven Artifactory login |
| `LOGIN_URL` | Eclipse auth endpoint to obtain a JWT before uploading the plugin |
| `LOGIN_USERNAME` | Eclipse login identity |
| `LOGIN_PASSWORD` | Eclipse login password |
| `UPLOAD_URL` | Plugin dashboard's upload endpoint |
| `TELEGRAM_BOT_TOKEN` | Sends a build/deploy status message to Telegram |
| `TELEGRAM_CHAT_ID` | Chat the Telegram notification is sent to |

Without all eight set, the `build`, `deploy`, or `notify` jobs will fail at that step.

## What the pipeline does

- **build** — checks out the repo, sets up JDK 26 + Maven 3.9.16, resolves dependencies
  through the `JGREL` repo, runs `mvn clean package`, uploads the jar as a workflow artifact.
  Runs on every push and PR.
- **deploy** — only on a push to `main`. Downloads the jar, logs in to Eclipse to get a JWT,
  then `POST`s the base64-encoded jar to the plugin dashboard's upload endpoint.
- **notify** — always runs last (even if `build`/`deploy` failed) and posts a ✅/❌ summary
  to Telegram with the commit and a link to the run.

## Continuing development

- Plugin logic lives in `src/main/java/com/eftcorp/plugins/`:
  - `ExamplePlugin.java` — the `@PluginDescriptor` (plugin identity/name, used to upgrade
    in place on re-upload).
  - `ExamplePaymentProcessor.java` — `IPaymentProcessor`, registered via `@ExtensionProvider`.
  - `ExamplePaymentGateway.java` — `IPaymentGateway`-family implementation, also registered
    via `@ExtensionProvider`. Kept `@Dependent` scoped (not `@ApplicationScoped`) since it
    holds per-payment state — reusing one instance across payments throws `PAY003`.
- After merging to `main`, the pipeline builds and uploads automatically. On the plugin
  dashboard, confirm the new version shows a clean **History** entry, and if beans/mapping
  don't seem to take effect, a manual **Turn Off → Turn On** cycle forces a full reload.
- Test payments through the tenant's `POST /eclipse-conductor/rest/v1/tenants/{tenantId}/payments`
  endpoint with `"type"` set to the processor's registered `@ExtensionProvider` name.
