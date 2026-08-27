# GharTV Jio Live v0.5.2 — physical TV feedback candidate

This release continues the existing `ghartv` project, package identity, `main` branch and signing key.

## Corrected

- The player strip is no longer permanent. A television guide panel appears on tune, INFO or OK and auto-hides during viewing.
- The panel shows current programme, start/end time, completion progress, next programme and Previous/Guide/Next actions.
- Channel Up/Down remains inside the language/category view that launched playback.
- Subscription channels are separated into a Subscription category.
- Provider-refused HTTP 403 channels are recorded under Unavailable instead of repeatedly asking for Jio reconnection.
- Subscription and unavailable dialogs include Next channel, Guide and Retry.
- `business_type=premium` is used for subscription grouping; Jio's broad `is_premium` flag is deliberately not used.

## Boundary

No subscription, DRM, licensing, geographic or provider restriction is bypassed. Some feeds that Jio lists can still be refused by Jio's playback API.
