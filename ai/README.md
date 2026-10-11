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

Two layers: an always-on pointer in the agent's context file, and the on-demand skill.

### 1. Install the skill

Claude Code, Codex CLI and Gemini CLI share the Agent Skills format (a folder with `SKILL.md`); only the directory differs.

| Agent | Project dir | User dir | Context file |
|---|---|---|---|
| Claude Code | `.claude/skills/` | `~/.claude/skills/` | `CLAUDE.md` |
| Codex CLI | `.agents/skills/` | `~/.agents/skills/` | `AGENTS.md` |
| Gemini CLI | `.agents/skills/` (alias) or `.gemini/skills/` | `~/.agents/skills/` or `~/.gemini/skills/` | `GEMINI.md` |

```bash
# from a checkout of this repo
scripts/install-skill.sh --agent all --scope project --dest /path/to/your/app

# without a checkout
curl -fsSL https://raw.githubusercontent.com/mandanai/devscope-compose/main/scripts/install-skill.sh | sh -s -- --agent codex
```

Options: `--agent claude|codex|gemini|all`, `--scope project|user`, `--gemini-native` (use `.gemini/skills`), `--link`, `--force`. Reload skills afterwards (Gemini: `/skills reload`). The skill is self-contained: it carries a generated copy of this folder in `references/`.

### 2. Add the always-on pointer

Skills activate on demand from their description. Add this to the context file your agent reads (`AGENTS.md`, `GEMINI.md`, `CLAUDE.md`, `.cursorrules`):

```markdown
## JourneyScope
For anything involving JourneyScope (journey tracing, devscope-* modules, the on-device Inspector), use the
journeyscope skill, or follow
https://raw.githubusercontent.com/mandanai/devscope-compose/main/ai/setup-playbook.md
and the guardrails in .../ai/guardrails.md before editing. Index: .../llms.txt
```

Gemini CLI also supports `@file` imports in `GEMINI.md`; if you installed the skill, `@./.agents/skills/journeyscope/references/guardrails.md` keeps the guardrails always loaded.

You can also invoke it explicitly: "use the journeyscope skill to set up JourneyScope and trace the checkout flow".

## Status

JourneyScope is pre-release (`0.2.0`, not on Maven Central). Agents must publish it locally first; see the playbook.

## Maintaining

API or behaviour changes must update the matching file here in the same PR, then run `scripts/sync-skill-references.sh` to refresh the skill copy. Every Kotlin snippet is lifted from `samples/composeApp` or verified against `devscope-*/api/*.api`.
