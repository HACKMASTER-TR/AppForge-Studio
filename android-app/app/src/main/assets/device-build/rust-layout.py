#!/usr/bin/env python3
"""Bounded Cargo layout inspection. Reads data only, never invokes Cargo or Git."""
import copy,json,pathlib,re,tomllib

def read(path):
    if path.is_symlink() or not path.is_file() or not 1<=path.stat().st_size<=2*1024*1024:
        raise ValueError('Unsafe/missing Cargo manifest')
    return tomllib.loads(path.read_text())

def resolve(root,selection_required=True):
    if root.is_symlink() or root.resolve()!=root:raise ValueError('Unsafe Cargo root')
    top=read(root/'Cargo.toml');workspace=top.get('workspace',{})
    if not isinstance(workspace,dict):raise ValueError('Invalid Cargo workspace')
    packages={};names={};pending=[]
    def bounded(path):
        path=path.absolute()
        resolved=path.resolve()
        if not resolved.is_relative_to(root):raise ValueError('Cargo path escapes reviewed root')
        current=path
        while current!=root:
            if current.is_symlink():raise ValueError('Cargo member/path symlink')
            current=current.parent
            if not current.is_relative_to(root) and current!=root:break
        # Check the normalized path too (including symlinks hidden before ..).
        for part in resolved.relative_to(root).parts:
            if part in ('target','node_modules','.git','appforge-rust'):raise ValueError('Cargo package in ignored/generated directory')
        return resolved
    if 'package' in top:pending.append(root)
    excludes=workspace.get('exclude',[])
    if not isinstance(excludes,list) or not all(isinstance(x,str) for x in excludes):raise ValueError('Invalid workspace exclusions')
    members=workspace.get('members',[])
    if not isinstance(members,list) or len(members)>512:raise ValueError('Workspace member bound')
    for pattern in members:
        if not isinstance(pattern,str) or not pattern or '\\' in pattern or pathlib.PurePosixPath(pattern).is_absolute() or '..' in pathlib.PurePosixPath(pattern).parts:
            raise ValueError('Unsafe workspace member pattern')
        found=0
        for member in root.glob(pattern):
            found+=1
            if found>512:raise ValueError('Workspace member expansion bound')
            member=bounded(member);relative=member.relative_to(root)
            if any(relative.match(x) for x in excludes):continue
            pending.append(member)
        if not found:raise ValueError('Workspace member missing')
    while pending:
        directory=bounded(pending.pop(0))
        if directory in packages:continue
        if len(packages)>=512:raise ValueError('Cargo local package bound')
        manifest=copy.deepcopy(read(directory/'Cargo.toml'))
        package=manifest.get('package')
        if not isinstance(package,dict):raise ValueError('Cargo member has no package')
        for key,value in list(package.items()):
            if isinstance(value,dict) and value.get('workspace') is True:
                inherited=workspace.get('package',{}).get(key)
                if inherited is None:raise ValueError('Missing workspace package inheritance')
                package[key]=copy.deepcopy(inherited)
        name=package.get('name');version=package.get('version')
        if not isinstance(name,str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',name) or not isinstance(version,str) or not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?',version):
            raise ValueError('Invalid Cargo package identity')
        if name in names:raise ValueError('Ambiguous duplicate Cargo package name')
        names[name]=directory;packages[directory]=manifest
        declared=package.get('workspace')
        if declared is not None and (not isinstance(declared,str) or bounded(directory/declared)!=root):raise ValueError('Foreign workspace root')
        sections=[manifest.get(k,{}) for k in ('dependencies','dev-dependencies','build-dependencies')]
        targets=manifest.get('target',{})
        if not isinstance(targets,dict):raise ValueError('Invalid Cargo target dependencies')
        for target in targets.values():
            if not isinstance(target,dict):raise ValueError('Invalid Cargo target table')
            sections.extend(target.get(k,{}) for k in ('dependencies','dev-dependencies','build-dependencies'))
        for dependencies in sections:
            if not isinstance(dependencies,dict):raise ValueError('Invalid dependency table')
            for key,value in list(dependencies.items()):
                if not isinstance(value,dict):continue
                base=directory
                if value.get('workspace') is True:
                    inherited=workspace.get('dependencies',{}).get(key)
                    if inherited is None:raise ValueError('Missing workspace dependency inheritance')
                    inherited={'version':inherited} if isinstance(inherited,str) else copy.deepcopy(inherited)
                    if not isinstance(inherited,dict) or set(value)-{'workspace','features','optional','default-features'}:raise ValueError('Unsupported workspace dependency override')
                    features=inherited.get('features',[])+value.get('features',[])
                    inherited.update({k:v for k,v in value.items() if k not in ('workspace','features')})
                    if features:inherited['features']=features
                    dependencies[key]=value=inherited;base=root
                if 'path' in value:
                    path=value['path']
                    if not isinstance(path,str) or pathlib.PurePath(path).is_absolute() or '\\' in path:raise ValueError('Unsafe local Cargo dependency')
                    pending.append(bounded(base/path))
                if 'registry' in value or 'registry-index' in value:raise ValueError('Unreviewed alternate Cargo registry')
        for section in ('patch','replace'):
            if manifest.get(section):raise ValueError('Cargo patch/replace needs explicit source policy')
        # Bind custom source paths too, including build.rs; none may leave the package.
        candidates=[]
        if isinstance(manifest.get('lib'),dict):candidates.append(manifest['lib'].get('path','src/lib.rs'))
        for kind in ('bin','example','test','bench'):
            for target in manifest.get(kind,[]):
                if not isinstance(target,dict):raise ValueError('Invalid Cargo target declaration')
                if 'path' in target:candidates.append(target['path'])
        build=package.get('build')
        if build not in (None,False):candidates.append(build)
        for path in candidates:
            if not isinstance(path,str) or pathlib.PurePath(path).is_absolute() or '\\' in path or not bounded(directory/path).is_relative_to(directory):raise ValueError('Cargo source path escapes package')
    if not packages:raise ValueError('No Cargo packages')
    selector=root/'appforge-rust-project.json'
    if selector.exists() or selector.is_symlink():
        if selector.is_symlink() or not selector.is_file() or not 1<=selector.stat().st_size<=4096:raise ValueError('Unsafe Cargo selection')
        selected=json.loads(selector.read_text())
        if set(selected)!={'schema','package'} or selected['schema']!=1 or selected['package'] not in names:raise ValueError('Invalid/unknown selected Cargo package')
        directory=names[selected['package']]
    elif root in packages:directory=root
    elif not selection_required:directory=names[sorted(names)[0]]
    else:raise ValueError('Virtual workspace requires explicit appforge-rust-project.json package selection')
    return dict(directory=directory,manifest=packages[directory],packages=packages,workspace=workspace)
