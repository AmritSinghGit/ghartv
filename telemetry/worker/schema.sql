CREATE TABLE IF NOT EXISTS telemetry_events (
    event_id TEXT PRIMARY KEY,
    received_at INTEGER NOT NULL,
    client_ts INTEGER NOT NULL,
    install_hash TEXT NOT NULL,
    session_id TEXT NOT NULL,
    event_name TEXT NOT NULL,
    app_version TEXT NOT NULL,
    version_code INTEGER NOT NULL,
    screen TEXT NOT NULL,
    manufacturer TEXT NOT NULL,
    model TEXT NOT NULL,
    android_release TEXT NOT NULL,
    android_api INTEGER NOT NULL,
    locale TEXT NOT NULL,
    network TEXT NOT NULL,
    reference TEXT NOT NULL,
    attributes_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_received_at ON telemetry_events(received_at);
CREATE INDEX IF NOT EXISTS idx_telemetry_event_name ON telemetry_events(event_name, received_at);
CREATE INDEX IF NOT EXISTS idx_telemetry_version ON telemetry_events(version_code, received_at);
CREATE INDEX IF NOT EXISTS idx_telemetry_install ON telemetry_events(install_hash, received_at);

CREATE TABLE IF NOT EXISTS ingest_windows (
    install_hash TEXT NOT NULL,
    window_bucket INTEGER NOT NULL,
    event_count INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (install_hash, window_bucket)
);
