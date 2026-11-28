from datetime import date
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent / 'data'


class DataChecks(unittest.TestCase):
    def test_exclusions_and_ambiguous_park_hours(self):
        facilities = json.loads((ROOT / 'official-hours.json').read_text())['facilities']
        excluded = {r['kakaoId'] for r in facilities if r.get('recommendation', {}).get('exclude')}
        config = Path(__file__).resolve().parents[3] / 'server/config/coursepick.json'
        server_excluded = set(json.loads(config.read_text())['candidateSearch']['excludedPlaceIds'])
        self.assertLessEqual(excluded, server_excluded)
        park = next(r for r in facilities if r['kakaoId'] == '919042739')
        self.assertIsNone(park['hours'])
        self.assertIn('hours', park['userReport'])

    def test_matching_identifiers_and_storage_scope(self):
        data = json.loads((ROOT / 'place-mappings.json').read_text())
        self.assertEqual(data['radiusMeters'], 1300)
        self.assertTrue(data['runComplete'])
        ids, google_ids = set(), set()
        for row in data['mappings']:
            self.assertTrue(row['kakaoId'].isdigit())
            self.assertNotIn(row['kakaoId'], ids)
            ids.add(row['kakaoId'])
            self.assertLessEqual(set(row), {'kakaoId', 'googlePlaceId', 'candidatePlaceIds', 'themes', 'checkedAt', 'status', 'reason'})
            date.fromisoformat(row['checkedAt'])
            self.assertIn(row['status'], {'matched', 'review', 'unmatched', 'api_error'})
            if row['status'] == 'matched':
                self.assertTrue(row['googlePlaceId'])
                self.assertNotIn(row['googlePlaceId'], google_ids)
                google_ids.add(row['googlePlaceId'])
            else:
                self.assertIsNone(row['googlePlaceId'])

    def test_every_unmatched_facility_has_explicit_research_state(self):
        mappings = json.loads((ROOT / 'place-mappings.json').read_text())['mappings']
        expected = {r['kakaoId'] for r in mappings if r['status'] == 'unmatched' and set(r['themes']) & {'history','culture','walk'}}
        facilities = json.loads((ROOT / 'official-hours.json').read_text())['facilities']
        self.assertEqual(expected, {r['kakaoId'] for r in facilities})
        for row in facilities:
            date.fromisoformat(row['checkedAt'])
            self.assertIn('hours', row)
            self.assertIn('closedDays', row)
            if row['verificationStatus'] == 'verified':
                self.assertTrue(row['sources'])
                self.assertIsNotNone(row['hours'])
            elif row['verificationStatus'] == 'user_reported':
                self.assertIsNotNone(row['hours'])
                self.assertEqual(row['userReport']['sourceType'], 'user_provided')
                self.assertFalse(row['userReport']['independentlyVerified'])
            else:
                self.assertIsNone(row['hours'])
            for source in row['sources']:
                self.assertTrue(source['url'].startswith('https://'))
                date.fromisoformat(source['checkedAt'])


if __name__ == '__main__':
    unittest.main()
