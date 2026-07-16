"""Seeded byte, mutation and contract-valid grammar generators (stdlib only)."""

import csv
from dataclasses import dataclass
from datetime import date, datetime
import io
import json
from pathlib import Path
import random
import re
import string

ROOT = Path(__file__).resolve().parent
GRAMMAR = json.loads((ROOT / 'grammar.json').read_text(encoding='utf-8'))
REFERENCE = re.compile(r'<([a-z0-9_]+)>')
SHARED = {'delimiter': 'delimiter', 'newline': 'newline',
          'holiday_header_slot': 'holiday', 'holiday_value_slot': 'holiday'}
EMPLOYEES = ('Liam Walsh', 'Renée Dubois', 'Mia Chen', 'Walsh, Liam',
             'Walsh; Liam', 'Walsh\t Liam', 'Renée "Ren" Dubois')
EVENTS = (b'calculate', b'approve', b'pay', b'reverse')
TOKENS = (b'"', b'\r', b'\0', b'\t', b';', b'\xe9', b'+999999999',
          b'0001', b'24:00', b'13:61', '\ufeff'.encode('utf-8'))
ALIASES = {'employee': 'employee', 'employee name': 'employee', 'staff': 'employee',
           'date': 'date', 'shift date': 'date', 'start': 'start',
           'start time': 'start', 'end': 'end', 'end time': 'end',
           'public holiday': 'holiday', 'public_holiday': 'holiday', 'holiday': 'holiday'}


@dataclass(frozen=True)
class Candidate:
    data: bytes
    expected_rows: int | None = None


def grammar(rng):
    """Adapt the spike's bounded expansion, sharing separator and optional slots."""
    choices = {}
    row_count = 0

    def expand(symbol, depth=10):
        nonlocal row_count
        if depth <= 0:
            raise ValueError('grammar depth exceeded')
        if symbol == 'row':
            row_count += 1
        alternatives = GRAMMAR[symbol]
        key = SHARED.get(symbol)
        if key in choices:
            index = choices[key]
        else:
            index = rng.randrange(len(alternatives))
            if key is not None:
                choices[key] = index
        return REFERENCE.sub(lambda m: expand(m[1], depth - 1), alternatives[index])

    data = expand('file').encode('utf-8')
    return Candidate(data, row_count)


def contract_rows(data, employees=EMPLOYEES):
    """Independent acceptance guard for shrinking valid inputs; None means invalid.

    Quoted newlines are excluded because ADR 0003 leaves their policy undecided.
    Uses the public format contract, never the product parser's response.
    """
    try:
        text = data.decode('utf-8-sig')
        if '\ufeff' in text or re.search(r'\r(?!\n)', text):
            return None
        first = text.split('\n', 1)[0]
        for delimiter in (',', ';', '\t'):
            fields = next(csv.reader([first], delimiter=delimiter, strict=True))
            header = [ALIASES.get(field.lower()) for field in fields]
            if (None not in header and len(set(header)) == len(header)
                    and {'employee', 'date', 'start', 'end'} <= set(header)):
                break
        else:
            return None
        rows = list(csv.reader(io.StringIO(text, newline=''), delimiter=delimiter, strict=True))
        rows = [row for row in rows if row and row != ['']]
        if not first or len(rows) < 2:
            return None
        for row in rows[1:]:
            if len(row) != len(header) or any('\n' in x or '\r' in x for x in row):
                return None
            values = dict(zip(header, row))
            if values['employee'] not in employees:
                return None
            day = values['date']
            if re.fullmatch(r'\d{4}-\d{2}-\d{2}', day):
                date.fromisoformat(day)
            elif re.fullmatch(r'\d{1,2}/\d{1,2}/\d{4}', day):
                d, m, y = map(int, day.split('/'))
                date(y, m, d)
            else:
                return None
            times = []
            for key in ('start', 'end'):
                value = values[key]
                if re.fullmatch(r'\d{2}:\d{2}', value):
                    times.append(datetime.strptime(value, '%H:%M').time())
                elif re.fullmatch(r'\d{1,2}:\d{2} (AM|PM)', value.upper()):
                    times.append(datetime.strptime(value.upper(), '%I:%M %p').time())
                else:
                    return None
            if times[0] == times[1]:
                return None
            if values.get('holiday', '').lower() not in ('true', 'yes', '1', 'false', 'no', '0', ''):
                return None
        return len(rows) - 1
    except (UnicodeError, csv.Error, ValueError, KeyError, StopIteration):
        return None


