# GharTV: which branch is the source of truth (checked 31 Oct 2026)

Records locations only; not a merge decision or release approval.

| Branch / PR | Head | State | What it holds |
| --- | --- | --- | --- |
| `main` | `7c31886` | published | update feed `update/latest.json` (code63), Pages docs, old `android-tv` (versionCode 16) |
| `codex/ghartv-remove-auto-preview` (PR #1) | `56426db` | PR is CLOSED (draft, CONFLICTING with main, 7 files, last updated 25 Oct); branch still exists | the real app source (code48..code63), `web-player/` (port 8790 screen), `tools/tv-first/` installer |
| `lane/ghartv-nova` (PR #2) | `1ad1c52` | OPEN, draft, mergeable | control notes, code64 one-click-play patch + CI workflow |

Decision kept (owner has not said otherwise): leave the three split; do not merge or force-push. Do not delete PR #1's branch: it is the only copy of the app source and the web player.
The revived screen runs from a read-only export of `web-player/` at `http://100.85.0.40:8790/` (registry screen `ghartv-web`).
