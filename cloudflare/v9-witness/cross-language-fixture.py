#!/usr/bin/env python3
"""TEST ONLY: generate genuine signed Ed25519 Phase3G fixture for JS parity."""
import base64
from contextlib import ExitStack
from datetime import datetime,timedelta,timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from unittest.mock import patch

tools=Path(os.environ.get('APPFORGE_REFERENCE_TOOLS', Path(__file__).resolve().parents[2]/'quality/tools'))
if not (tools/'appforge_phase3g_integrated_v9.py').is_file():
    raise RuntimeError('real Phase 3G reference source missing; do not skip parity')
sys.path.insert(0,str(tools))
import appforge_phase3g_integrated_v9 as g

def call(*args):
    return subprocess.run(['openssl',*map(str,args)],check=True,capture_output=True,timeout=20).stdout

def sign(priv,domain,payload,dir):
    path=dir/'msg';path.write_bytes(domain+g.crypto.canonical(payload))
    return base64.b64encode(call('pkeyutl','-sign','-inkey',priv,'-rawin','-in',path)).decode('ascii')

with tempfile.TemporaryDirectory(prefix='appforge-v9-js-parity-') as d:
    root=Path(d)
    k={}
    for name in ['root','release']:
        priv=root/(name+'.pem')
        call('genpkey','-algorithm','ED25519','-out',priv)
        der=call('pkey','-in',priv,'-pubout','-outform','DER')
        k[name]={'priv':priv,'der':der,'pin':g.crypto.spki(der),
                 'entry':{'keyid':g.crypto.spki(der),'spki_der_b64':base64.b64encode(der).decode('ascii')}}
    ev={'schema':1,'stage':'OFFLINE_EVIDENCE_ONLY',
        'snapshot':g.crypto.SNAPSHOT,'original_objects_verified':218,
        'original_deb_objects_verified':214,'ubuntu_inrelease_verified':4,
        'signed_packages_indexes_verified':32,'distinct_content_addressed_objects':248,
        'distinct_content_addressed_bytes':650832480,
        'appforge_ed25519_release_signature':'NOT_PRESENT',
        'r2_upload_allowed':False,'public_distribution_allowed':False,
        'android_client_enabled':False,'apt_ready':False,'all_engines_ready':False}
    evraw=g.crypto.canonical(ev)
    evidence=root/'evidence.json';evidence.write_bytes(evraw)
    evidence_pin=hashlib.sha256(evraw).hexdigest()
    now=datetime.now(timezone.utc).replace(microsecond=0)
    stamp=lambda x:x.strftime('%Y-%m-%dT%H:%M:%SZ')
    issued=stamp(now-timedelta(days=1));expires=stamp(now+timedelta(days=20))
    rp={'schema':1,'role':'appforge-v9-root','version':1,
        'issued_at':issued,'expires_at':expires,'root_key':k['root']['entry'],
        'release_keys':[k['release']['entry']],'release_threshold':1}
    lp={'schema':1,'role':'appforge-v9-release','root_version':1,
        'sequence':1,'snapshot':g.crypto.SNAPSHOT,'evidence_sha256':evidence_pin,
        'issued_at':issued,'expires_at':expires,'r2_upload_allowed':False,
        'public_distribution_allowed':False,'android_client_enabled':False}
    rootdoc={'payload':rp,'signatures':[{'keyid':k['root']['pin'],
        'sig_b64':sign(k['root']['priv'],g.crypto.ROOT_CONTEXT,rp,root)}]}
    releasedoc={'payload':lp,'signatures':[{'keyid':k['release']['pin'],
        'sig_b64':sign(k['release']['priv'],g.crypto.RELEASE_CONTEXT,lp,root)}]}
    rootraw=g.crypto.canonical(rootdoc);releaseraw=g.crypto.canonical(releasedoc)
    rootmeta=root/'root.json';rootmeta.write_bytes(rootraw)
    releasemeta=root/'release.json';releasemeta.write_bytes(releaseraw)
    pubpath=root/'bootstrap.der';pubpath.write_bytes(k['root']['der'])
    with ExitStack() as st:
        st.enter_context(patch.object(g.crypto,'EVIDENCE_PIN',evidence_pin))
        st.enter_context(patch.object(g.policy,'EVIDENCE',evidence_pin))
        expected=g.evaluate(evidence=evidence,root_metadata=rootmeta,
            release_metadata=releasemeta,first_root_public_der=pubpath,
            first_root_pin=k['root']['pin'],now=now)
    print(json.dumps({'evidenceRaw':evraw.decode('ascii'),
        'rootRaw':rootraw.decode('ascii'),'releaseRaw':releaseraw.decode('ascii'),
        'bootstrapDer':base64.b64encode(k['root']['der']).decode('ascii'),
        'bootstrapPin':k['root']['pin'],'evidencePin':evidence_pin,
        'now':int(now.timestamp()*1000),'expected':expected},separators=(',',':')))
