#!/usr/bin/env python3

import argparse
import json
import os
import subprocess
import sys
import time


API = (
    "https://backboard.railway.com/graphql/v2"
)

TOKEN = (
    os.environ.get(
        "RAILWAY_PROJECT_TOKEN",
        ""
    ).strip()
)


def graphql(query, variables=None):
    if not TOKEN:
        raise RuntimeError(
            "RAILWAY_PROJECT_TOKEN tanımlı değil."
        )

    payload = json.dumps({
        "query": query,
        "variables": variables or {}
    })

    result = subprocess.run(
        [
            "curl",
            "--silent",
            "--show-error",
            "--fail-with-body",
            "--request",
            "POST",
            "--url",
            API,
            "--header",
            f"Project-Access-Token: {TOKEN}",
            "--header",
            "Content-Type: application/json",
            "--header",
            "Accept: application/json",
            "--header",
            "User-Agent: AppForge-Production-Automation/1.0",
            "--data-binary",
            payload
        ],
        capture_output=True,
        text=True
    )

    if result.returncode != 0:
        raise RuntimeError(
            result.stderr.strip()
            or
            result.stdout.strip()
        )

    response = json.loads(
        result.stdout
    )

    if response.get("errors"):
        raise RuntimeError(
            json.dumps(
                response["errors"],
                ensure_ascii=False
            )
        )

    return response.get(
        "data",
        {}
    )


def context():
    data = graphql(
        """
        query {
          projectToken {
            projectId
            environmentId
          }
        }
        """
    )

    ctx = data.get(
        "projectToken"
    )

    if not ctx:
        raise RuntimeError(
            "Railway Project Token context bulunamadı."
        )

    return ctx


def project_services(project_id):
    data = graphql(
        """
        query($id: String!) {
          project(id: $id) {
            name
            services {
              edges {
                node {
                  id
                  name
                }
              }
            }
          }
        }
        """,
        {
            "id":
                project_id
        }
    )

    project = data.get(
        "project"
    )

    if not project:
        raise RuntimeError(
            "Railway projesi bulunamadı."
        )

    return [
        edge["node"]
        for edge in
        project
        .get("services", {})
        .get("edges", [])
    ]


def railway_cli(
    args,
    project_id,
    environment_id
):
    env = os.environ.copy()

    # Railway Project Token CLI tarafından
    # RAILWAY_TOKEN değişkeninden okunur.
    env["RAILWAY_TOKEN"] = TOKEN

    command = [
        "npx",
        "-y",
        "@railway/cli@latest",
        *args
    ]

    print(
        "Railway CLI:",
        " ".join(
            command[:4]
            +
            ["..."]
        ),
        flush=True
    )

    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        env=env
    )

    if result.returncode != 0:
        raise RuntimeError(
            "Railway CLI hatası: "
            +
            (
                result.stderr.strip()
                or
                result.stdout.strip()
            )
        )

    if result.stdout.strip():
        print(
            result.stdout.strip()
        )

    return result.stdout


def redeploy_service(
    service_name,
    project_id,
    environment_id
):
    print(
        "Railway redeploy:",
        service_name,
        flush=True
    )

    railway_cli(
        [
            "redeploy",
            "--service",
            service_name,
            "--yes",
            "--json"
        ],
        project_id,
        environment_id
    )



def deployments(
    project_id,
    service_id,
    environment_id,
    first=30
):
    data = graphql(
        """
        query(
          $projectId: String!,
          $serviceId: String!,
          $environmentId: String!,
          $first: Int!
        ) {
          deployments(
            first: $first,
            input: {
              projectId: $projectId,
              serviceId: $serviceId,
              environmentId: $environmentId
            }
          ) {
            edges {
              node {
                id
                status
                createdAt
              }
            }
          }
        }
        """,
        {
            "projectId":
                project_id,

            "serviceId":
                service_id,

            "environmentId":
                environment_id,

            "first":
                first
        }
    )

    rows = [
        edge["node"]
        for edge in
        data
        .get("deployments", {})
        .get("edges", [])
    ]

    rows.sort(
        key=lambda x:
            x.get(
                "createdAt",
                ""
            ),
        reverse=True
    )

    return rows


