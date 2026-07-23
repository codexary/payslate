import csv
import io
import json
import random
import unittest

from generators import EMPLOYEES, Generators, contract_rows, large_seed, mutate


class GeneratorTests(unittest.TestCase):
    def test_determinism_and_stream_independence(self):
        for strategy in ('random', 'mutation', 'grammar'):
            a, b = Generators(20260716), Generators(20260716)
            for _ in range(20):
                if strategy != 'random':
                    b.next('random')
                self.assertEqual(a.next(strategy), b.next(strategy))
        for strategy in ('random', 'mutation'):
            a, b = Generators(7), Generators(7)
            self.assertEqual([a.next(strategy, 'pay-run-events') for _ in range(30)],
                             [b.next(strategy, 'pay-run-events') for _ in range(30)])

    def test_employee_candidates_are_deterministic_json_without_row_expectations(self):
        for strategy in ('random', 'mutation'):
            a, b = Generators(42), Generators(42)
            values = []
            for _ in range(1000):
                candidate = a.next(strategy, 'employees')
                self.assertEqual(candidate, b.next(strategy, 'employees'))
                self.assertIsNone(candidate.expected_rows)
                body = json.loads(candidate.data)
                self.assertEqual(set(body), {'name', 'employment', 'hourlyRate',
                                             'bsb', 'account', 'deductions'})
                values.append(body)
            self.assertTrue(any(isinstance(x['name'], str) and len(x['name']) == 101
                                for x in values))
            self.assertTrue(any(isinstance(x['name'], str) and '\0' in x['name']
                                for x in values))
            for number in (1e20, 12.345, int('9' * 1000)):
                self.assertTrue(any(x['hourlyRate'] == number for x in values))
            self.assertTrue(any(isinstance(x['hourlyRate'], (list, dict)) for x in values))
        with self.assertRaises(ValueError):
            Generators(42).next('grammar', 'employees')

    def test_employee_mutations_change_at_most_one_field_and_keep_negative_zero(self):
        from generators import EMPLOYEE_BODY
        generator = Generators(7)
        candidates = [generator.next('mutation', 'employees').data for _ in range(1000)]
        self.assertTrue(any(b':-0' in data for data in candidates))
        for data in candidates:
            body = json.loads(data)
            self.assertLessEqual(sum(body[k] != v for k, v in EMPLOYEE_BODY.items()), 1)

    def test_grammar_width_counts_and_contract(self):
        columns = set()
        for seed in (1, 42, 20260716):
            generator = Generators(seed)
            for _ in range(300):
                candidate = generator.next('grammar')
                text = candidate.data.decode('utf-8-sig')
                header = text.splitlines()[0]
                delimiter = '\t' if '\t' in header else ';' if ';' in header else ','
                rows = [row for row in csv.reader(io.StringIO(text, newline=''),
                                                  delimiter=delimiter, strict=True) if row]
                self.assertTrue(all(len(row) == len(rows[0]) for row in rows))
                columns.add(('notes' in rows[0], 'break minutes' in rows[0]))
                self.assertFalse(any('\n' in field or '\r' in field
                                     for row in rows for field in row))
                self.assertEqual(len(rows) - 1, candidate.expected_rows)
                self.assertEqual(contract_rows(candidate.data), candidate.expected_rows,
                                 candidate.data)
                self.assertTrue(all(row[0] in EMPLOYEES for row in rows[1:]))
        self.assertEqual(columns, {(False, False), (False, True),
                                   (True, False), (True, True)})

    def test_contract_ignores_extra_columns_but_rejects_duplicate_known_columns(self):
        for extra in ('notes,break minutes', 'notes,notes', 'custom,other'):
            data = (f'employee,date,start,end,{extra}\n'
                    'Liam Walsh,2026-07-06,09:00,17:00,checked,30\n').encode()
            self.assertEqual(contract_rows(data), 1)
        for extra in ('employee', 'Employee Name', 'STAFF', 'date', 'START TIME',
                      'end', 'End Time'):
            data = (f'employee,date,start,end,{extra}\n'
                    'Liam Walsh,2026-07-06,09:00,17:00,ignored\n').encode()
            self.assertIsNone(contract_rows(data), data)
        self.assertIsNone(contract_rows(
            b'employee,date,start,end,holiday,PUBLIC_HOLIDAY\n'
            b'Liam Walsh,2026-07-06,09:00,17:00,true,true\n'))

    def test_contract_rejects_quoted_line_breaks_in_ignored_columns(self):
        for newline in (b'\n', b'\r\n'):
            data = (b'employee,date,start,end,notes\n'
                    b'Liam Walsh,2026-07-06,09:00,17:00,"first' + newline + b'second"\n')
            self.assertIsNone(contract_rows(data), data)

    def test_mutation_covers_quoted_line_breaks(self):
        from generators import TOKENS

        class QuotedLineBreakOnly:
            def randint(self, a, b):
                return 1

            def choice(self, values):
                return b'"quoted\nline"' if values is TOKENS else 'token'

            def randrange(self, stop):
                return stop - 2

        data = (b'employee,date,start,end,notes\n'
                b'Liam Walsh,2026-07-06,09:00,17:00,\n')
        result = mutate(data, QuotedLineBreakOnly())
        self.assertIn(b'"quoted\nline"', result)
        self.assertIsNone(contract_rows(result))

    def test_large_seed_is_deterministic_and_valid(self):
        a, b = large_seed(random.Random(9)), large_seed(random.Random(9))
        self.assertEqual(a, b)
        self.assertTrue(5000 <= contract_rows(a) <= 20000)

    def test_field_swap_preserves_row_boundary(self):
        class SwapOnly:
            def randint(self, a, b):
                return 1

            def choice(self, values):
                return 'swap'

            def randrange(self, stop):
                return 1

            def sample(self, population, count):
                return [0, 3]

        for newline in (b'\n', b'\r\n'):
            data = newline.join((b'employee,date,start,end',
                                 b'Liam Walsh,2026-07-06,09:00,17:00', b''))
            result = mutate(data, SwapOnly())
            self.assertEqual(result.splitlines(), [b'employee,date,start,end',
                                                    b'17:00,2026-07-06,09:00,Liam Walsh'])
            self.assertTrue(result.endswith(newline))

    def test_support_seed_bytes(self):
        from generators import ROOT
        self.assertIn(b'\xe9', (ROOT / 'seeds/ticket-c-windows1252.csv').read_bytes())
        data = (ROOT / 'seeds/bom-semicolon.csv').read_bytes()
        self.assertTrue(data.startswith(b'\xef\xbb\xbf'))
        self.assertIn(b'\r\n', data)

    def test_contract_guard_rejects_invalid_shrinks(self):
        good = b'employee,date,start,end\nLiam Walsh,2026-07-06,09:00,17:00\n'
        self.assertEqual(contract_rows(good), 1)
        for data in (b'', good.replace(b'17:00', b'09:00'),
                     good.replace(b'2026-07-06', b'2026-02-30'),
                     good.replace(b'Liam Walsh', b'Unknown'), good.replace(b'\n', b'\r'),
                     good.replace(b'09:00', b'24:00'), good + b'broken\n'):
            self.assertIsNone(contract_rows(data), data)


if __name__ == '__main__':
    unittest.main()
