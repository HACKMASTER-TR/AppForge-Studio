#!/usr/bin/env python3
"""Read-only AppForge staging Cloudflare credential preflight."""

import json
import os
import re
import sys
import urllib.error
import urllib.request

WORKER = "appforge-control-plane"
ACCOUNT = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "")
DATABASE = os.environ.get("CLOUDFLARE_D1_DATABASE_ID", "")
TOKEN = os.environ.get("CLOUDFLARE_API_TOKEN", "")


def stop(code):
    print("STOP: " + code)
    raise SystemExit(1)


def get_json(path):
    # Never print response bodies, headers, tokens, IDs or URLs.
    url = "https://api.cloudflare.com/client/v4/" + path

    request = urllib.request.Request(
        url,
        headers={
            "Authorization": "Bearer " + TOKEN,
            "Accept": "application/json",
        },
        method="GET",
    )

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    opener = urllib.request.build_opener(NoRedirect)

    for attempt in range(1, 4):
        try:
            with opener.open(request, timeout=20) as response:
                if response.status != 200:
                    print(
                        "CLOUDFLARE_HTTP_STATUS="
                        + str(response.status)
                    )
                    stop("CLOUDFLARE_HTTP_STATUS")

                try:
                    payload = json.load(response)
                except ValueError:
                    print("CLOUDFLARE_FAILURE_KIND=INVALID_JSON")
                    stop("CLOUDFLARE_INVALID_RESPONSE")

        except urllib.error.HTTPError as exc:
            print("CLOUDFLARE_FAILURE_KIND=HTTP_ERROR")
            print("CLOUDFLARE_HTTP_STATUS=" + str(exc.code))
            stop("CLOUDFLARE_READ_DENIED")

        except urllib.error.URLError as exc:
            reason = getattr(exc, "reason", None)

            print("CLOUDFLARE_FAILURE_KIND=URL_ERROR")
            print(
                "CLOUDFLARE_NETWORK_ERROR="
                + (
                    type(reason).__name__
                    if reason is not None
                    else "Unknown"
                )
            )
            print("CLOUDFLARE_ATTEMPT=" + str(attempt))

            if attempt < 3:
                continue

            stop("CLOUDFLARE_READ_FAILED")

        except OSError as exc:
            print("CLOUDFLARE_FAILURE_KIND=OS_ERROR")
            print(
                "CLOUDFLARE_OS_ERROR="
                + type(exc).__name__
            )
            print("CLOUDFLARE_ATTEMPT=" + str(attempt))

            if attempt < 3:
                continue

            stop("CLOUDFLARE_READ_FAILED")

        if not isinstance(payload, dict):
            stop("UNEXPECTED_API_RESPONSE")

        if payload.get("success") is not True:
            stop("CLOUDFLARE_API_REJECTED")

        return payload.get("result")

    stop("CLOUDFLARE_READ_FAILED")


print("=== APPFORGE CLOUDFLARE AUTH PREFLIGHT ===")
print("MODE=READ_ONLY")
print("WORKER=" + WORKER)

if not TOKEN or not ACCOUNT or not DATABASE:
    stop("MISSING_GITHUB_SECRET")

if not re.fullmatch(r"[0-9a-fA-F]{32}", ACCOUNT):
    stop("INVALID_ACCOUNT_ID_FORMAT")

if not re.fullmatch(
    r"[0-9a-fA-F]{8}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{12}",
    DATABASE,
):
    stop("INVALID_D1_DATABASE_ID_FORMAT")

print("SECRET_VALUES=NOT_PRINTED")
print("IDENTIFIER_FORMATS=PASS")

# This CI token is intentionally scoped to one existing Worker.
# A Worker Editor token is proven by reading that Worker's settings
# directly; account-wide token introspection is not required.
print("TOKEN_AUTH_MODE=WORKER_RESOURCE")

settings = get_json(
    "accounts/" + ACCOUNT +
    "/workers/scripts/" + WORKER + "/settings"
)

if not isinstance(settings, dict):
    stop("WORKER_SETTINGS_UNAVAILABLE")

bindings = settings.get("bindings")

if not isinstance(bindings, list):
    stop("WORKER_BINDINGS_NOT_VERIFIABLE")

db_bindings = [
    item for item in bindings
    if isinstance(item, dict)
    and item.get("name") == "DB"
    and item.get("type") in ("d1", "d1_database")
]

if len(db_bindings) != 1:
    stop("EXPECTED_DB_BINDING_NOT_FOUND")

binding = db_bindings[0]
actual_id = (
    binding.get("id")
    or binding.get("database_id")
)

if (
    not isinstance(actual_id, str)
    or actual_id.lower() != DATABASE.lower()
):
    stop("D1_DATABASE_ID_MISMATCH")

print("WORKER_SETTINGS=READABLE")
print("DB_BINDING=PASS")
print("D1_DATABASE_ID=MATCH")

names = {
    item.get("name")
    for item in bindings
    if isinstance(item, dict)
}

expected_names = {
    "GOOGLE_ANDROID_CLIENT_ID",
    "GOOGLE_WEB_CLIENT_ID",
}

if not expected_names.issubset(names):
    stop("GOOGLE_CONFIG_NAMES_MISSING")

print("GOOGLE_CONFIG_NAMES=PASS")
print("GOOGLE_CONFIG_VALUES=NOT_PRINTED")
print("CLOUDFLARE_AUTH_PREFLIGHT=PASS")
print("WORKER_DEPLOY=NOT_STARTED")
print("MIGRATIONS_APPLY=NOT_STARTED")
