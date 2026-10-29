#!/usr/bin/env python3
"""Explicit acquisition of whole OSV ecosystem exports, never called by scans.

HTTPS authenticates the OSV-owned origin. Object generation, server size/MD5,
local SHA-256, every ZIP CRC/JSON record and 72-hour freshness are checked.
These checks do not constitute signed provenance attestation.
"""
import base64
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PurePosixPath
import sys
import urllib.parse
import urllib.request
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def acquire(ecosystem, root):
    now = datetime.now(timezone.utc)
    object_name = urllib.parse.quote(ecosystem + '/all.zip', safe='')
    metadata_url = 'https://storage.googleapis.com/storage/v1/b/osv-vulnerabilities/o/' + object_name
    with urllib.request.urlopen(metadata_url, timeout=60) as response:
        metadata = json.load(response)
    updated = datetime.fromisoformat(metadata['updated'].replace('Z', '+00:00'))
    age = (now - updated).total_seconds()
    require(0 <= age <= 72 * 3600, 'database stale or future dated')
    size = int(metadata['size'])
    require(0 < size < 512 * 1024**2, 'database size limit')
    url = metadata_url + '?alt=media&generation=' + metadata['generation']
    target = root / 'osv-scalibr' / ecosystem / 'all.zip'
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_suffix('.partial')
    sha = hashlib.sha256()
    md5 = hashlib.md5(usedforsecurity=False)
    total = 0
    with urllib.request.urlopen(url, timeout=120) as response, partial.open('xb') as stream:
        while chunk := response.read(1024 * 1024):
            total += len(chunk)
            require(total <= size, 'size limit')
            sha.update(chunk)
            md5.update(chunk)
            stream.write(chunk)
    require(total == size, 'download size mismatch')
    require(base64.b64encode(md5.digest()).decode() == metadata['md5Hash'], 'upstream MD5 mismatch')
    count = matching = unpacked = 0
    with zipfile.ZipFile(partial) as archive:
        require(0 < len(archive.infolist()) < 500000, 'ZIP entry count limit')
        for entry in archive.infolist():
            name = PurePosixPath(entry.filename)
            require(not name.is_absolute() and '..' not in name.parts, 'unsafe ZIP path')
            require(entry.filename.endswith('.json') and entry.file_size < 32 * 1024**2, 'invalid ZIP entry')
            unpacked += entry.file_size
            require(unpacked < 8 * 1024**3, 'uncompressed size limit')
            record = json.loads(archive.read(entry))
            require(isinstance(record.get('id'), str) and isinstance(record.get('affected'), list), 'invalid advisory JSON')
            count += 1
            matching += any(a.get('package', {}).get('ecosystem') == ecosystem for a in record['affected'])
    require(matching > 0, 'empty ecosystem coverage')
    partial.rename(target)
    return dict(ecosystem=ecosystem, origin='https://osv-vulnerabilities.storage.googleapis.com/' + ecosystem + '/all.zip',
                metadata_url=metadata_url, download_url=url, generation=metadata['generation'],
                updated=metadata['updated'], acquired_at=now.isoformat(), age_seconds_at_acquisition=age,
                freshness_limit_seconds=72 * 3600, size=total, sha256=sha.hexdigest(),
                upstream_md5_base64=metadata['md5Hash'], upstream_crc32c_base64=metadata.get('crc32c'),
                zip_crc_all_entries_verified=True, json_records=count, ecosystem_matching_records=matching,
                uncompressed_bytes=unpacked, verification='HTTPS origin + generation + size + upstream MD5 + local SHA-256 + ZIP CRC/JSON; no signed attestation')


if __name__ == '__main__':
    import os
    os.umask(0o077)
    destination = Path(sys.argv[1]).resolve()
    destination.mkdir(parents=True, exist_ok=True)
    records = []
    for ecosystem in ('npm', 'Maven', 'PyPI', 'crates.io'):
        record = acquire(ecosystem, destination)
        records.append(record)
        (destination / 'provenance.json').write_text(json.dumps(records, indent=2) + '\n')
        print(ecosystem, record['sha256'], record['json_records'], flush=True)