def start_deploy(
    service_id,
    environment_id
):
    data = graphql(
        """
        mutation(
          $serviceId: String!,
          $environmentId: String!
        ) {
          serviceInstanceDeploy(
            serviceId: $serviceId,
            environmentId: $environmentId
          )
        }
        """,
        {
            "serviceId":
                service_id,

            "environmentId":
                environment_id
        }
    )

    deployment_id = (
        data.get(
            "serviceInstanceDeploy"
        )
    )

    if not deployment_id:
        raise RuntimeError(
            "Railway deployment ID döndürmedi."
        )

    return deployment_id



def deployment_status(
    deployment_id
):
    data = graphql(
        """
        query($id: String!) {
          deployment(id: $id) {
            id
            status
            createdAt
          }
        }
        """,
        {
            "id":
                deployment_id
        }
    )

    return (
        data.get("deployment")
        or {}
    )


def wait_deploy(
    deployment_id,
    timeout=900
):
    started = time.time()

    while (
        time.time() -
        started <
        timeout
    ):
        item = deployment_status(
            deployment_id
        )

        status = (
            item
            .get(
                "status",
                ""
            )
            .upper()
        )

        print(
            "Deployment",
            deployment_id,
            status,
            flush=True
        )

        if status == "SUCCESS":
            return

        if status in {
            "FAILED",
            "CRASHED",
            "REMOVED",
            "SKIPPED"
        }:
            raise RuntimeError(
                f"Deployment {status}"
            )

        time.sleep(8)

    raise RuntimeError(
        "Deployment timeout."
    )


def get_json(url):
    result = subprocess.run(
        [
            "curl",
            "--silent",
            "--show-error",
            "--fail",
            "--max-time",
            "20",
            url
        ],
        capture_output=True,
        text=True
    )

    if result.returncode != 0:
        raise RuntimeError(
            result.stderr.strip()
            or
            f"HTTP health hatası: {url}"
        )

    return json.loads(
        result.stdout
    )


def health_gate(
    studio_url,
    heartbeat_token=None
):
    base = (
        studio_url
        .rstrip("/")
    )

    ready = get_json(
        base + "/ready"
    )

    if not ready.get("ok"):
        raise RuntimeError(
            "/ready başarısız."
        )

    health = get_json(
        base + "/health"
    )

    if not health.get("ok"):
        raise RuntimeError(
            "/health başarısız."
        )

    if not health.get(
        "database",
        False
    ):
        raise RuntimeError(
            "Database health başarısız."
        )

    if heartbeat_token:
        token = (
            heartbeat_token
            .lower()
        )

        deadline = (
            time.time() + 120
        )

        while True:
            health = get_json(
                base + "/health"
            )

            workers = (
                health
                .get("queue", {})
                .get("workers", [])
            )

            matches = [
                worker
                for worker in workers
                if (
                    token
                    in
                    str(
                        worker.get(
                            "worker_id",
                            ""
                        )
                    ).lower()
                    and
                    worker.get(
                        "toolchain_ok",
                        False
                    )
                )
            ]

            if matches:
                print(
                    "Heartbeat OK:",
                    matches[0].get(
                        "worker_id"
                    )
                )
                break

            if time.time() >= deadline:
                raise RuntimeError(
                    "Yeni Worker heartbeat görülmedi: "
                    + heartbeat_token
                )

            print(
                "Worker heartbeat bekleniyor:",
                heartbeat_token,
                flush=True
            )

            time.sleep(5)

    print(
        "Production health gate SUCCESS."
    )


