# GharTV continuation rules

- Read `CURRENT_HANDOFF.md`, `.ghartv-owner-state.json`, `RELEASE_MANIFEST.json`, live `update/latest.json`, and actual Git refs before implementation.
- Continue `AmritSinghGit/ghartv`, lane `ghartv`, main, package `in.ghartv.nova`, existing checkout and existing AVD. No duplicate project, branch, worktree, account or runtime.
- Keep reviewed app source SHA, APK SHA-256 and delivery/control SHA distinct. Never infer an APK's source from current main HEAD.
- The owner approved exact RC5 binary production distribution on 2026-09-12. The microphone fix is nonblocking. Future code changes are not automatically approved for production.
- Never rebuild with a replacement signing key or wipe app storage to solve an update mismatch. Preserve Jio login and household data.
- GitHub is durable authority for source and safe handoffs. A public commit is not proof of Mac sync, Obsidian replication, provider action, running frontend or TV installation.
- Use one verified download-and-run command, cyan terminal identity and run-specific receipts including failure states. Preserve global Operon session identity; do not perform terminal-global repairs.
- Fast-forward only clean canonical checkouts. No force pushes, reset, clean, prune, bulk rebase, unrequested branch/worktree creation or blind commit/upload of local files.
- Remove only provably redundant, checksum-matched lane installers and current-run owned temporary files. Do not sweep unknown Downloads/Desktop directories, repositories, state, signed keys, credentials, completed reports or logs.
- Reports and diagnostics remain private. No tokens, raw logs, IPs, account IDs, household locations or collected TV identities in GitHub. Preserve the current opt-in diagnostics and non-uploaded viewing-history boundary.
- Read the existing local collector configuration without transmitting its admin token to an arbitrary endpoint or redirected origin. Do not collect whole-system TV logs by default.
- Analytics remains tenant `ghartv` in existing Operon Analytics lane and PR #61; no second control surface/service. Unknown/missing data must not appear as zero or certified live data.
- Keep validation minimal and relevant. No long test-suite gates for owner review. Test and disclose actual local/helper checks separately from physical-TV UAT.
- A notification, a download, an installer prompt and a confirmed running version are different states. Do not claim silent or mandatory updates exist in the current APK.
