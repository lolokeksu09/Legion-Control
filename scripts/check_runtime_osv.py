#!/usr/bin/env python3
import unittest
from scan_runtime_osv import scan

P1 = "pkg:maven/org.example/library@1.0"
P2 = "pkg:cargo/example@2.0"

class OsvScanTest(unittest.TestCase):
    def test_empty_results_are_clean(self):
        self.assertEqual({P1: [], P2: []}, scan([P1, P2], lambda q: {"results": [{}, {}]}))

    def test_findings_and_pagination_preserve_identity(self):
        responses = iter([
            {"results": [{"vulns": [{"id":"RUSTSEC-1"}]}, {"next_page_token":"next"}]},
            {"results": [{"vulns":[{"id":"GHSA-1"},{"id":"GHSA-1"}]}]}
        ])
        def query(q):
            if len(q) == 1:
                self.assertEqual({"package":{"purl":P1},"page_token":"next"}, q[0])
            return next(responses)
        self.assertEqual({P1:["GHSA-1"],P2:["RUSTSEC-1"]}, scan([P1,P2],query))

    def test_incomplete_and_malformed_results_fail(self):
        for response in ({"results":[]}, {"results":[{"error":"bad"}]}, {"results":[{"vulns":None}]}):
            with self.assertRaises(ValueError):
                scan([P1], lambda q: response)

    def test_repeat_token_and_network_failure_are_not_clean(self):
        with self.assertRaises(ValueError):
            scan([P1], lambda q: {"results":[{"next_page_token":"loop"}]})
        def offline(q):
            raise OSError("offline")
        with self.assertRaises(OSError):
            scan([P1], offline)

if __name__ == "__main__":
    unittest.main()
