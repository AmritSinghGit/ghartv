# GharTV v0.5.4 — log-driven living-room review

This candidate continues the existing `ghartv` project on `main` and keeps package
`in.ghartv.nova`, the existing release signing identity, Jio session, favourites and
opt-in v0.5.3 diagnostics contract.

## Evidence sources

The review runner separates two evidence planes:

1. **Product telemetry** — opt-in Cloudflare summary/export from the existing
   `ghartv-telemetry` collector. These are privacy-filtered TV events.
2. **Delivery/runtime history** — sanitized GharTV terminal receipts, setup logs,
   emulator logcat and continuity records on the owner Mac. These explain build and
   deployment friction but are not counted as customer product failures.

When the collector contains no product events, the report says so explicitly. It does
not invent usage conclusions.

## Candidate changes

- `For you`, `Recent`, `Favourites`, `Available`, language, genre, `Subscription` and
  `Needs attention` views.
- Local-only successful-viewing memory; it is never passed to Telemetry.
- O(1) channel-number lookup and cached category views.
- Ranked Unicode-aware exact, prefix and token search.
- Channel counts in category chips and per-view focus restoration.
- Responsive 4-column 1080p living-room guide with tighter card density.
- One automatic retry for DNS, timeout and connection-reset playback failures.
- Better Jio access/subscription/network/DRM recovery copy with Next, Guide and Retry.

## Release boundary

`v0.5.4-rc4` is a GitHub prerelease for review. The stable update manifest remains on
v0.5.3, so existing televisions do not receive v0.5.4 automatically. Owner acceptance
is required before publishing a stable v0.5.4 update request.
