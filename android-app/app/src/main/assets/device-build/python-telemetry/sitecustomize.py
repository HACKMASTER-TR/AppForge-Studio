"""Observe CPython HTTP response reads without altering TLS, pip hashes or caching.
Python -I/-S or alternate HTTP implementations intentionally remain uninstrumented.
"""
import os
_target = os.environ.get('APPFORGE_DOWNLOAD_TELEMETRY')
if _target:
    import http.client
    import json
    import re
    import time
    import uuid
    _original_begin = http.client.HTTPResponse.begin
    _original_read = http.client.HTTPResponse.read
    _original_readinto = http.client.HTTPResponse.readinto
    def _publish(response, finished=False):
        try:
            event = response._appforge_transfer
            now = time.monotonic()
            if not finished and now - event['last'] < .25:
                return
            event['last'] = now
            total = event['total']
            value = dict(schema=1, transfer=event['id'], component='Python/pip HTTP body',
                         bytes=event['bytes'], total=total if total is not None and event['bytes'] <= total else None,
                         elapsedMs=int((now-event['start'])*1000), finished=finished)
            temporary = _target + '.python-' + str(os.getpid()) + '-' + event['id']
            with open(temporary, 'w') as stream:
                json.dump(value, stream)
            os.replace(temporary, _target)
        except Exception:
            pass  # Observability cannot change transport or weaken resolver validation.
    def _begin(self, *args, **kwargs):
        result = _original_begin(self, *args, **kwargs)
        length = self.getheader('Content-Length')
        total = int(length) if length and re.fullmatch(r'[0-9]{1,18}', length) else None
        if total == 0:
            total = None
        now = time.monotonic()
        self._appforge_transfer = dict(id=uuid.uuid4().hex, bytes=0, total=total, start=now, last=now-1)
        _publish(self)
        return result
    def _read(self, *args, **kwargs):
        data = _original_read(self, *args, **kwargs)
        if hasattr(self, '_appforge_transfer'):
            self._appforge_transfer['bytes'] += len(data)
            _publish(self, not data or self.isclosed())
        return data
    def _readinto(self, buffer):
        # HTTPResponse.readinto does not call read on fixed/chunked stdlib responses.
        # Track nesting defensively for alternate CPython implementations.
        before = self._appforge_transfer['bytes'] if hasattr(self, '_appforge_transfer') else None
        size = _original_readinto(self, buffer)
        if before is not None:
            if self._appforge_transfer['bytes'] == before:
                self._appforge_transfer['bytes'] += size or 0
            _publish(self, not size or self.isclosed())
        return size
    http.client.HTTPResponse.begin = _begin
    http.client.HTTPResponse.read = _read
    http.client.HTTPResponse.readinto = _readinto