def random_bytes(rng):
    if rng.randrange(2):
        return bytes(rng.randrange(256) for _ in range(rng.randint(0, 4096)))
    return ('\n'.join(''.join(rng.choices(string.printable[:95], k=rng.randint(0, 240)))
                      for _ in range(rng.randint(0, 24)))).encode('ascii')


def mutate(data, rng):
    """Apply one to four uniformly selected format-agnostic edits."""
    for _ in range(rng.randint(1, 4)):
        op = rng.choice(('overwrite', 'insert', 'delete', 'token', 'duplicate', 'drop', 'swap'))
        if op in ('overwrite', 'insert', 'delete', 'token'):
            pos = rng.randrange(len(data) + 1)
            if op == 'insert':
                data = data[:pos] + bytes([rng.randrange(256)]) + data[pos:]
            elif op == 'token':
                data = data[:pos] + rng.choice(TOKENS) + data[pos:]
            elif pos < len(data):
                data = data[:pos] + (bytes([rng.randrange(256)]) if op == 'overwrite' else b'') + data[pos + 1:]
        else:
            lines = data.splitlines(keepends=True)
            if not lines:
                continue
            pos = rng.randrange(len(lines))
            if op == 'duplicate':
                lines.insert(pos, lines[pos])
            elif op == 'drop':
                del lines[pos]
            else:
                first = lines[0]
                delimiter = b'\t' if b'\t' in first else b';' if b';' in first else b','
                line = lines[pos]
                ending = b'\r\n' if line.endswith(b'\r\n') else b'\n' if line.endswith(b'\n') else b''
                fields = (line[:-len(ending)] if ending else line).split(delimiter)
                if len(fields) >= 2:
                    a, b = rng.sample(range(len(fields)), 2)
                    fields[a], fields[b] = fields[b], fields[a]
                    lines[pos] = delimiter.join(fields) + ending
            data = b''.join(lines)
    return data


def large_seed(rng):
    rows = [b'employee,date,start,end\n']
    for _ in range(rng.randint(5000, 20000)):
        name = rng.choice(EMPLOYEES[:3])
        rows.append(f'{name},2026-07-{rng.randint(6, 12):02},09:00,17:00\n'.encode('utf-8'))
    return b''.join(rows)


class Generators:
    def __init__(self, seed):
        # Separate streams: changing strategy interleaving cannot change its inputs.
        self.rng = {name: random.Random(f'{seed}:{name}')
                    for name in ('random', 'mutation', 'grammar')}
        self.seeds = [path.read_bytes() for path in sorted((ROOT / 'seeds').glob('*.csv'))]
        self.seeds.append(large_seed(random.Random(f'{seed}:large')))

    def next(self, strategy, target='timesheets'):
        rng = self.rng[strategy]
        if target == 'pay-run-events':
            if strategy == 'grammar':
                raise ValueError('event target supports random and mutation only')
            if strategy == 'mutation':
                return Candidate(mutate(rng.choice(EVENTS), rng))
            return Candidate(''.join(rng.choices(string.ascii_letters + string.digits + '-_',
                                                k=rng.randint(1, 40))).encode('ascii'))
        if strategy == 'grammar':
            return grammar(rng)
        if strategy == 'mutation':
            return Candidate(mutate(rng.choice(self.seeds), rng))
        if strategy == 'random':
            return Candidate(random_bytes(rng))
        raise ValueError(f'unknown strategy: {strategy}')
