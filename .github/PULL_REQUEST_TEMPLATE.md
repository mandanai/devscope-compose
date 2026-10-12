## Summary
<!-- What changes and why. Link the issue: Closes #123 -->

## Type of change
- [ ] Bug fix
- [ ] New feature
- [ ] Refactor / chore
- [ ] Documentation
- [ ] Breaking change

## Testing
<!-- Commands run and platforms checked -->
- [ ] `./gradlew spotlessCheck detekt checkKotlinAbi`
- [ ] `./gradlew testAndroidHostTest`
- [ ] `./gradlew iosSimulatorArm64Test` (macOS), if iOS code changed
- [ ] Regression test added for bug fixes

## Checklist
- [ ] Public API changes update the ABI baseline (`updateKotlinAbi`) and have KDoc
- [ ] Docs updated; `ai/` changed with API/behavior and `scripts/sync-skill-references.sh` run
- [ ] No new deprecation warnings (`./gradlew help --warning-mode all`)
- [ ] No secrets, tokens or personal data in code, tests or logs
- [ ] New dependencies come from `gradle/libs.versions.toml` and are justified below
- [ ] CI is green

## Security and dependencies
<!-- New dependencies, captured data, redaction impact, or "none" -->
