# Build Cancellation

v1.4 supports cancellation for both queued and running builds.

## Queued build
`POST /api/builds/:id/cancel`

The queued job is immediately marked `cancelled`.

## Running build
The API sets `cancel_requested=true`.

The worker checks this flag while Gradle is running. When cancellation is detected:
- Linux/macOS: SIGTERM, then SIGKILL fallback.
- Windows: `taskkill /PID <pid> /T /F`.

The worker marks the job/build as `cancelled` instead of retrying it.

Team builds require the `build.cancel` permission.
