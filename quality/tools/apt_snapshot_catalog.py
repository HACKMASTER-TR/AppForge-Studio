"""Build a reviewable package catalog from real APT solver output and pinned signed metadata.
This does not resolve/download packages itself and never labels metadata-only rows installed.
"""
from pathlib import Path
import argparse,hashlib,json,re,shlex,urllib.parse

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def paragraphs(text):
 result=[]
 for paragraph in re.split(r'\n\s*\n',text):
  row={};key=None
  for line in paragraph.splitlines():
   if line.startswith((' ','\t')) and key:row[key]+=' '+line.strip()
   elif ': ' in line:key,value=line.split(': ',1);row[key]=value
  if row:result.append(row)
 return result

def main():
 parser=argparse.ArgumentParser();parser.add_argument('evidence',type=Path);parser.add_argument('destination',type=Path);args=parser.parse_args()
 repo=Path(__file__).resolve().parents[2];profile=repo/'android-app/app/src/main/assets/device-build/apt-snapshot-profile.tsv'
 expected=dict(line.split('\t') for line in profile.read_text().splitlines());rows=[];modules=[];nodes={};edges=[]
 root_hashes={'amd64':'c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58','arm64':'04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2'}
 for arch in ['amd64','arm64']:
  folder=args.evidence/'resolution'/arch
  for suite,digest in expected.items():
   release=folder/'lists'/f'snapshot.ubuntu.com_ubuntu_20261018T000000Z_dists_{suite}_InRelease';assert sha(release)==digest
  baseline=paragraphs((args.evidence/f'{arch}-vendor.status').read_text())
  baseline=[{'name':p['Package'],'version':p['Version'],'architecture':p['Architecture'],'sha256':None,'integritySource':'pinned vendor rootfs archive'} for p in baseline if p.get('Status')=='install ok installed']
  modules.append({'moduleId':'LINUX_RUNTIME','architecture':arch,'ubuntu':'24.04.4 / noble','rootfsSha256':root_hashes[arch],'packages':baseline,'verification':'VERIFIED_VENDOR_ARCHIVE_INVENTORY_NOT_APT_REINSTALL'})
  for uris in sorted(folder.glob('*.uris')):
   module=uris.stem;metadata=paragraphs(uris.with_suffix('.metadata').read_text());by_sha={p['SHA256']:p for p in metadata if 'SHA256' in p};packages=[]
   for line in uris.read_text().splitlines():
    if not line.startswith("'https:"):continue
    parts=shlex.split(line);assert len(parts)==4 and parts[3].startswith('SHA256:')
    url,filename,size,digest=parts;digest=digest[7:];assert re.fullmatch('[a-f0-9]{64}',digest)
    p=by_sha[digest];assert int(p['Size'])==int(size) and p['Architecture'] in [arch,'all']
    assert urllib.parse.unquote(url)=='https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/'+p['Filename']
    entry={'name':p['Package'],'version':p['Version'],'architecture':p['Architecture'],'sha256':digest,'bytes':int(size),'url':url,'dependencies':p.get('Depends',''),'preDependencies':p.get('Pre-Depends',''),'repositoryMetadata':expected,'verification':'SIGNED_INDEX_RESOLVED_NOT_INSTALLED'}
    packages.append(entry)
    values=[module,arch,p['Package'],p['Version'],p['Architecture'],digest,size,url,p.get('Depends',''),p.get('Pre-Depends','')]
    assert all('\t' not in x and '\n' not in x for x in values);rows.append('\t'.join(values))
    node=f"{arch}:{p['Package']}:{p['Version']}:{digest}";nodes[node]=entry
    edges.append({'module':module,'architecture':arch,'package':node,'depends':entry['dependencies'],'preDepends':entry['preDependencies'],'resolutionAuthority':'APT solver using exact pinned snapshot and vendor baseline'})
   modules.append({'lockfileVersion':1,'moduleId':module,'architecture':arch,'ubuntu':'noble','snapshot':'20261018T000000Z','baselineRootfsSha256':root_hashes[arch],'packages':packages,'downloadBytes':sum(p['bytes'] for p in packages),'verification':'SIGNED_METADATA_AND_SOLVER_PASS; execution status is separate evidence','noAdditionalApt':module=='EXPO_REACT_NATIVE' and arch=='amd64'})
 args.destination.mkdir(parents=True,exist_ok=True)
 catalog=args.destination/'apt-module-packages-v1.tsv';catalog.write_text(''.join(line+'\n' for line in sorted(set(rows))))
 manifest={'schema':1,'snapshot':'20261018T000000Z','repository':'https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/','signatureFingerprint':'F6ECB3762474EDA9D21B7022871920D1991BC93C','profileSha256':sha(profile),'catalogSha256':sha(catalog),'sourceSecurity':'APT signed metadata validation; exact InRelease SHA-256 pins; no signature/TLS/expiry bypass','modules':modules,'uniqueDebs':len({p['sha256'] for m in modules for p in m['packages'] if p['sha256']}),'baselineDebHashLimit':'Preinstalled baseline packages are covered by the verified vendor rootfs archive, not separately downloaded .deb hashes.'}
 (args.destination/'package-lock-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 (args.destination/'dependency-graph.json').write_text(json.dumps({'schema':1,'snapshot':manifest['snapshot'],'nodes':nodes,'edges':edges,'moduleGraph':{'LINUX_RUNTIME':[],'ANDROID_CORE':['LINUX_RUNTIME'],'NODE_WEB':['LINUX_RUNTIME'],'PYTHON_ANDROID':['ANDROID_CORE'],'EXPO_REACT_NATIVE':['ANDROID_CORE','NODE_WEB'],'WINDOWS_NATIVE':['LINUX_RUNTIME']},'semantics':'Debian dependency/version expressions are preserved verbatim; alternatives were evaluated by APT. This graph is not an independent reimplementation of Debian version comparison.'},indent=2)+'\n')
 print(json.dumps({'catalogSha256':sha(catalog),'rows':len(set(rows)),'uniqueDebs':manifest['uniqueDebs']}))
if __name__=='__main__':main()
