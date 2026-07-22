import json
import unittest
from urllib.parse import unquote_to_bytes

from campaign import minimise, setup, wire_request
from generators import contract_rows
from oracles import Response, check, finding_class


def response(status=200, body=None, content_type='application/json', elapsed=0.1):
    return Response(status, {'Content-Type': content_type},
                    json.dumps({'rows': 2} if body is None else body).encode(), elapsed)


class OracleTests(unittest.TestCase):
    def test_normal_success_and_problem(self):
        self.assertEqual(check(response(), 2), [])
        self.assertEqual(check(response(400, {'status': 400, 'title': 'Invalid request'},
                                        'application/problem+json; charset=UTF-8')), [])

    def test_each_oracle(self):
        cases = [
            ('server-error', response(500, {'status': 500, 'title': 'Error'}, 'application/problem+json'), None),
            ('problem-format', response(400, {'status': 400, 'title': 'Bad'}), None),
            ('problem-format', response(400, {'status': 400}, 'application/problem+json'), None),
            ('internal-detail', response(400, {'detail': 'com.elsworne.Type'}), None),
            ('slow', response(elapsed=2.1), None),
            ('valid-rejected', response(400), 2),
            ('valid-rejected', response(body={'rows': 1}), 2),
        ]
        for oracle, result, expected in cases:
            self.assertIn(oracle, check(result, expected))
        self.assertNotIn('slow', check(response(elapsed=2.0)))
        self.assertIn('slow', check(response(elapsed=1.1), slow_seconds=1))
        self.assertIn('problem-format', check(Response(500, {}, b'<html>Error</html>', 0.1)))

    def test_internal_detail_patterns(self):
        for detail in ('java.lang.String', 'org.springframework.Type', 'org.apache.Type',
                       'org.postgresql.Type', 'IllegalArgumentException',
                       'SELECT id FROM employees', 'INSERT INTO timesheets VALUES (?)',
                       'UPDATE timesheets SET starts = ?', 'DELETE FROM employees',
                       'at sample.Work.run(Work.java:42)'):
            self.assertIn('internal-detail', check(response(body={'detail': detail})))

    def test_class_normalisation(self):
        a = response(400, {'title': 'Bad', 'detail': 'row 123: ' + 'x' * 80})
        b = response(400, {'title': 'Bad', 'detail': 'row 987: ' + 'x' * 80})
        self.assertEqual(finding_class('slow', a), finding_class('slow', b))
        self.assertEqual(len(finding_class('slow', a)[3]), 60)

    def test_minimisation_keeps_class_and_cap(self):
        calls = []
        failure = response(500, {'status': 500, 'title': 'Error'}, 'application/problem+json')
        wanted = finding_class('server-error', failure)

        def reproduce(data):
            calls.append(data)
            result = failure if b'!' in data else response()
            if 'server-error' in check(result) and finding_class('server-error', result) == wanted:
                return result
            return None

        small, replay, attempts = minimise(b'extra\nnoise!noise\nextra\n', reproduce, 40)
        self.assertEqual(small, b'!')
        self.assertEqual(finding_class('server-error', replay), wanted)
        self.assertEqual(attempts, len(calls))
        self.assertLessEqual(attempts, 40)
        self.assertEqual(minimise(b'none', reproduce, 2)[:2], (b'none', None))

    def test_valid_minimisation_recounts_and_preserves_contract(self):
        data = b'employee,date,start,end\n' + b'Liam Walsh,2026-07-06,09:00,17:00\n' * 3
        failure = response(400, {'status': 400, 'title': 'Invalid request'}, 'application/problem+json')

        def reproduce(trial):
            count = contract_rows(trial)
            return failure if count is not None and 'valid-rejected' in check(failure, count) else None

        small, replay, attempts = minimise(data, reproduce, 80)
        self.assertIsNotNone(replay)
        self.assertEqual(contract_rows(small), 1)
        self.assertLess(len(small), len(data))
        self.assertLessEqual(attempts, 80)

    def test_wire_encoding(self):
        data = b'/evil?x=1#\x00\xff'
        path, body, media_type = wire_request(data, 'pay-run-events', 42)
        self.assertEqual(unquote_to_bytes(path.rsplit('/', 1)[1]), data)
        self.assertEqual(body, b'')
        path, body, media_type = wire_request(data, 'timesheets')
        self.assertIn(b'name="file"', body)
        self.assertIn(data, body)
        self.assertTrue(media_type.startswith('multipart/form-data; boundary='))

    def test_employee_wire_body_and_oracles(self):
        from generators import Generators
        for strategy in ('random', 'mutation'):
            candidate = Generators(42).next(strategy, 'employees')
            self.assertEqual(wire_request(candidate.data, 'employees'),
                             ('/v1/employees', candidate.data, 'application/json'))
            self.assertNotIn('valid-rejected', check(response(400), candidate.expected_rows))
            result = response(500, {'status': 500, 'title': 'Error'}, 'application/problem+json')
            self.assertEqual(check(result, candidate.expected_rows), ['server-error'])

    def test_setup_does_not_duplicate_existing_names(self):
        class FakeClient:
            def json(self, method, path, body=None):
                from urllib.parse import parse_qs, urlsplit
                self_test.assertEqual(method, 'GET')
                name = parse_qs(urlsplit(path).query)['q'][0]
                return response(body=[{'id': 1, 'name': name}])
        self_test = self
        setup(FakeClient(), create=True)


if __name__ == '__main__':
    unittest.main()
