# Host-only opt-in integration proof. Requires Docker, public system CA and the exact local vendor archive.
from pathlib import Path
import subprocess,os,hashlib,json
import sys,argparse
parser=argparse.ArgumentParser()
parser.add_argument('workdir',type=Path)
parser.add_argument('--offline-only',action='store_true')
parser.add_argument('--inspect-classpath',type=Path)
parser.add_argument('--jdk',type=Path)
parser.add_argument('--seed-store',type=Path)
parser.add_argument('--prepare-network-none',action='store_true')
args=parser.parse_args()
repo=Path(__file__).resolve().parents[2]
w=args.workdir.resolve();assets=repo/'android-app/app/src/main/assets/device-build'
w.mkdir(parents=True,exist_ok=True)
(w/'apt.conf').write_text('Acquire::https::CaInfo \"/run/system-ca.crt\";\nAcquire::https::Verify-Peer \"true\";\nAcquire::https::Verify-Host \"true\";\n')
env=dict(os.environ)
for k in ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH']:env.pop(k,None)
docker=['docker','--host=unix:///var/run/docker.sock']
archive=w/'ubuntu-base-24.04.4-base-amd64.tar.gz';assert hashlib.sha256(archive.read_bytes()).hexdigest()=='c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58'
with archive.open('rb') as f:s=subprocess.run(docker+['import','-','appforge-apt-proof:24.04.4-amd64'],stdin=f,env=env,capture_output=True,text=True,check=True)
base=docker+['run','--rm','--mount',f'type=bind,src={w},dst=/proof','--mount',f'type=bind,src={assets},dst=/workspace/runtime,readonly','--mount','type=bind,src=/etc/ssl/certs/ca-certificates.crt,dst=/run/system-ca.crt,readonly','-e','LANG=C.UTF-8','-e','LC_ALL=C.UTF-8','-e','APT_CONFIG=/proof/apt.conf','-e','APPFORGE_APT_SNAPSHOT=1','-e','APPFORGE_APT_MANAGED_INSTALL=1','-e','APPFORGE_DOWNLOAD_TELEMETRY=/proof/real-events.json']
inspect_command=''
if args.inspect_classpath:
 assert args.jdk and args.jdk.is_dir()
 classpath=args.inspect_classpath.read_text().strip().split(':');destinations=[]
 for i,entry in enumerate(classpath):
  dest=f'/run/proof-classpath/cp{i}';destinations.append(dest)
  base.extend(['--mount',f'type=bind,src={Path(entry).resolve()},dst={dest},readonly'])
 base.extend(['--mount',f'type=bind,src={args.jdk.resolve()},dst=/run/proof-jdk,readonly'])
 inspect_command='/run/proof-jdk/bin/java -cp '+':'.join(destinations)+' com.appforge.studio.build.AptLockInspectHostKt /proof/production-preflight.json'
if args.seed_store:
 base.extend(['--mount',f'type=bind,src={args.seed_store.resolve()},dst=/run/seed-store,readonly'])
online=r'''set -eu
. /workspace/runtime/apt-download-telemetry.sh
export APPFORGE_MODULE_ID=ANDROID_CORE
appforge_apt_get update
appforge_apt_get install -y --no-install-recommends ca-certificates curl unzip zip xz-utils tar git file libstdc++6 zlib1g libpng16-16 fontconfig libfreetype6
export APPFORGE_MODULE_ID=PYTHON_ANDROID
appforge_apt_get update
appforge_apt_get install -y --no-install-recommends python3 python3-pip python3-venv python3.12 python3.12-minimal libpython3.12-minimal libpython3.12-stdlib
export APPFORGE_MODULE_ID=WINDOWS_NATIVE
appforge_apt_get update
appforge_apt_get install -y --no-install-recommends ca-certificates cmake ninja-build file binutils-mingw-w64-x86-64 gcc-mingw-w64-x86-64-posix g++-mingw-w64-x86-64-posix mingw-w64-x86-64-dev mingw-w64-tools
cp -a /opt/appforge-device/apt-snapshot-v1 /proof/real-store
cp -a /opt/appforge-device/apt-locks-v5 /proof/real-locks
dpkg-query -W -f='${Package}\t${Version}\t${Architecture}\n' | sort > /proof/real-online-installed.tsv
python3 --version
cmake --version
x86_64-w64-mingw32-gcc-posix --version
printf 'ONLINE_ACTUAL_MODULE_APT_SETS_PASS\n'
'''
if args.seed_store:online=online.replace('set -eu', 'set -eu\nmkdir -p /opt/appforge-device\ncp -a /run/seed-store /opt/appforge-device/apt-snapshot-v1',1)
if not args.offline_only:
 with (w/'real-online.log').open('w') as f:
  p=subprocess.run(base+(['--network=none'] if args.prepare_network_none else [])+['appforge-apt-proof:24.04.4-amd64','sh','-c',online],env=env,stdout=f,stderr=subprocess.STDOUT,timeout=900)
 print('online exit',p.returncode,flush=True);assert p.returncode==0
