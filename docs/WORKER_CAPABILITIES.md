# Worker Capability Routing

Workers advertise capabilities:

```env
WORKER_CAPABILITIES=android-api-37,java-17,gradle
```

Build config can request:

```json
{
  "workerRequirements": [
    "android-api-37",
    "java-17",
    "gradle"
  ]
}
```

A worker only claims a job when its capabilities contain every requested capability.

Examples:
- `android-api-37`
- `java-17`
- `gradle`
- `high-memory`
- `linux`
- `gpu-preview`

This allows different worker pools to service different build requirements.