def rollback(
    previous_success_id,
    studio_url
):
    if not previous_success_id:
        raise RuntimeError(
            "Rollback için önceki SUCCESS deployment yok."
        )

    print(
        "ROLLBACK deployment:",
        previous_success_id
    )

    data = graphql(
        """
        mutation($id: String!) {
          deploymentRollback(id: $id) {
            id
          }
        }
        """,
        {
            "id":
                previous_success_id
        }
    )

    rollback_result = (
        data.get(
            "deploymentRollback"
        )
        or {}
    )

    rollback_id = (
        rollback_result.get(
            "id"
        )
    )

    if rollback_id:
        wait_deploy(
            rollback_id,
            timeout=900
        )

    health_gate(
        studio_url,
        None
    )

    print(
        "✅ Rollback SUCCESS"
    )



def deploy(args):
    ctx = context()

    project_id = (
        ctx["projectId"]
    )

    environment_id = (
        ctx["environmentId"]
    )

    services = project_services(
        project_id
    )

    service = next(
        (
            item
            for item in services
            if item.get("name")
            == args.service
        ),
        None
    )

    if not service:
        raise RuntimeError(
            "Railway servisi bulunamadı: "
            + args.service
        )

    service_id = (
        service["id"]
    )

    before_rows = deployments(
        project_id,
        service_id,
        environment_id,
        first=30
    )

    previous_success = next(
        (
            item
            for item in before_rows
            if (
                item
                .get(
                    "status",
                    ""
                )
                .upper()
                == "SUCCESS"
            )
        ),
        None
    )

    previous_success_id = (
        previous_success.get("id")
        if previous_success
        else None
    )

    print(
        "Service:",
        args.service
    )

    print(
        "Previous SUCCESS:",
        previous_success_id
    )

    print(
        "Target image:",
        args.image
    )

    deployment_id = None

    try:
        before_ids = {
            item.get("id")
            for item in before_rows
            if item.get("id")
        }

        # Servis kalıcı olarak :latest image'a bağlıdır.
        # Image workflow latest digest'i güncelledikten sonra
        # Project Token ile yalnız redeploy yapılır.
        redeploy_service(
            args.service,
            project_id,
            environment_id
        )

        deployment_id = None
        discover_deadline = time.time() + 120

        while time.time() < discover_deadline:
            current_rows = deployments(
                project_id,
                service_id,
                environment_id,
                first=10
            )

            new_deployment = next(
                (
                    item
                    for item in current_rows
                    if (
                        item.get("id")
                        and
                        item.get("id") not in before_ids
                    )
                ),
                None
            )

            if new_deployment:
                deployment_id = new_deployment["id"]
                break

            print(
                "Yeni Railway deployment bekleniyor...",
                flush=True
            )

            time.sleep(5)

        if not deployment_id:
            raise RuntimeError(
                "Redeploy başladı ancak yeni deployment bulunamadı."
            )

        print(
            "Deployment ID:",
            deployment_id
        )

        wait_deploy(
            deployment_id,
            timeout=args.timeout
        )

        # Yeni Worker gerçekten Studio tarafından
        # heartbeat ile görülmeden production SUCCESS değil.
        health_gate(
            args.studio_url,
            args.heartbeat
        )

        print(
            "✅ PRODUCTION DEPLOY SUCCESS"
        )

    except Exception as original_error:
        print(
            "❌ Production gate başarısız:",
            str(original_error),
            file=sys.stderr
        )

        if deployment_id and previous_success_id:
            try:
                rollback(
                    previous_success_id,
                    args.studio_url
                )

            except Exception as rollback_error:
                print(
                    "❌ ROLLBACK FAILED:",
                    rollback_error,
                    file=sys.stderr
                )

        raise



