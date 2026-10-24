# Published APK source pointer

`main` is the public control and distribution branch. It does not contain the app source of the
APK it distributes. This note says where that source is. It records locations only; it is not an
approval, a merge decision or a release gate.

## Published household APK (code63)

| Item | Value |
| --- | --- |
| versionCode / versionName | 63 / `0.6.0-rc15.4-jio-timeouts-pointer-review` |
| Source commit | `d0dea76361fa3f92f643f7436ada7cf65cc30a10` |
| Source branch | `codex/ghartv-remove-auto-preview` (PR #1) |
| Source tag | `v0.6.0-rc15.4-jio-timeouts-pointer-review` (points at the source commit) |
| APK SHA-256 | `91aa47d31afbbd28fed8ebec40cbcbb77789e3373dfdfc75e18c9d85285c7b7b` |
| Distribution mode | `exact-tested-signed-apk-no-rebuild` |

The values above are copied from `update/latest.json`; `tools/test_source_pointer.py` fails if they drift.

## How to read this

- The source commit is not an ancestor of `main`, and `android-tv/` on `main` is older than the
  published APK (versionCode 16 at the time of writing). Do not infer the APK's source from `main` HEAD.
- Keep the PR1 branch and the source tag. Deleting either removes the only place the code63 source lives.
- Source SHA, APK SHA-256 and the delivery/control commit are three different identifiers. The
  delivery/control commit is whichever `main` commit last changed `update/latest.json`.
- Reconciling PR1 with `main` (merge, or keep split) is an open owner decision. Until it is made, the
  two branches stay split and this note is the link between them.

## Check

```
python3 -I tools/test_source_pointer.py
```
