#!/usr/bin/env python3
"""Real no-network APT/dpkg transaction/replay using a TEST-ONLY signed fixture.

Runs in a disposable Ubuntu container, never the host /opt or Android data.
Uses production installer code with only test key/fingerprint fixture substitutions.
This is not acceptance of missing real Ubuntu Packages objects or an Android phone.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from datetime import datetime, timedelta, timezone
from email.utils import format_datetime

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'android-app/app/src/main/assets/device-build'
IMAGE = 'ubuntu:24.04@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852'


def run(*args, **kwargs):
    result=subprocess.run(args, capture_output=True, **kwargs)
    if result.returncode:
        detail=result.stderr.decode(errors='replace') if isinstance(result.stderr,bytes) else result.stderr
        output=result.stdout.decode(errors='replace') if isinstance(result.stdout,bytes) else result.stdout
        raise RuntimeError(f"{args[0]} failed ({result.returncode}): {detail}\n{output}")
    return result


def main():
    docker_env = os.environ.copy()
    for key in ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH'):
        docker_env.pop(key, None)
    docker = ['docker', '--host=unix:///var/run/docker.sock']
    run(*docker, 'image', 'inspect', IMAGE, env=docker_env)  # Pre-pull is explicit.
    with tempfile.TemporaryDirectory(prefix='v9-offline-apt-') as temp:
        base = Path(temp)
        runtime = base / 'runtime'
        runtime.mkdir()
        home = base / 'gpg'
        home.mkdir(mode=0o700)
        run('gpg', '--homedir', str(home), '--batch', '--pinentry-mode', 'loopback', '--passphrase', '',
            '--quick-generate-key', 'Host fixture only <test@example.invalid>', 'ed25519', 'sign', '0')
        fpr = next(line.split(':')[9] for line in run('gpg', '--homedir', str(home), '--batch',
            '--with-colons', '--fingerprint', text=True).stdout.splitlines() if line.startswith('fpr:'))
        (base / 'keyring.gpg').write_bytes(run('gpg', '--homedir', str(home), '--batch', '--export').stdout)
        pkg = base / 'package'
        (pkg / 'DEBIAN').mkdir(parents=True)
        (pkg / 'usr/local/bin').mkdir(parents=True)
        executable = pkg / 'usr/local/bin/appforge-r2-fixture'
        executable.write_text('#!/bin/sh\nprintf "42\\n"\n')
        executable.chmod(0o755)
        (pkg / 'DEBIAN/control').write_text('Package: appforge-r2-fixture\nVersion: 1.0\nArchitecture: amd64\nMaintainer: Test <test@example.invalid>\nInstalled-Size: 4\nDescription: Offline transaction test fixture\n')
        (pkg / 'DEBIAN/md5sums').write_text(hashlib.md5(executable.read_bytes()).hexdigest() + '  usr/local/bin/appforge-r2-fixture\n')
        for directory in (pkg,*[p for p in pkg.rglob('*') if p.is_dir()]):directory.chmod(0o755)
        for control in (pkg/'DEBIAN').iterdir():control.chmod(0o644)
        archive = base / 'fixture.deb'
        run('dpkg-deb', '--root-owner-group', '--build', str(pkg), str(archive))
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        size = archive.stat().st_size
        provenance = 'https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/pool/main/a/appforge-r2-fixture_1.0_amd64.deb'
        (runtime / 'apt-module-packages-v1.tsv').write_text(f'NODE_WEB\tamd64\tappforge-r2-fixture\t1.0\tamd64\t{digest}\t{size}\t{provenance}\n')
        payload = f'Package: appforge-r2-fixture\nVersion: 1.0\nArchitecture: amd64\nFilename: pool/main/a/appforge-r2-fixture_1.0_amd64.deb\nSize: {size}\nSHA256: {digest}\nDescription: fixture\n\n'.encode()
        index_sha = hashlib.sha256(payload).hexdigest()
        store = base / 'store'
        lists = store / 'lists'
        lists.mkdir(parents=True)
        (store / 'archives').mkdir()
        shutil.copyfile(archive, store / 'archives' / (digest + '.deb'))
        profiles = []
        now = datetime.now(timezone.utc)
        for suite in ('noble', 'noble-updates', 'noble-security', 'noble-backports'):
            text = f'Origin: Ubuntu\nCodename: noble\nSuite: {suite}\nDate: {format_datetime(now-timedelta(hours=1))}\nValid-Until: {format_datetime(now+timedelta(hours=1))}\nArchitectures: amd64\nComponents: main restricted universe multiverse\nSHA256:\n'
            for component in ('main', 'restricted', 'universe', 'multiverse'):
                text += f' {index_sha} {len(payload)} {component}/binary-amd64/Packages\n'
                (lists / f'snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_{suite}_{component}_binary-amd64_Packages').write_bytes(payload)
            plain = base / 'plaintext'
            plain.write_text(text)
            filename = f'snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_{suite}_InRelease'
            run('gpg', '--homedir', str(home), '--batch', '--yes', '--armor', '--clearsign', '--output', str(lists / filename), str(plain))
            profiles.append(suite + '\t' + hashlib.sha256((lists / filename).read_bytes()).hexdigest())
        (runtime / 'apt-snapshot-profile.tsv').write_text('\n'.join(profiles) + '\n')
        (store / 'indexes.sha256').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest() + '  ' + f.name + '\n' for f in sorted(lists.iterdir())))
        source = (ASSETS / 'apt-snapshot-lock.sh').read_text().replace('F6ECB3762474EDA9D21B7022871920D1991BC93C', fpr)
        source = source.replace('/usr/share/keyrings/ubuntu-archive-keyring.gpg', '/workspace/keyring.gpg')
        (runtime / 'apt-snapshot-lock.sh').write_text(source)
        script = r'''set -eu
mkdir -p /workspace
cp -a /fixture/runtime /fixture/keyring.gpg /fixture/store /workspace/
mkdir -p /opt/appforge-device/apt-snapshot-v1 /var/cache/apt/archives
cp -a /workspace/store /opt/appforge-device/apt-snapshot-v1/amd64-20261018T000000Z
printf 'working-node-preserve\n' > /tmp/active-node
printf 'user-project-preserve\n' > /tmp/user-project
sha256sum /tmp/active-node /tmp/user-project > /tmp/protected.sha256
export APPFORGE_APT_MANAGED_INSTALL=1 APPFORGE_R2_RELEASE_AUTHORIZED=1 APPFORGE_MODULE_ID=NODE_WEB APPFORGE_APT_SNAPSHOT=1
. /workspace/runtime/apt-snapshot-lock.sh
appforge_snapshot_apt_get update
appforge_snapshot_apt_get install -y --no-install-recommends appforge-r2-fixture
[ "$(/usr/local/bin/appforge-r2-fixture)" = 42 ]
appforge_snapshot_apt_get install -y --no-install-recommends appforge-r2-fixture
sha256sum -c /tmp/protected.sha256
printf '\n# tampered installed file\n' >> /usr/local/bin/appforge-r2-fixture
if appforge_snapshot_apt_get install -y --no-install-recommends appforge-r2-fixture; then echo 'TAMPER_ACCEPTED';exit 1;fi
sha256sum -c /tmp/protected.sha256
printf 'HOST_OFFLINE_INSTALL=PASS;HOST_OFFLINE_REPLAY=PASS;INSTALLED_TAMPER_DENIED=PASS\n'
'''
        result = run(*docker, 'run', '--rm', '--network', 'none', '--mount', f'type=bind,src={base},dst=/fixture,readonly',
                     IMAGE, 'sh', '-c', script, env=docker_env, text=True, timeout=120)
        print(result.stdout)
        assert 'APPFORGE_APT_NO_DOWNLOAD_VERIFIED:' in result.stdout
        assert 'APPFORGE_APT_LOCK_REUSED:' in result.stdout
        print(json.dumps(dict(synthetic_fixture=True, docker_network='none', image=IMAGE,
            apt_install='PASS', offline_replay='PASS', installed_tamper_denied='PASS',
            ubuntu_real_package_install='NOT_RUN', android_physical='NOT_RUN', production_mutations=0)))


if __name__ == '__main__':
    main()
