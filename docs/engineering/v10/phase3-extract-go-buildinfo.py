"""Read Go module identities from verified archives; never execute scanner binaries.

Reproduce after phase3-review-archives.py. This is build metadata, not an SBOM,
license closure review, provenance signature verification or scanner acceptance.
"""
import hashlib
import json
import tarfile
from pathlib import Path

review_path = Path('docs/engineering/v10/phase3-security-archive-review.json')
review = json.loads(review_path.read_text())
pins = json.loads(Path('quality/tools/v10-oss-pins.json').read_text())


def read_string(data, offset):
    size = 0
    for shift in range(0, 64, 7):
        byte = data[offset]
        offset += 1
        size |= (byte & 127) << shift
        if byte < 128:
            if size > 8 * 1024 * 1024 or offset + size > len(data):
                raise ValueError('INVALID_BUILDINFO_SIZE')
            return data[offset:offset + size], offset + size
    raise ValueError('INVALID_BUILDINFO_VARINT')


for row in review['artifacts']:
    if not row.get('matches_pin'):
        continue
    tool = row['tool']
    pin = pins[tool]['artifacts']['x86_64']
    archive = Path('.appforge/tmp/v10/phase3/review-cache') / pin['name']
    if hashlib.sha256(archive.read_bytes()).hexdigest() != pin['sha256']:
        raise ValueError('ARCHIVE_CHANGED')
    with tarfile.open(archive, 'r:gz') as tar:
        entry = tar.getmember(tool)
        if entry.size > 100 * 1024 * 1024:
            raise ValueError('BINARY_LIMIT')
        data = tar.extractfile(entry).read()
    offset = data.find(b'\xff Go buildinf:')
    if offset < 0 or not data[offset + 15] & 2:
        row['go_buildinfo'] = {'status': 'UNSUPPORTED_LAYOUT'}
        continue
    version, next_offset = read_string(data, offset + 32)
    modules, _ = read_string(data, next_offset)
    # Go wraps module text in 16-byte sentinels. Preserve identities only.
    lines = modules[16:-16].decode('utf-8').splitlines()
    dependencies = []
    for line in lines:
        fields = line.split('\t')
        if fields[0] in ('dep', 'mod', '=>'):
            dependencies.append({'kind': fields[0], 'identity': fields[1:]})
    row['go_buildinfo'] = {'status': 'STATIC_METADATA_ONLY', 'go_version': version.decode('utf-8'),
                           'modules': dependencies, 'license_closure': 'UNREVIEWED'}
review_path.write_text(json.dumps(review, indent=2) + '\n')
print('STATIC_BUILDINFO_RECORDED; NO_SCANNER_EXECUTION')
