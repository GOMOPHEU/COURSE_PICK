import unittest
from audit_place_data import audit


class AuditTest(unittest.TestCase):
    def test_shared_candidates_are_flagged_without_becoming_matches(self):
        result = audit({'generatedAt': '2026-11-19', 'mappings': [
            {'kakaoId': '1', 'status': 'matched', 'googlePlaceId': 'google1'},
            {'kakaoId': '2', 'status': 'review', 'themes': ['history'], 'candidatePlaceIds': ['google1']},
            {'kakaoId': '3', 'status': 'unmatched', 'themes': ['cafe']},
        ]})
        self.assertEqual(result['candidateCollisionCount'], 1)
        self.assertEqual(result['identityChecksCompleted'], 0)
        self.assertEqual(result['places'][0]['savedStatus'], 'review')
        self.assertEqual(result['places'][1]['candidateCount'], 0)
        self.assertEqual(result['providerRequests'], 0)