else:
 assert (w/'real-store').is_dir() and (w/'real-locks').is_dir()
# Same pristine, hash-verified production base. Docker network namespace has no network.
offline=r'''set -eu
mkdir -p /opt/appforge-device
cp -a /proof/real-store /opt/appforge-device/apt-snapshot-v1
cp -a /proof/real-locks /opt/appforge-device/apt-locks-v5
. /workspace/runtime/apt-download-telemetry.sh
export APPFORGE_MODULE_ID=ANDROID_CORE
appforge_apt_get update
appforge_apt_get install -y --no-install-recommends ca-certificates curl unzip zip xz-utils tar git file libstdc++6 zlib1g libpng16-16 fontconfig libfreetype6
export APPFORGE_MODULE_ID=PYTHON_ANDROID
appforge_apt_get update
appforge_apt_get install -y --no-install-recommends python3 python3-pip python3-venv python3.12 python3.12-minimal libpython3.12-minimal libpython3.12-stdlib
export APPFORGE_MODULE_ID=WINDOWS_NATIVE
appforge_apt_get update
appforge_apt_get install -y --no-install-recommends ca-certificates cmake ninja-build file binutils-mingw-w64-x86-64 gcc-mingw-w64-x86-64-posix g++-mingw-w64-x86-64-posix mingw-w64-x86-64-dev mingw-w64-tools
dpkg-query -W -f='${Package}\t${Version}\t${Architecture}\n' | sort > /proof/real-offline-installed.tsv
cmp /proof/real-online-installed.tsv /proof/real-offline-installed.tsv
[ -z "$(dpkg --audit)" ]
python3 --version
cmake --version
x86_64-w64-mingw32-gcc-posix --version
printf 'DOCKER_NETWORK_NONE_ACTUAL_APT_INSTALL_PASS\n'
'''
offline+='\n'+inspect_command+'\n'
with (w/'real-offline.log').open('w') as f:
 p=subprocess.run(base+['--network=none','appforge-apt-proof:24.04.4-amd64','sh','-c',offline],env=env,stdout=f,stderr=subprocess.STDOUT,timeout=420)
print('offline exit',p.returncode,flush=True);assert p.returncode==0
(w/'real-install-result.json').write_text(json.dumps({'rootfsSha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'architecture':'amd64','snapshot':'20261018T000000Z','modules':['ANDROID_CORE','PYTHON_ANDROID','WINDOWS_NATIVE'],'networkNoneInstall':'PASS','productionKotlinPreflight':'PASS' if inspect_command else 'NOT_RUN','sourceSha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [assets/'apt-snapshot-lock.sh',assets/'apt-download-telemetry.sh',assets/'apt-snapshot-profile.tsv']},'onlineInstalledSha256':hashlib.sha256((w/'real-online-installed.tsv').read_bytes()).hexdigest(),'offlineInstalledSha256':hashlib.sha256((w/'real-offline-installed.tsv').read_bytes()).hexdigest(),'scope':'APT package subsets only, not full Android SDK/Chaquopy or Windows/Android device acceptance'},indent=2)+'\n')
