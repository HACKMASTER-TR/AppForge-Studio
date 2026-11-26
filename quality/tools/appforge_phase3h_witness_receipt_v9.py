#!/usr/bin/env python3
"""Offline verifier for a hypothetical independently signed V9 witness receipt.

No signing keys, server, network, storage, Android integration or deployment.
A receipt does not prove the witness independently verified Phase3G releases.
"""
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
import base64
import binascii
import hashlib
import json
import re
import secrets

import appforge_phase3e_verify_v9 as crypto

DOMAIN = b'APPFORGE-V9-PHASE3H-WITNESS-RECEIPT\x00'
MAX_RECEIPT = 8192
SHA = re.compile(r'[0-9a-f]{64}\Z')
NONCE = re.compile(r'[0-9a-f]{32}\Z')
MAX_SEQUENCE = 2**31-1
MAX_GENERATION = 2**63-1


def require(ok, reason):
    if not ok:
        raise ValueError('BLOCKED: '+reason)


def canonical(data):
    return (json.dumps(data, sort_keys=True, separators=(',', ':'),
                       ensure_ascii=True, allow_nan=False)+'\n').encode('ascii')


def _unique(pairs):
    output = {}
    for k, v in pairs:
        require(k not in output, 'duplicate JSON key')
        output[k] = v
    return output


def parse(raw):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_RECEIPT, 'receipt size')
    try:
        parsed = json.loads(raw.decode('utf-8','strict'),
            object_pairs_hook=_unique,
            parse_constant=lambda _: (_ for _ in ()).throw(ValueError('nonfinite')))
        require(canonical(parsed) == raw, 'noncanonical receipt')
    except (UnicodeError, json.JSONDecodeError, TypeError, RecursionError,
            OverflowError) as err:
        raise ValueError('BLOCKED: malformed JSON receipt') from err
    return parsed


def exact(obj, names, label):
    require(type(obj) is dict and set(obj) == set(names), 'invalid '+label+' fields')


def number(x, lower, upper, label):
    require(type(x) is int and lower <= x <= upper, 'invalid '+label)


def sha(x, label):
    require(type(x) is str and bool(SHA.fullmatch(x)), 'invalid '+label)


def nonce(x):
    require(type(x) is str and bool(NONCE.fullmatch(x)), 'invalid request nonce')


def new_challenge():
    return secrets.token_hex(16)


def record(obj):
    if obj is None:
        return None
    exact(obj, ('sha256','root_version','release_sequence'), 'checkpoint record')
    sha(obj['sha256'], 'checkpoint SHA')
    number(obj['root_version'], 1, MAX_SEQUENCE, 'root version')
    number(obj['release_sequence'], 1, MAX_SEQUENCE, 'release sequence')
    return (obj['sha256'], obj['root_version'], obj['release_sequence'])


def current_identity(payload):
    """Digest of semantic witness state, independent of fresh challenge and TTL."""
    return hashlib.sha256(canonical({k:payload[k] for k in
        ('witness_epoch','generation','committed','pending')})).hexdigest()


def validate_payload(p, device_id, challenge, *, now, epoch_floor):
    exact(p,('schema','role','device_id','challenge','witness_epoch','generation',
             'committed','pending','issued_at','expires_at'),'witness payload')
    require(type(p['schema']) is int and p['schema']==1 and
            p['role']=='appforge-v9-witness-receipt','wrong witness role')
    sha(p['device_id'],'device identity')
    require(type(device_id) is str and p['device_id']==device_id,
            'device mismatch')
    nonce(challenge)
    require(p['challenge']==challenge,'stale or cross-request receipt')
    number(p['witness_epoch'],1,MAX_SEQUENCE,'witness epoch')
    number(p['generation'],0,MAX_GENERATION,'witness generation')
    number(epoch_floor,1,MAX_SEQUENCE,'minimum witness epoch')
    require(p['witness_epoch']>=epoch_floor,'witness epoch rollback')
    committed=record(p['committed'])
    pending=p['pending']
    if pending is not None:
        exact(pending,('prior','next'),'pending witness intent')
        old=record(pending['prior'])
        nxt=record(pending['next'])
        require(nxt is not None and old==committed,'witness pending prior mismatch')
        if old is None:
            require(nxt[1]==1,'non-bootstrap witness pending root')
        else:
            require(nxt[2]>old[2] and nxt[1] in (old[1],old[1]+1),
                    'witness pending rollback or root skip')
    if committed is None and pending is None:
        require(p['generation']==0,'empty witness has advanced generation')
    issued=crypto.iso_time(p['issued_at'])
    expires=crypto.iso_time(p['expires_at'])
    require(type(now) is datetime and now.tzinfo is not None,'trusted clock required')
    require(issued<=now+timedelta(seconds=10),'witness issued in future')
    require(expires>now,'witness receipt expired')
    require(expires>issued and expires-issued<=timedelta(seconds=120),
            'witness receipt freshness window exceeded')
    return committed,pending


