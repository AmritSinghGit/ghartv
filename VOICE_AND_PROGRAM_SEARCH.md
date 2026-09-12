# Voice, channel and programme search

- The Voice button opens the Google TV / Android TV speech recogniser.
- When no recogniser is installed, GharTV falls back to typed search.
- Channel number/name/language/genre matches are returned immediately from the local index.
- Programme-title matching uses a bounded, on-demand sample of Jio EPG data so startup remains light.
- Current and upcoming programmes can open their channel. A past programme is labelled catch-up only when Jio marks the channel as catch-up capable.
- Pause, rewind, forward and Live are enabled only when Media3 reports a seekable live window; GharTV does not pretend every channel has DVR rights.
