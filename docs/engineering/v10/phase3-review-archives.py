import json,hashlib,urllib.request,tarfile,struct
from pathlib import Path
pins=json.loads(Path('quality/tools/v10-oss-pins.json').read_text());cache=Path('.appforge/tmp/v10/phase3/review-cache');cache.mkdir(exist_ok=True)
rows=[]
for tool,pin in pins.items():
 a=pin['artifacts']['x86_64'];row={'tool':tool,'version':pin['version'],'host':'Linux x86_64 only','url':a['url'],'expected_sha256':a['sha256'],'executed':False,'license_closure_reviewed':False}
 try:
  with urllib.request.urlopen(a['url'],timeout=60) as response:body=response.read(100*1024*1024+1)
  sha=hashlib.sha256(body).hexdigest();row.update(observed_sha256=sha,archive_bytes=len(body),matches_pin=sha==a['sha256'])
  if sha!=a['sha256'] or len(body)>100*1024*1024:raise ValueError('ARCHIVE_PIN_OR_SIZE_REJECTED')
  path=cache/a['name'];path.write_bytes(body);row['notice_files']=[]
  with tarfile.open(path,'r:gz') as tar:
   for index,entry in enumerate(tar):
    if index>=5000:raise ValueError('ENTRY_LIMIT')
    if not entry.isfile():continue
    if any(x in entry.name.upper() for x in ('LICENSE','NOTICE','COPYING')):
     if entry.size>8*1024*1024:raise ValueError('NOTICE_SIZE_LIMIT')
     data=tar.extractfile(entry).read();row['notice_files'].append({'path':entry.name,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)})
    if entry.name==tool:
     if entry.size>100*1024*1024:raise ValueError('BINARY_SIZE_LIMIT')
     binary=tar.extractfile(entry).read();row['binary_sha256']=hashlib.sha256(binary).hexdigest()
     row['elf_machine']=struct.unpack('<H',binary[18:20])[0] if binary[:6]==b'\x7fELF\x02\x01' else None
  row['status']='ARCHIVE_VERIFIED_EXECUTION_BLOCKED';row['blocker']='Transitive module license/notice closure and provenance attestation not reviewed; no supported local Linux x86_64 execution host. Archive hash alone is insufficient.'
 except Exception as e:row.update(status='BLOCKED',error=type(e).__name__)
 rows.append(row)
 Path('docs/engineering/v10/phase3-security-archive-review.json').write_text(json.dumps({'schema_version':1,'scope':'Static archive inspection only; no scanner invocation or SBOM generation','artifacts':rows},indent=2)+'\n')
 print(tool,row['status'],flush=True)
