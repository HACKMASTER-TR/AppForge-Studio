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


def service_instance(
    service_id,
    environment_id
):
    data = graphql(
        """
        query(
          $serviceId: String!,
          $environmentId: String!
        ) {
          serviceInstance(
            serviceId: $serviceId,
            environmentId: $environmentId
          ) {
            serviceId
            environmentId
            source {
              image
              repo
              branch
            }
          }
        }
        """,
        {
            "serviceId":
                service_id,

            "environmentId":
                environment_id
        }
    )

    return data.get(
        "serviceInstance"
    ) or {}


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


def connect_image(
    service_id,
    image
):
    graphql(
        """
        mutation(
          $id: String!,
          $input: ServiceSourceInput!
        ) {
          serviceConnect(
            id: $id,
            input: $input
          ) {
            id
            name
          }
        }
        """,
        {
            "id":
                service_id,

            "input": {
                "image":
                    image
            }
        }
    )


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
          serviceInstanceDeployV2(
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
            "serviceInstanceDeployV2"
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
    service_id,
    environment_id,
    previous_image
):
    if not previous_image:
        raise RuntimeError(
            "Rollback için eski image bulunamadı."
        )

    print(
        "ROLLBACK:",
        previous_image
    )

    connect_image(
        service_id,
        previous_image
    )

    rollback_id = start_deploy(
        service_id,
        environment_id
    )

    wait_deploy(
        rollback_id,
        timeout=900
    )

    print(
        "Rollback SUCCESS:",
        rollback_id
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

    before = service_instance(
        service_id,
        environment_id
    )

    previous_image = (
        before
        .get("source", {})
        .get("image")
    )

    print(
        "Service:",
        args.service
    )

    print(
        "Previous image:",
        previous_image
    )

    print(
        "Target image:",
        args.image
    )

    try:
        connect_image(
            service_id,
            args.image
        )

        deployment_id = start_deploy(
            service_id,
            environment_id
        )

        print(
            "Deployment ID:",
            deployment_id
        )

        wait_deploy(
            deployment_id,
            timeout=args.timeout
        )

        after = service_instance(
            service_id,
            environment_id
        )

        active_image = (
            after
            .get("source", {})
            .get("image")
        )

        if (
            active_image !=
            args.image
        ):
            raise RuntimeError(
                "Railway image doğrulaması başarısız. "
                f"Beklenen={args.image}, "
                f"aktif={active_image}"
            )

        health_gate(
            args.studio_url,
            args.heartbeat
        )

        print(
            "✅ PRODUCTION DEPLOY SUCCESS"
        )

    except Exception:
        print(
            "❌ Production gate başarısız.",
            file=sys.stderr
        )

        try:
            rollback(
                service_id,
                environment_id,
                previous_image
            )

            health_gate(
                args.studio_url,
                None
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
