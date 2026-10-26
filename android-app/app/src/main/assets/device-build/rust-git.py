#!/usr/bin/env python3
"""Pinned Git bundles -> Cargo's own directory-source normalization.
Network transfers belong to VerifiedModuleDownload. Git is file-protocol-only;
no GitHub API, Git network fetch, hooks, source build or credential lookup here.
Cargo metadata/vendor execution requires the caller's separate offline consent.
"""
import hashlib,json,os,pathlib,re,shutil,subprocess,tarfile,tempfile,tomllib,urllib.parse

def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(65536),b''):h.update(b)
    return h.hexdigest()

def source(value):
    if not isinstance(value,str) or not value.startswith('git+https://'):raise ValueError('Only public HTTPS Git source identities supported')
    u=urllib.parse.urlsplit(value[4:])
    if not u.hostname or u.username or u.password or u.port not in (None,443) or not re.fullmatch('[a-f0-9]{40}',u.fragment) or '%' in u.path or '..' in u.path.split('/') or '\\' in u.path:
        raise ValueError('Unsafe/unpinned Git source identity')
    query=urllib.parse.parse_qs(u.query,keep_blank_values=True,strict_parsing=True)
    if len(query)>1 or any(k not in ('rev','tag','branch') or len(v)!=1 or not re.fullmatch(r'[A-Za-z0-9_./-]{1,100}',v[0]) for k,v in query.items()):raise ValueError('Unsupported Git selector')
    return urllib.parse.urlunsplit((u.scheme,u.netloc,u.path,'','')),u.fragment,{k:v[0] for k,v in query.items()}

def pins(root,lock):
    required={p['source'] for p in lock.get('package',[]) if str(p.get('source','')).startswith('git+')}
    if not required:return []
    f=root/'appforge-rust-sources.json'
    if f.is_symlink() or not f.is_file() or not 1<=f.stat().st_size<=128*1024:raise ValueError('Only checksum-locked registry or SHA-256-pinned Git bundle sources are accepted; explicit bundle sources missing')
    j=json.loads(f.read_text())
    if j.get('schema')!=1 or not isinstance(j.get('sources'),list) or len(j['sources'])>64:raise ValueError('Unknown Git source pin schema/bound')
    result={}
    for p in j['sources']:
        if set(p) not in ({'source','url','sha256'},{'source','path','sha256'}):raise ValueError('Unknown Git bundle fields')
        url,commit,_=source(p['source'])
        if len(p['source'])>2048 or not re.fullmatch('[a-f0-9]{64}',p['sha256']):raise ValueError('Unsafe Git source/checksum')
        item=dict(kind='git-bundle',name='git-'+p['sha256'][:16],version=commit,sha256=p['sha256'],source=p['source'])
        if 'url' in p:
            u=urllib.parse.urlsplit(p['url'])
            if len(p['url'])>2048 or u.scheme!='https' or not u.hostname or u.username or u.password or u.query or u.fragment or u.port not in (None,443) or '\\' in u.path:raise ValueError('Unsafe Git bundle HTTPS URL')
            item['url']=p['url']
        else:
            name=p['path']
            if not isinstance(name,str):raise ValueError('Local Git bundle path must be text')
            path=pathlib.PurePosixPath(name)
            if not isinstance(name,str) or not name or len(name)>512 or path.is_absolute() or '..' in path.parts or '\\' in name or any(x in path.parts for x in ('target','.git','node_modules','appforge-rust')):raise ValueError('Unsafe local Git bundle path')
            local=root/path
            for component in [local,*local.parents]:
                if component==root:break
                if component.is_symlink():raise ValueError('Local Git bundle symlink rejected')
            if not local.is_file() or local.stat().st_size>256*1024*1024 or not local.resolve().is_relative_to(root) or sha(local)!=p['sha256']:raise ValueError('Local Git bundle missing/unsafe')
            item['localPath']=name
        if p['source'] in result:raise ValueError('Duplicate Git source pin')
        result[p['source']]=item
    if set(result)!=required:raise ValueError('Git bundle pins must exactly match Cargo.lock sources')
    return [result[k] for k in sorted(result)]

def config(root,vendor):
    lock=tomllib.loads((root/'Cargo.lock').read_text());result=[]
    for p in pins(root,lock):
        url,commit,query=source(p['source']);key=p['source'].split('#',1)[0]
        result.extend(['--config','source.'+json.dumps(key)+'.git='+json.dumps(url)])
        for name,value in query.items():result.extend(['--config','source.'+json.dumps(key)+'.'+name+'='+json.dumps(value)])
        result.extend(['--config','source.'+json.dumps(key)+'.replace-with="appforge-vendor"'])
    return result

