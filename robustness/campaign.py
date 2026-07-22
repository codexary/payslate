"""Run a bounded, seeded HTTP campaign against a local Payslate stack."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from generators import EMPLOYEES, Generators, contract_rows
from oracles import NAMES, Response, check, finding_class


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Client:
    def __init__(self, base_url, token):
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.opener = urllib.request.build_opener(NoRedirect)
        self.requests = 0

    def request(self, method, path, body=None, content_type=None):
        headers = {'Authorization': f'Bearer {self.token}'}
        if content_type:
            headers['Content-Type'] = content_type
        request = urllib.request.Request(self.base_url + path, data=body,
                                         headers=headers, method=method)
        start = time.perf_counter()
        self.requests += 1
        try:
            stream = self.opener.open(request, timeout=120)
        except urllib.error.HTTPError as error:
            stream = error
        with stream:
            data = stream.read()
            result = Response(stream.code, dict(stream.headers), data, time.perf_counter() - start)
        if result.status in (401, 403):
            raise RuntimeError(f'authorization failed ({result.status}); obtain a fresh officer token')
        return result

    def json(self, method, path, body=None):
        return self.request(method, path, None if body is None else json.dumps(body).encode(),
                            'application/json')


def setup(client, create=False):
    """Search exact names first: the API permits duplicates, which break uploads."""
    for name in EMPLOYEES:
        path = '/v1/employees/search?' + urllib.parse.urlencode({'q': name})
        response = client.json('GET', path)
        if response.status != 200:
            raise RuntimeError(f'employee lookup failed ({response.status})')
        matches = [item for item in json.loads(response.body) if item.get('name') == name]
        if len(matches) > 1:
            raise RuntimeError(f'duplicate synthetic employee: {name}')
        if not matches:
            if not create:
                raise RuntimeError('synthetic employees missing; run with --setup first')
            result = client.json('POST', '/v1/employees', {
                'name': name, 'employment': 'PERMANENT', 'hourlyRate': 20,
                'bsb': '123-456', 'account': '12345678', 'deductions': 0})
            if result.status != 200:
                # A concurrent setup may have created the same name.
                retry = client.json('GET', path)
                if retry.status != 200 or len([x for x in json.loads(retry.body)
                                               if x.get('name') == name]) != 1:
                    raise RuntimeError(f'employee creation failed ({result.status})')


def wire_request(data, target, run_id=None):
    if target == 'employees':
        return '/v1/employees', data, 'application/json'
    if target == 'pay-run-events':
        return f'/v1/pay-runs/{run_id}/' + urllib.parse.quote_from_bytes(data, safe=''), b'', None
    boundary = 'payslate-' + hashlib.sha256(data).hexdigest()[:24]
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
            'filename="candidate.csv"\r\nContent-Type: text/csv\r\n\r\n').encode()
    body += data + f'\r\n--{boundary}--\r\n'.encode()
    return '/v1/timesheets', body, f'multipart/form-data; boundary={boundary}'


def minimise(data, reproduce, max_requests=40):
    """Delete line chunks, then byte chunks; preserve only successful replays.

    reproduce returns a matching Response or None. The cap includes the initial
    replay and conservatively counts locally rejected trials as well as requests.
    This bounded reduction does not promise a globally smallest input.
    """
    used = 0
    response = None
    if max_requests <= 0:
        return data, response, used
    response = reproduce(data)
    used += 1
    if response is None:
        return data, None, used
    for unit in ('lines', 'bytes'):
        limit = max_requests // 2 if unit == 'lines' else max_requests
        parts = data.splitlines(keepends=True) if unit == 'lines' else [data[i:i + 1] for i in range(len(data))]
        granularity = 2
        while parts and used < limit:
            chunk = max(1, (len(parts) + granularity - 1) // granularity)
            reduced = False
            for start in range(0, len(parts), chunk):
                if used >= limit:
                    break
                trial_parts = parts[:start] + parts[start + chunk:]
                trial = b''.join(trial_parts)
                result = reproduce(trial)
                used += 1
                if result is not None:
                    parts, data, response = trial_parts, trial, result
                    granularity = max(2, granularity - 1)
                    reduced = True
                    break
            if not reduced:
                if granularity >= len(parts):
                    break
                granularity = min(len(parts), granularity * 2)
    return data, response, used


def save_report(path, report):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def run(args):
    client = Client(args.base_url, args.token)
    if args.target != 'employees' and (args.target == 'timesheets' or args.setup):
        setup(client, args.setup)
    run_id = None
    if args.target == 'pay-run-events':
        response = client.json('POST', '/v1/pay-runs',
                               {'periodStart': '2026-07-06', 'periodEnd': '2026-07-12'})
        if response.status != 200 or 'id' not in response.payload():
            raise RuntimeError(f'pay run creation failed ({response.status})')
        run_id = response.payload()['id']
    strategies = (['random', 'mutation', 'grammar'] if args.target == 'timesheets'
                  else ['random', 'mutation']) if args.strategy == 'all' else [args.strategy]
    generators = Generators(args.seed)
    out = args.out
    out.mkdir(parents=True, exist_ok=True)
    report_path = out / 'findings.json'
    report = json.loads(report_path.read_text(encoding='utf-8')) if report_path.exists() else {'runs': [], 'findings': {}}
    counts = Counter({s: 0 for s in strategies})
    oracle_counts = Counter({name: 0 for name in NAMES})
    strategy_findings = Counter({s: 0 for s in strategies})
    seen = set()
    transport_errors = 0
    reduction_requests = 0
    observations = out / f'{args.target}-{args.seed}-{len(report["runs"]) + 1}.jsonl'

    def send(data):
        path, body, media_type = wire_request(data, args.target, run_id)
        return client.request('POST', path, body, media_type)

    with observations.open('w', encoding='utf-8') as log:
        for index in range(args.budget):
            strategy = strategies[index % len(strategies)]
            candidate = generators.next(strategy, args.target)
            counts[strategy] += 1
            try:
                response = send(candidate.data)
            except (OSError, urllib.error.URLError) as error:
                transport_errors += 1
                log.write(json.dumps({'candidate': index + 1, 'strategy': strategy,
                                      'transport_error': type(error).__name__,
                                      'size': len(candidate.data)}) + '\n')
                log.flush()
                if (index + 1) % 50 == 0:
                    print(f'progress {index + 1}/{args.budget} classes={len(seen)} transport_errors={transport_errors}', flush=True)
                continue
            found = check(response, candidate.expected_rows, args.slow_seconds)
            oracle_counts.update(found)
            strategy_findings[strategy] += bool(found)
            log.write(json.dumps({'candidate': index + 1, 'strategy': strategy,
                                  'size': len(candidate.data), 'sha256': hashlib.sha256(candidate.data).hexdigest(),
                                  'expected_rows': candidate.expected_rows, 'status': response.status,
                                  'elapsed': response.elapsed, 'response': response.payload(),
                                  'oracles': found}, ensure_ascii=False) + '\n')
            log.flush()
            for oracle in found:
                identity = finding_class(oracle, response)
                if identity in seen:
                    continue
                seen.add(identity)
                class_id = hashlib.sha256(json.dumps(identity).encode()).hexdigest()[:16]
                print(f'finding {class_id} {oracle} status={response.status}; reducing input', flush=True)

                def reproduce(data):
                    expected = None
                    if oracle == 'valid-rejected':
                        expected = contract_rows(data)
                        if expected is None:
                            return None
                    try:
                        replay = send(data)
                    except (OSError, urllib.error.URLError):
                        return None
                    if (oracle in check(replay, expected, args.slow_seconds)
                            and finding_class(oracle, replay) == identity):
                        return replay
                    return None

                before = client.requests
                small, replay, attempts = minimise(candidate.data, reproduce, args.minimise_requests)
                reduction_requests += client.requests - before
                final_response = replay if replay is not None else response
                folder = out / class_id
                # One representative per class, preserving earlier targets' artifacts.
                if class_id not in report['findings']:
                    folder.mkdir(exist_ok=True)
                    (folder / 'input.bin').write_bytes(small)
                    path, body, media_type = wire_request(small, args.target, run_id)
                    description = f'POST {client.base_url}{path}\n'
                    if media_type:
                        description += f'Content-Type: {media_type}\n'
                    description += f'Content-Length: {len(body)}\n\n'
                    description += ('Multipart field file: input.bin (filename candidate.csv)\n'
                                    if args.target == 'timesheets' else 'JSON body: input.bin\n'
                                    if args.target == 'employees' else 'Event path segment: percent-encoded bytes from input.bin\n')
                    (folder / 'request.txt').write_text(description, encoding='utf-8')
                    response_text = (f'HTTP {final_response.status}\nElapsed-Seconds: {final_response.elapsed:.6f}\n'
                                     + ''.join(f'{k}: {v}\n' for k, v in final_response.headers.items()) + '\n')
                    (folder / 'response.txt').write_bytes(response_text.encode() + final_response.body)
                    report['findings'][class_id] = {
                        'class': identity, 'target': args.target, 'strategy': strategy,
                        'seed': args.seed, 'candidate': index + 1, 'original_size': len(candidate.data),
                        'minimised_size': len(small), 'reproduced': replay is not None,
                        'reduction_attempts': attempts, 'reduction_requests': client.requests - before,
                        'expected_rows': contract_rows(small) if oracle == 'valid-rejected' else None,
                        'original_elapsed': response.elapsed, 'minimised_elapsed': final_response.elapsed,
                        'original_response': response.payload()}
                    save_report(report_path, report)
            if (index + 1) % 50 == 0:
                print(f'progress {index + 1}/{args.budget} classes={len(seen)} transport_errors={transport_errors}', flush=True)
    summary = {'seed': args.seed, 'budget': args.budget, 'target': args.target,
               'counts_per_strategy': dict(counts), 'findings_per_oracle': dict(oracle_counts),
               'distinct_finding_classes': len(seen), 'candidates_with_findings_per_strategy': dict(strategy_findings),
               'transport_errors': transport_errors, 'minimisation_requests': reduction_requests}
    report['runs'].append({'summary': summary, 'observations': observations.name,
                           'slow_seconds': args.slow_seconds, 'minimise_requests': args.minimise_requests})
    save_report(report_path, report)
    print(json.dumps(summary), flush=True)
    return 2 if transport_errors else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:18080')
    parser.add_argument('--token', required=True)
    parser.add_argument('--strategy', choices=('all', 'random', 'mutation', 'grammar'), default='all')
    parser.add_argument('--budget', type=int, default=300, help='total candidates, shared round-robin across strategies')
    parser.add_argument('--seed', type=int, default=20260716)
    parser.add_argument('--target', choices=('timesheets', 'pay-run-events', 'employees'), default='timesheets')
    parser.add_argument('--setup', action='store_true')
    parser.add_argument('--slow-seconds', type=float, default=2.0)
    parser.add_argument('--minimise-requests', type=int, default=40)
    parser.add_argument('--out', type=Path, default=Path(__file__).resolve().parent / 'out')
    args = parser.parse_args()
    if args.budget < 0 or (args.budget == 0 and not args.setup):
        parser.error('budget must be positive, or zero with --setup')
    if not 1 <= args.minimise_requests <= 200 or not 0 < args.slow_seconds < float('inf'):
        parser.error('minimise-requests must be 1–200 and slow-seconds must be positive and finite')
    if args.target != 'timesheets' and args.strategy == 'grammar':
        parser.error(f'{args.target} supports random, mutation or all')
    url = urllib.parse.urlsplit(args.base_url)
    if url.scheme not in ('http', 'https') or not url.netloc or url.username or url.password or url.query or url.fragment:
        parser.error('base-url must be an HTTP(S) URL without credentials, query or fragment')
    try:
        return run(args)
    except (OSError, ValueError, RuntimeError, urllib.error.URLError) as error:
        print(f'campaign stopped: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
