# Physical TV feedback implemented in v0.5.2

1. **Persistent bottom strip** — removed. The player guide auto-hides after seven seconds unless an action has focus. INFO or OK brings it back.
2. **Now/Next guide** — shows current programme, time range, progress, next programme and Previous/Guide/Next controls.
3. **Scoped channel surfing** — CH+/CH− uses the exact language/category selected on the home guide.
4. **Subscription category** — catalogue records with `business_type=premium` are kept in a separate Subscription group.
5. **Unavailable category** — channels refused by Jio with a non-subscription HTTP 403 are remembered separately until they play successfully.
6. **Recovery** — subscription and failure dialogs now provide Next channel, Guide and Retry.
7. **No false reconnect** — a channel-specific HTTP 403 no longer presents Reconnect Jio unless Jio actually returns an authentication status.