def volume_maintenance(
    project_id,
    environment_id,
    services,
    backup_postgres
):
    service_names = {
        item["id"]:
            item.get(
                "name",
                item["id"]
            )
        for item in services
    }

    try:
        data = graphql(
            """
            query($id: String!) {
              project(id: $id) {
                volumes {
                  id
                  name
                  volumeInstances {
                    id
                    serviceId
                    environmentId
                    currentSizeMB
                    sizeMB
                    state
                  }
                }
              }
            }
            """,
            {
                "id":
                    project_id
            }
        )

        volumes = (
            data
            .get("project", {})
            .get("volumes", [])
        )

    except Exception as exc:
        print(
            "::warning::Railway volume API kontrolü atlandı: "
            + str(exc)
        )

        return

    for volume in volumes:
        for instance in (
            volume.get(
                "volumeInstances",
                []
            )
        ):
            if (
                instance.get(
                    "environmentId"
                )
                != environment_id
            ):
                continue

            used = float(
                instance.get(
                    "currentSizeMB"
                )
                or 0
            )

            size = float(
                instance.get(
                    "sizeMB"
                )
                or 0
            )

            service_name = (
                service_names.get(
                    instance.get(
                        "serviceId"
                    ),
                    "-"
                )
            )

            ratio = (
                used / size
                if size > 0
                else 0
            )

            print(
                "Volume:",
                volume.get("name"),
                service_name,
                f"{used:.0f}/{size:.0f} MB",
                f"{ratio * 100:.1f}%"
            )

            if ratio >= 0.80:
                print(
                    "::warning::Railway volume %80 üzeri: "
                    f"{volume.get('name')} "
                    f"{ratio * 100:.1f}%"
                )

            if (
                backup_postgres
                and
                service_name.lower()
                == "postgres"
            ):
                instance_id = (
                    instance.get("id")
                )

                if not instance_id:
                    continue

                try:
                    graphql(
                        """
                        mutation($id: String!) {
                          volumeInstanceBackupCreate(
                            volumeInstanceId: $id
                          )
                        }
                        """,
                        {
                            "id":
                                instance_id
                        }
                    )

                    print(
                        "✅ Postgres volume backup tetiklendi:",
                        instance_id
                    )

                except Exception as exc:
                    print(
                        "::warning::Postgres backup tetiklenemedi: "
                        + str(exc)
                    )


def maintenance(args):
    health_gate(
        args.studio_url,
        None
    )

    ctx = context()

    project_id = (
        ctx["projectId"]
    )

    environment_id = (
        ctx["environmentId"]
    )

    services = project_services(
        project_id
    )

    failures = []

    for service in services:
        rows = deployments(
            project_id,
            service["id"],
            environment_id,
            first=10
        )

        active = next(
            (
                item
                for item in rows
                if item.get(
                    "status",
                    ""
                ).upper()
                != "REMOVED"
            ),
            None
        )

        print(
            "Service:",
            service.get("name"),
            "latest:",
            (
                active.get("status")
                if active
                else "NONE"
            )
        )

        if (
            active
            and
            active.get(
                "status",
                ""
            ).upper()
            in {
                "FAILED",
                "CRASHED"
            }
        ):
            failures.append(
                service.get("name")
            )

    volume_maintenance(
        project_id,
        environment_id,
        services,
        args.backup_postgres
    )

    if failures:
        raise RuntimeError(
            "Başarısız Railway servisleri: "
            + ", ".join(failures)
        )

    print(
        "✅ Railway maintenance SUCCESS"
    )


def main():
    parser = argparse.ArgumentParser()

    sub = parser.add_subparsers(
        dest="command",
        required=True
    )

    dep = sub.add_parser(
        "deploy"
    )

    dep.add_argument(
        "--service",
        required=True
    )

    dep.add_argument(
        "--image",
        required=True
    )

    dep.add_argument(
        "--heartbeat",
        default=""
    )

    dep.add_argument(
        "--studio-url",
        required=True
    )

    dep.add_argument(
        "--timeout",
        type=int,
        default=900
    )

    maintenance_parser = (
        sub.add_parser(
            "maintenance"
        )
    )

    maintenance_parser.add_argument(
        "--studio-url",
        required=True
    )

    maintenance_parser.add_argument(
        "--backup-postgres",
        action="store_true"
    )

    args = parser.parse_args()

    if args.command == "deploy":
        deploy(args)

    elif args.command == "maintenance":
        maintenance(args)


if __name__ == "__main__":
    main()
