# Load Test Results Bot (n8n)

The workflow from the TesterGuruJi video **"n8n for Testers"**. When a load test finishes, CI posts the run summary to an n8n webhook. n8n judges it against the SLA, an AI agent writes a short summary, Slack gets a red or green report, and a Jira bug is created automatically when the SLA breaks.

> **Let code decide, let AI explain.** The PASS/FAIL verdict comes from a deterministic Code node. The AI only writes the human-friendly summary and is told not to change the status or invent numbers.

## What's inside

| File | What it is |
| --- | --- |
| `workflows/load-test-results-bot.json` | The main workflow (10 nodes) |
| `workflows/error-alert-workflow.json` | Error workflow: posts a Slack alert when the bot fails |
| `samples/run-fail.json` | Payload that breaches the SLA (Checkout P95 3800 ms vs 2000 ms) |
| `samples/run-pass.json` | Payload where everything is within SLA |
| `ci/github-actions-step.yml` | 3-line GitHub Actions step that calls the webhook |
| `ci/Jenkinsfile-stage.groovy` | Same thing as a Jenkins stage |

**No credentials are included.** After importing, attach your own credentials to the Slack, Jira and chat-model nodes.

## The flow

```
Webhook ─▶ SLA Judge (Code) ─▶ SLA breached? (IF)
                                 ├─ true  ─▶ AI Agent ─▶ Slack: FAIL report ─▶ Jira: create bug ─▶ Respond to Webhook
                                 │            ├─ Chat Model (Claude / GPT / Gemini)
                                 │            └─ Get Baseline Run (HTTP Request tool)
                                 └─ false ─▶ Slack: PASS report ─────────────────────────────────▶ Respond to Webhook
```

## Setup

1. **Run n8n locally**

   ```bash
   docker run -it --rm -p 5678:5678 -v n8n_data:/home/node/.n8n docker.n8n.io/n8nio/n8n
   ```

   Open http://localhost:5678 and create your owner account. Use the latest n8n version.

2. **Import the workflows.** In n8n: *Workflows → Import from File*. Import both JSON files from `workflows/`.

3. **Add credentials** (use a test Slack workspace and a test Jira project while trying it out):
   - **Slack: FAIL report**, **Slack: PASS report** and **Slack: error alert**: a Slack bot token with `chat:write`, with the bot invited to `#perf-results`.
   - **Jira: create bug**: Jira Cloud email + API token. Then pick your project (the file uses project key `PERF`) and issue type `Bug`.
   - **Chat Model**: an Anthropic API key. You can swap this node for the OpenAI or Gemini chat model node; the AI Agent works with any of them. Re-select the model from the list after import.

4. **Point the baseline tool at your data.** The **Get Baseline Run** node calls `https://YOUR-RESULTS-SERVER/api/runs/baseline`. Change it to wherever your baseline results live, or delete the node if you don't have a baseline yet. The agent works without it.

5. **Link the error workflow.** Open *Load Test Results Bot → Settings → Error workflow* and select *Load Test Bot: Error Alert*.

## Try it

Open the **Webhook** node, click *Listen for test event*, then send a sample:

```bash
# failing run: red Slack message + Jira bug
curl -X POST "http://localhost:5678/webhook-test/perf-results" \
  -H "Content-Type: application/json" -d @samples/run-fail.json

# passing run: one green Slack message, no bug
curl -X POST "http://localhost:5678/webhook-test/perf-results" \
  -H "Content-Type: application/json" -d @samples/run-pass.json
```

When it works, activate the workflow and use the production URL (`/webhook/perf-results`) from CI. See the snippets in `ci/`.

## Payload format

```json
{
  "runId": "checkout-load-2026-11-11-01",
  "users": 500,
  "durationMin": 30,
  "transactions": [
    { "name": "Login",    "p95Ms": 820,  "errorPct": 0.1 },
    { "name": "Search",   "p95Ms": 1450, "errorPct": 0.3 },
    { "name": "Checkout", "p95Ms": 3800, "errorPct": 1.2 }
  ],
  "sla": { "p95Ms": 2000, "errorPct": 1.0 }
}
```

A transaction breaches the SLA when its P95 is above `sla.p95Ms` **or** its error rate is above `sla.errorPct`.

## Security

n8n stores every credential you give it, so treat it like production infrastructure:

- Keep n8n on the latest version. Several critical vulnerabilities were fixed in early 2026.
- Never expose it to the internet without authentication.
- Keep webhook URLs secret (store them as CI secrets, as in `ci/`).

## Licence

MIT for the workflow files in this repo. n8n itself is licensed under the Sustainable Use License (fair-code).
