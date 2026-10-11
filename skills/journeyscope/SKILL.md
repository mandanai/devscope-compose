---
name: journeyscope
description: Install, configure and instrument JourneyScope (devscope-* modules) in a Kotlin or Kotlin Multiplatform app. Use when the user mentions JourneyScope, journey tracing, the devscope Inspector, tracing a flow end to end (UI to network to database), or adding network, Flow, navigation or SQLDelight tracing.
---

# JourneyScope setup and instrumentation

Do the setup, configuration and code changes yourself in the user's codebase. The procedure, rules and API reference are in this skill's `references/` folder.

## Locate the knowledge base

Use the first that exists:

1. `references/` next to this file.
2. `ai/` in a checkout of the JourneyScope repo.
3. `https://raw.githubusercontent.com/mandanai/devscope-compose/main/ai/<file>` (index: `llms.txt` at the repo root).

## Procedure

1. Read `references/setup-playbook.md` and follow it step by step.
2. Use `references/decision-tree.md` against the host project to pick modules. Load only the `references/scenarios/*.md` files it selects.
3. Obey `references/guardrails.md` before every edit. Use only symbols in `references/api-cheatsheet.md`.
4. Instrument the flow the user named, top-down, matching the host project's style and dependency mechanism.
5. Run `references/verification.md`. Do not claim done until builds pass, release isolation holds, and one named journey is proven or the gap is stated.
6. Finish with the report described in the playbook (modules, files, flows traced, not traced, unverified).

## Hard rules

- Collection must be debug-only: `JourneyScopeConfig.debugOnly(...)`, `JourneyScope.configure` exactly once.
- Debug UI/export modules are `debugImplementation`, never in release.
- No bodies, tokens, PII or interpolated data in span names or metadata.
- Ask before widening allowlists, enabling body capture or persistence, or changing redaction/sampling.
- JourneyScope is pre-release; if not resolvable, ask before running `./gradlew publishToMavenLocal`.

## If you cannot run builds or shell commands

Still make the edits, then give the user the exact commands from `references/verification.md` to run and say which checks were not run.
