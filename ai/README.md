# JourneyScope for AI agents

Machine-oriented instructions that let an AI coding agent install, configure and instrument JourneyScope in a host app without the developer doing it by hand. Human docs live in [`docs/`](../docs/getting-started.md); this folder is the procedure, decision rules and guardrails an agent follows.

## Read order

1. [setup-playbook.md](setup-playbook.md) — the end-to-end procedure. Always first.
2. [decision-tree.md](decision-tree.md) — which modules and scenario files apply to the detected project.
3. [scenarios/](scenarios/) — load only the files the decision tree selects.
4. [guardrails.md](guardrails.md) — hard rules; re-read before editing.
5. [verification.md](verification.md) — proof the integration works; required before reporting done.
6. [api-cheatsheet.md](api-cheatsheet.md) — the only APIs an agent may use. [troubleshooting.md](troubleshooting.md) when something fails.

## Typical developer prompts

- "Set up JourneyScope in this app."
- "Trace the checkout flow with JourneyScope."
- "Add JourneyScope network tracing to our OkHttp client."

## Use it from your project

Paste into your project's `AGENTS.md` / `CLAUDE.md` / `.cursorrules`:

```markdown
## JourneyScope
For anything involving JourneyScope (journey tracing, devscope-* modules, the on-device Inspector), follow
https://raw.githubusercontent.com/mandanai/devscope-compose/main/ai/setup-playbook.md
and the guardrails in .../ai/guardrails.md before editing. Index: .../llms.txt
```

Claude Code users can instead copy [`skills/journeyscope`](../skills/journeyscope/SKILL.md) into `.claude/skills/`.

## Status

JourneyScope is pre-release (`0.1.0-SNAPSHOT`, not on Maven Central). Agents must publish it locally first; see the playbook.

## Maintaining

API or behaviour changes must update the matching file here in the same PR. Every Kotlin snippet is lifted from `samples/composeApp` or verified against `devscope-*/api/*.api`.
