# Live Build Logs

v1.7 stores build output line-by-line in:

`appforge_build_log_lines`

This becomes the authoritative source for live logs.

Compatibility:
- the last 400 lines are still mirrored into `appforge_builds.logs`
- older Android clients continue to work

Endpoints:

- `GET /api/builds/:id/logs?after=<id>`
- `GET /api/builds/:id/logs.txt`
- `GET /api/builds/:id/events?after=<id>`

The `/events` endpoint uses Server-Sent Events and emits:
- `ready`
- `log`
- `status`
- `done`
- `error`

Web Studio consumes this stream with authenticated `fetch()` rather than native `EventSource`, allowing the normal Authorization header.