@dataclass(frozen=True)
class VerifiedSnapshot:
    committed: object
    pending: object
    witness_epoch: int
    generation: int
    identity: str


class ReceiptVerifier:
    """In-process replay controls; NOT a persistent secure monotonic store."""
    def __init__(self, pinned_der, pinned_spki_sha256, *, epoch_floor=1):
        crypto.require(type(pinned_der) is bytes,'witness public DER bytes missing')
        require(crypto.spki(pinned_der)==pinned_spki_sha256,
                'external witness public key pin mismatch')
        number(epoch_floor,1,MAX_SEQUENCE,'minimum witness epoch')
        self._der=pinned_der
        self._pin=pinned_spki_sha256
        self._floor_epoch=epoch_floor
        self._last=None
        self._last_committed=None
        self._used_challenges=set()

    def verify(self, raw, *, device_id, challenge, now=None):
        nonce(challenge)
        require(challenge not in self._used_challenges, 'reused witness challenge')
        receipt=parse(raw)
        exact(receipt,('payload','keyid','sig_b64'),'signed receipt')
        require(receipt['keyid']==self._pin, 'untrusted witness signer')
        require(type(receipt['sig_b64']) is str and len(receipt['sig_b64'])==88,
                'invalid witness signature encoding')
        try:
            sig=base64.b64decode(receipt['sig_b64'],validate=True)
        except (ValueError,binascii.Error) as err:
            raise ValueError('BLOCKED: invalid witness signature base64') from err
        require(len(sig)==64 and base64.b64encode(sig).decode('ascii')==receipt['sig_b64'],
                'noncanonical witness signature')
        p=receipt['payload']
        require(type(p) is dict,'invalid witness payload')
        message=DOMAIN+canonical(p)
        crypto.verify_crypto(self._der,sig,message)
        now=now if now is not None else datetime.now(timezone.utc)
        committed,pending=validate_payload(p,device_id,challenge,now=now,
                                            epoch_floor=self._floor_epoch)
        generation=p['generation'];epoch=p['witness_epoch']
        ident=current_identity(p)
        if self._last is not None:
            prev_epoch,prev_gen,prev_identity=self._last
            require(epoch>prev_epoch or (epoch==prev_epoch and generation>=prev_gen),
                    'witness generation rollback')
            if epoch==prev_epoch and generation==prev_gen:
                require(ident==prev_identity,'same generation witness equivocation')
            require(epoch==prev_epoch,'witness epoch replacement needs independent re-pinning')
            if self._last_committed is not None:
                require(committed is not None,'witness committed checkpoint deleted')
                require(committed[2]>=self._last_committed[2] and
                        committed[1]>=self._last_committed[1],
                        'committed witness floor rollback')
                if committed[2]==self._last_committed[2]:
                    require(committed==self._last_committed,
                            'same release witness checkpoint changed')
        self._last=(epoch,generation,ident)
        self._last_committed=committed
        self._used_challenges.add(challenge)
        return VerifiedSnapshot(committed,pending,epoch,generation,ident)


if __name__=='__main__':
    print('PHASE3H_STAGE2_RECEIPTS=VERIFY_ONLY')
    print('REAL_WITNESS=NOT_DEPLOYED')
    print('PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED')
    print('R2_UPLOAD=NO')
    print('PUBLIC_DISTRIBUTION=DISABLED')
    print('ANDROID_CLIENT_ENABLED=NO')
    print('ALL_ENGINES_READY=false')