def environment(home,mappings):
    env=dict(os.environ,CARGO_HOME=str(home),CARGO_NET_GIT_FETCH_WITH_CLI='true',CARGO_NET_OFFLINE='false',GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL='/dev/null',GIT_ATTR_NOSYSTEM='1',GIT_TERMINAL_PROMPT='0',GIT_NO_REPLACE_OBJECTS='1')
    # No inherited credential/helper/transport configuration. Keep proxy/CA trust.
    for k in list(env):
        if k.startswith('GIT_CONFIG_') and k not in ('GIT_CONFIG_NOSYSTEM','GIT_CONFIG_GLOBAL') or k in ('GIT_SSH','GIT_SSH_COMMAND','GIT_ASKPASS','SSH_ASKPASS','GIT_PROXY_COMMAND','GIT_DIR','GIT_WORK_TREE','GIT_OBJECT_DIRECTORY','GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_CONFIG','GIT_EXEC_PATH','GIT_TEMPLATE_DIR'):env.pop(k,None)
    for key in list(env):
        if key.startswith('CARGO_') or key in ('RUSTC_WRAPPER','RUSTC_WORKSPACE_WRAPPER','RUSTFLAGS','CARGO_ENCODED_RUSTFLAGS'):env.pop(key,None)
    env.update(CARGO_HOME=str(home),CARGO_NET_GIT_FETCH_WITH_CLI='true',CARGO_NET_OFFLINE='false')
    settings=[('protocol.allow','never'),('protocol.file.allow','always'),('protocol.https.allow','never'),('protocol.http.allow','never'),('protocol.ssh.allow','never'),('core.hooksPath','/dev/null'),('fetch.fsckObjects','true'),('transfer.fsckObjects','true'),('credential.helper','')]
    settings.extend(('url.'+path.as_uri()+'.insteadOf',url) for url,path in mappings)
    env['GIT_CONFIG_COUNT']=str(len(settings))
    for i,(key,value) in enumerate(settings):env['GIT_CONFIG_KEY_'+str(i)]=key;env['GIT_CONFIG_VALUE_'+str(i)]=value
    return env

def validate_tree(tree,allowed):
    scope={'__name__':'trusted_layout','__file__':str(pathlib.Path(__file__).with_name('rust-layout.py'))}
    exec(compile(pathlib.Path(scope['__file__']).read_bytes(),scope['__file__'],'exec'),scope)
    layout=scope['resolve'](tree,False)
    def inspect(value):
        if isinstance(value,dict):
            if 'git' in value and value['git'] not in allowed:raise ValueError('Unpinned transitive Git source')
            for child in value.values():inspect(child)
        elif isinstance(value,list):
            for child in value:inspect(child)
    for manifest in layout['packages'].values():inspect(manifest)

