#!/usr/bin/env python3

import json
import math
import os
import subprocess
import sys

STUDIO_URL = os.environ.get(
    "STUDIO_URL",
    "https://appforge-studio-production.up.railway.app"
).rstrip("/")

SERVICE_NAME = os.environ.get(
    "RAILWAY_SERVICE",
    "AppForge-Worker"
)

MIN_REPLICAS = max(
    1,
    int(os.environ.get("AUTOSCALE_MIN_REPLICAS", "3"))
)

MAX_REPLICAS = min(
    50,
    max(
        MIN_REPLICAS,
        int(os.environ.get("AUTOSCALE_MAX_REPLICAS", "50"))
    )
)

TOKEN = os.environ.get(
    "RAILWAY_PROJECT_TOKEN",
    ""
).strip()


def run(command, env=None):
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        env=env
    )

    if result.returncode != 0:
        raise RuntimeError(
            result.stderr.strip()
            or result.stdout.strip()
            or "Komut başarısız."
        )

    return result.stdout.strip()


def railway_env():
    if not TOKEN:
        raise RuntimeError(
            "RAILWAY_PROJECT_TOKEN tanımlı değil."
        )

    env = os.environ.copy()
    env["RAILWAY_TOKEN"] = TOKEN
    return env


def railway_json(*args):
    raw = run(
        [
            "npx",
            "-y",
            "@railway/cli@latest",
            *args,
            "--json"
        ],
        env=railway_env()
    )

    return json.loads(raw)


def get_health():
    raw = run([
        "curl",
        "--silent",
        "--show-error",
        "--fail",
        "--retry", "4",
        "--retry-delay", "2",
        "--retry-all-errors",
        "--connect-timeout", "10",
        "--max-time", "30",
        STUDIO_URL + "/health"
    ])

    return json.loads(raw)


def resolve_service_id():
    data = railway_json(
        "service",
        "list"
    )

    services = (
        data
        if isinstance(data, list)
        else data.get("services", [])
    )

    for item in services:
        if item.get("name") == SERVICE_NAME:
            service_id = str(
                item.get("id") or ""
            ).strip()

            if service_id:
                return service_id

    raise RuntimeError(
        "Railway service bulunamadı: "
        + SERVICE_NAME
    )


def region_config(service_id):
    cfg = railway_json(
        "environment",
        "config"
    )

    services = cfg.get(
        "services",
        {}
    )

    service_cfg = services.get(
        service_id,
        {}
    )

    deploy = service_cfg.get(
        "deploy",
        {}
    )

    regions = deploy.get(
        "multiRegionConfig",
        {}
    ) or {}

    active = {}

    for region, value in regions.items():
        if not isinstance(value, dict):
            continue

        replicas = int(
            value.get("numReplicas")
            or 0
        )

        if replicas > 0:
            active[str(region)] = replicas

    if not active:
        raise RuntimeError(
            "Aktif Railway region yapılandırması bulunamadı."
        )

    return active


def worker_snapshot(health):
    queue = health.get(
        "queue",
        {}
    )

    workers = queue.get(
        "workers",
        []
    )

    groups = {}

    for worker in workers:
        caps = set(
            worker.get("capabilities")
            or []
        )

        if not worker.get("toolchain_ok"):
            continue

        if "android-api-37" not in caps:
            continue

        if "source-isolation-dedicated" in caps:
            continue

        worker_id = str(
            worker.get("worker_id")
            or ""
        )

        if not worker_id:
            continue

        base = worker_id.rsplit(
            "#",
            1
        )[0]

        groups[base] = (
            groups.get(base, 0)
            + max(
                1,
                int(
                    worker.get("slots")
                    or 1
                )
            )
        )

    live_replicas = len(groups)
    live_slots = sum(
        groups.values()
    )

    slots_per_replica = (
        max(
            1,
            round(
                live_slots
                / live_replicas
            )
        )
        if live_replicas
        else 2
    )

    return {
        "queued":
            int(queue.get("queued") or 0),

        "running":
            int(queue.get("running") or 0),

        "liveReplicas":
            live_replicas,

        "liveSlots":
            live_slots,

        "slotsPerReplica":
            slots_per_replica
    }


def distribute(regions, desired):
    names = list(
        regions.keys()
    )

    if len(names) == 1:
        return {
            names[0]: desired
        }

    desired = max(
        desired,
        len(names)
    )

    result = {
        name: 1
        for name in names
    }

    remaining = (
        desired
        - len(names)
    )

    while remaining > 0:
        name = min(
            names,
            key=lambda x:
                result[x]
                / max(
                    1,
                    regions[x]
                )
        )

        result[name] += 1
        remaining -= 1

    return result


def scale(service_id, targets):
    args = [
        "npx",
        "-y",
        "@railway/cli@latest",
        "scale",
        "--service",
        service_id,
        "--json"
    ]

    for region, replicas in targets.items():
        args.append(
            f"{region}={replicas}"
        )

    print(
        run(
            args,
            env=railway_env()
        )
    )


def main():
    health = get_health()

    if not health.get("ok"):
        raise RuntimeError(
            "Production health başarısız."
        )

    snapshot = worker_snapshot(
        health
    )

    service_id = resolve_service_id()

    regions = region_config(
        service_id
    )

    configured = sum(
        regions.values()
    )

    queued = snapshot["queued"]
    running = snapshot["running"]

    slots_per_replica = max(
        1,
        snapshot["slotsPerReplica"]
    )

    total_jobs = (
        queued
        + running
    )

    required = (
        math.ceil(
            total_jobs
            / slots_per_replica
        )
        if total_jobs > 0
        else MIN_REPLICAS
    )

    desired = max(
        MIN_REPLICAS,
        min(
            MAX_REPLICAS,
            required
        )
    )

    action = "hold"

    if desired > configured:
        action = "scale_up"

    elif (
        queued == 0
        and running == 0
        and configured > MIN_REPLICAS
    ):
        desired = MIN_REPLICAS
        action = "scale_down_idle"

    else:
        desired = configured

    print(
        json.dumps(
            {
                "queued": queued,
                "running": running,
                "liveReplicas":
                    snapshot["liveReplicas"],
                "liveSlots":
                    snapshot["liveSlots"],
                "slotsPerReplica":
                    slots_per_replica,
                "configuredReplicas":
                    configured,
                "desiredReplicas":
                    desired,
                "minReplicas":
                    MIN_REPLICAS,
                "maxReplicas":
                    MAX_REPLICAS,
                "action":
                    action,
                "regions":
                    regions
            },
            indent=2,
            ensure_ascii=False
        )
    )

    if action == "hold":
        return

    targets = distribute(
        regions,
        desired
    )

    print(
        "Scale target:",
        targets
    )

    scale(
        service_id,
        targets
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            "AUTOSCALE ERROR:",
            error,
            file=sys.stderr
        )
        sys.exit(1)
