# GharTV in Operon Analytics

GharTV remains an independent marketable product and keeps its own repository,
package, signing identity and `ghartv` lane.

It is additionally registered as tenant `ghartv` of the reusable
`operon.analytics` capability. The tenant adapter reads the existing,
owner-controlled privacy-filtered Cloudflare collector through a server-side
admin token. The browser never receives that token.

The dashboard labels installations and active users as **observed/consented**:
people who decline diagnostics cannot ethically be counted as telemetry users.
GitHub APK downloads are shown separately and are not claimed as installations.

Metrics include observed installations, DAU/WAU/MAU, sessions, rounded active
time, playback requests/readiness, feature use, versions, device families,
errors, HTTP statuses and freshness.

Candidate `ANALYTICS-090-008` continues the existing `operon-analytics` lane,
branch `codex/opr-analytics-003-vcnow-data-control-convergence` and PR #61.
The GharTV Android review is deliberately independent of local analytics
materialisation: when the canonical analytics worktree is absent, tenant source
is registered directly on the same GitHub branch without creating another
worktree, and the local dashboard runtime remains deferred until that lane is
materialised again.
