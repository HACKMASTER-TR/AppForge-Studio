import tempfile,pathlib,zipfile,threading,http.server,subprocess,os,json,hashlib,shutil,time
# Generates an isolated loopback repository; no external project scripts or secrets.
# HTTP is allowed only for this local fixture repository, never production downloads.
base=pathlib.Path(__file__).resolve().parents[2]
r=pathlib.Path(tempfile.mkdtemp(prefix='af-gradle-observer-',dir=os.environ.get('APPFORGE_TMPDIR',tempfile.gettempdir())));repo=r/'repo';pkg=repo/'test/verified/1.0';pkg.mkdir(parents=True)
with zipfile.ZipFile(pkg/'verified-1.0.jar','w') as z:z.writestr('proof.txt','verified dependency' * 65536)
(pkg/'verified-1.0.pom').write_text('<project><modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>verified</artifactId><version>1.0</version></project>')
class Server(http.server.SimpleHTTPRequestHandler):
 def __init__(self,*a,**kw):super().__init__(*a,directory=str(repo),**kw)
 def log_message(self,*a):pass
 def copyfile(self,source,outputfile):
  while chunk:=source.read(32768):
   outputfile.write(chunk);outputfile.flush();time.sleep(0.03)
s=http.server.ThreadingHTTPServer(('127.0.0.1',0),Server);threading.Thread(target=s.serve_forever,daemon=True).start()
p=r/'project';p.mkdir();(p/'settings.gradle').write_text("rootProject.name='download-byte-fixture'")
(p/'build.gradle').write_text("repositories {maven {url='http://127.0.0.1:%s';allowInsecureProtocol=true}}\nconfigurations {proof}\ndependencies {proof 'test:verified:1.0'}\ntasks.register('resolveProof') {doLast {assert configurations.proof.singleFile.length()>0}}"%s.server_port)
g=p/'gradle';g.mkdir();(g/'verification-metadata.xml').write_text('<verification-metadata><configuration><verify-metadata>true</verify-metadata><verify-signatures>false</verify-signatures></configuration><components><component group="test" name="verified" version="1.0">'+''.join('<artifact name="%s"><sha256 value="%s" origin="local generated fixture"/></artifact>'%(f.name,hashlib.sha256(f.read_bytes()).hexdigest()) for f in pkg.iterdir())+'</component></components></verification-metadata>')
env=dict(os.environ,JAVA_HOME=os.environ['JAVA_HOME'],GRADLE_USER_HOME=str(r/'cache'),APPFORGE_DOWNLOAD_TELEMETRY=str(r/'event.json'));cmd=[os.environ.get('GRADLE_BIN','gradle'),'--no-daemon','--console=plain','--max-workers=1','-I',str(base/'android-app/app/src/main/assets/device-build/gradle-download-telemetry.init.gradle'),'resolveProof']
observed=[];stop=threading.Event()
def observe():
 while not stop.wait(.01):
  try:observed.append(json.loads((r/'event.json').read_text()))
  except (FileNotFoundError,ValueError):pass
threading.Thread(target=observe,daemon=True).start()
for phase,flags in [('empty-offline',['--offline']),('online',[]),('cached-offline',['--offline'])]:
 result=subprocess.run(cmd+flags,cwd=p,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(r/(phase+'.log')).write_text(result.stdout);print(phase,result.returncode,flush=True);assert (result.returncode!=0 if phase=='empty-offline' else result.returncode==0),result.stdout[-3000:]
 if phase=='online':
  event=json.loads((r/'event.json').read_text());assert event['finished'] and event['bytes']==event['total'] and event['bytes']>0;print('actual Gradle typed event',event,flush=True);s.shutdown()
stop.set();assert any(not e['finished'] and 0<e['bytes']<(e.get('total') or 0) for e in observed), 'No typed intermediate byte event';print('PASS live typed byte progress and offline fixture',r,flush=True)