def prepare(root,cache,destination,registry_builder,prefix,consent):
    if not consent:raise ValueError('Git metadata preparation requires explicit offline source-metadata consent')
    if destination.exists() or destination.is_symlink():raise ValueError('Fresh Git vendor destination required')
    lock=tomllib.loads((root/'Cargo.lock').read_text());items=pins(root,lock)
    if not items:raise ValueError('Git sources missing')
    if shutil.disk_usage(destination.parent).free<256*1024*1024:raise ValueError('Insufficient Git staging space')
    cargo=prefix/'bin/cargo'
    if not cargo.is_file():raise ValueError('Verified Cargo prefix required')
    before=sha(root/'Cargo.lock');allowed={source(p['source'])[0] for p in items}
    with tempfile.TemporaryDirectory(prefix='cargo-git-',dir=destination.parent) as tmp:
        tmp=pathlib.Path(tmp);home=tmp/'home';home.mkdir();mappings=[];repositories={}
        for i,p in enumerate(items):
            bundle=cache/(p['sha256']+'.bundle')
            if not bundle.exists() and 'localPath' in p:bundle=root/p['localPath']
            if bundle.is_symlink() or not bundle.is_file() or bundle.stat().st_size>256*1024*1024 or sha(bundle)!=p['sha256']:raise ValueError('Git bundle SHA-256 mismatch')
            repo=tmp/('repo-'+str(i));env=environment(home,[])
            subprocess.run(['git','clone','--bare','--no-local',str(bundle),str(repo)],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,timeout=120)
            if sha(bundle)!=p['sha256']:raise ValueError('Git bundle changed during clone')
            commit=source(p['source'])[1]
            subprocess.run(['git','--no-replace-objects','--git-dir='+str(repo),'fsck','--strict','--no-reflogs'],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,timeout=120)
            kind=subprocess.check_output(['git','--no-replace-objects','--git-dir='+str(repo),'cat-file','-t',commit],env=env,timeout=30).strip()
            if kind!=b'commit':raise ValueError('Git bundle does not contain locked commit')
            entries=subprocess.check_output(['git','--no-replace-objects','--git-dir='+str(repo),'ls-tree','-r','-z',commit],env=env,timeout=30)
            if len(entries)>4*1024*1024:raise ValueError('Git tree index bound')
            files=[];seen=set()
            for entry in entries.split(b'\0'):
                if not entry:continue
                meta,name=entry.split(b'\t',1);mode,kind,blob=meta.split();name=name.decode('utf-8');path=pathlib.PurePosixPath(name)
                if mode not in (b'100644',b'100755') or kind!=b'blob' or not re.fullmatch(b'[a-f0-9]{40}',blob) or path.is_absolute() or '..' in path.parts or '.git' in path.parts or '\\' in name or name in seen:raise ValueError('Git tree path/link/submodule rejected')
                seen.add(name);files.append((path,blob,mode))
            if len(files)>20000:raise ValueError('Git tree file bound')
            # Read actual Git blobs, not `git archive`: export-ignore/subst
            # attributes must not hide a Cargo manifest from validation.
            tree=tmp/('tree-'+str(i));tree.mkdir();total=0
            with subprocess.Popen(['git','--no-replace-objects','--git-dir='+str(repo),'cat-file','--batch'],env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL) as cat:
                for path,blob,mode in files:
                    cat.stdin.write(blob+b'\n');cat.stdin.flush();header=cat.stdout.readline(256).split()
                    if len(header)!=3 or header[:2]!=[blob,b'blob'] or not header[2].isdigit():raise ValueError('Git object header mismatch')
                    size=int(header[2]);total+=size
                    if total>256*1024*1024:raise ValueError('Git tree byte bound')
                    if shutil.disk_usage(tree).free<size+64*1024*1024:raise ValueError('Insufficient Git object staging space')
                    file=tree/path;file.parent.mkdir(parents=True,exist_ok=True)
                    with file.open('xb') as out:
                        left=size
                        while left:
                            chunk=cat.stdout.read(min(left,65536))
                            if not chunk:raise ValueError('Truncated Git object')
                            out.write(chunk);left-=len(chunk)
                    if cat.stdout.read(1)!=b'\n':raise ValueError('Git object boundary mismatch')
                cat.stdin.close()
                if cat.wait(timeout=30):raise ValueError('Git object extraction failed')
            validate_tree(tree,allowed)
            url=source(p['source'])[0]
            if url in repositories:
                # Multiple locked revisions must share one private object store.
                subprocess.run(['git','--git-dir='+str(repositories[url]),'fetch','--no-tags',str(repo),commit+':refs/appforge/'+commit],env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,timeout=120)
            else:
                repositories[url]=repo;mappings.append((url,repo))
        registry=tmp/'registry';registry_builder(root,cache,registry)
        env=environment(home,mappings);env['PATH']=str(prefix/'bin')+os.pathsep+env.get('PATH','');env['RUSTC']=str(prefix/'bin/rustc')
        for directory in (home,*home.parents):
            if any((directory/'.cargo'/name).exists() for name in ('config','config.toml')):raise ValueError('Ancestor Cargo configuration is not permitted during Git metadata preparation')
        args=[str(cargo),'vendor','--manifest-path',str(root/'Cargo.toml'),'--locked','--respect-source-config',str(destination),'--config','source.crates-io.replace-with="appforge-registry"','--config','source.appforge-registry.directory='+json.dumps(str(registry)),'--config','build.rustc='+json.dumps(str(prefix/'bin/rustc')),'--config','build.rustc-wrapper=""','--config','build.rustc-workspace-wrapper=""']
        # Git can fetch only verified local object stores; registry bytes are
        # preverified directory sources. Android caller additionally requires
        # every network absent throughout this separately consented phase.
        subprocess.run(args,cwd=home,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,check=True,timeout=300)
        if before!=sha(root/'Cargo.lock'):raise ValueError('Cargo.lock changed during Git normalization')
    return dict(schema=1,state='GIT_VENDOR_VERIFIED_NOT_OFFLINE_BUILD_READY')
