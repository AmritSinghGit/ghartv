# GharTV v0.5.4 RC2 acceptance

## Evidence report

- Open `PRODUCT_INTELLIGENCE_REPORT.html` from the run evidence folder.
- Confirm product telemetry and local build/delivery logs are shown separately.
- When telemetry is empty, confirm the report says there is no opt-in data rather than
  inferring user behaviour.

## Living-room home

- `For you` opens first on an upgraded installation.
- A successful channel appears in `Recent` after returning to the guide.
- `For you` improves after several successful plays while favourites remain first.
- Resetting suggestions does not clear the Jio session or favourites.
- Category chips show channel counts.
- Focus returns to the last channel used in each category.

## Search and indexing

- Exact channel number is first.
- Exact channel name is ahead of partial matches.
- Prefix searches such as `ptc`, `punj`, `sports` and `news` return immediately.
- Search is case-insensitive and supports Unicode channel/language text.
- Number tuning continues to work independently of the visible category.

## Access and recovery

- Regular language and genre views exclude Subscription and Needs attention channels.
- Available contains channels proven playable on this TV/account.
- DNS/timeout failure retries once, then offers Retry, Next channel and Guide.
- A persistent 403 becomes Needs attention, not an internet or guaranteed subscription claim.
- DRM errors remain distinguishable from network and access errors.

## Release safety

- APK updates the existing `in.ghartv.nova` installation and preserves data.
- GitHub release is marked prerelease `v0.5.4-rc2`.
- `update/latest.json` remains versionCode 10 / v0.5.3.
