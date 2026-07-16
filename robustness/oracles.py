"""HTTP response checks and stable finding identities."""

from dataclasses import dataclass
import json
import re

NAMES = ('server-error', 'problem-format', 'internal-detail', 'slow', 'valid-rejected')
INTERNAL = re.compile(
    r'com\.elsworne|java\.|org\.(?:springframework|apache|postgresql)|Exception|'
    r'\bSELECT\b[\s\S]{0,200}?\bFROM\b|\bINSERT\s+INTO\b|'
    r'\bUPDATE\s+\w+\s+SET\b|\bDELETE\s+FROM\b|\b(?:CREATE|ALTER|DROP)\s+TABLE\b|'
    r'\bat\s+[\w.$]+\([^)]*\.java:\d+\)', re.IGNORECASE)


@dataclass(frozen=True)
class Response:
    status: int
    headers: dict
    body: bytes
    elapsed: float

    def payload(self):
        try:
            result = json.loads(self.body)
            return result if isinstance(result, dict) else {}
        except (ValueError, UnicodeError):
            return {}


def check(response, expected_rows=None, slow_seconds=2.0):
    findings = []
    payload = response.payload()
    media_type = next((str(v).split(';', 1)[0].strip().lower()
                       for k, v in response.headers.items() if k.lower() == 'content-type'), '')
    if response.status >= 500:
        findings.append('server-error')
    if 400 <= response.status < 600 and (media_type != 'application/problem+json'
                                        or not {'status', 'title'} <= payload.keys()):
        findings.append('problem-format')
    # Parse JSON first so escaped newlines in stack frames are visible too.
    body = response.body.decode('utf-8', errors='replace')
    if INTERNAL.search(body) or INTERNAL.search(str(payload)):
        findings.append('internal-detail')
    if response.elapsed > slow_seconds:
        findings.append('slow')
    if expected_rows is not None and (response.status != 200
                                      or type(payload.get('rows')) is not int
                                      or payload['rows'] != expected_rows):
        findings.append('valid-rejected')
    return findings


def finding_class(oracle, response):
    payload = response.payload()
    detail = re.sub(r'\d', '#', str(payload.get('detail', ''))[:60])
    return oracle, response.status, str(payload.get('title', '')), detail
