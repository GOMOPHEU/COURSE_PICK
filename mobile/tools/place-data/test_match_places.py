import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

from match_places import ApiError, classify_match, distance, eligible, request_json, street, write_json


class MatchTests(unittest.TestCase):
    def setUp(self):
        self.kakao = {'id': '1', 'place_name': '테스트카페 안국점', 'x': '126.985', 'y': '37.576',
                      'road_address_name': '서울 종로구 율곡로 62'}

    def candidate(self, name='테스트카페 안국점', lng=126.985, address='서울특별시 종로구 율곡로 62', id='google-1'):
        return {'id': id, 'displayName': {'text': name}, 'formattedAddress': address,
                'location': {'latitude': 37.576, 'longitude': lng}}

    def test_exact_nearby(self):
        self.assertEqual(classify_match(self.kakao, [self.candidate()])['status'], 'matched')

    def test_address_formats(self):
        self.assertEqual(street('서울 종로구 종로1.2.3.4가동 인사동길 53-2'), ('인사동길', '53-2'))
        self.assertEqual(street('서울 종로구 율곡로 지하 62'), ('율곡로', '62'))

    def test_distant_same_name_not_matched(self):
        self.assertNotEqual(classify_match(self.kakao, [self.candidate(lng=127.1)])['status'], 'matched')

    def test_different_branch_not_matched(self):
        self.assertNotEqual(classify_match(self.kakao, [self.candidate(name='테스트카페 종로5가점', lng=126.990)])['status'], 'matched')

    def test_ambiguous_never_auto_accept(self):
        result = classify_match(self.kakao, [self.candidate(), self.candidate(id='google-2')])
        self.assertEqual(result['status'], 'review')
        self.assertIsNone(result['googlePlaceId'])

    def test_no_candidate(self):
        self.assertEqual(classify_match(self.kakao, [])['status'], 'unmatched')

    def test_different_jibun_address_exact_name_nearby(self):
        self.assertEqual(classify_match(self.kakao, [self.candidate(address='서울특별시 운니동 1')])['status'], 'matched')

    def test_missing_coordinates(self):
        p = self.candidate(); del p['location']
        self.assertEqual(classify_match(self.kakao, [p])['status'], 'unmatched')

    def test_auxiliary_places_filtered(self):
        for theme, name, category in [('walk', '공원 주차장', '여행 > 공원'), ('culture', '박물관 주차구역', '교통'), ('history', '궁궐찌개', '음식점')]:
            self.assertFalse(eligible(theme, {'place_name': name, 'category_name': category}))

    def test_geographic_distance(self):
        self.assertEqual(distance(37,127,37,127), 0)
        self.assertGreater(distance(37,127,38,127), 100000)

    @patch('match_places.urlopen')
    def test_auth_error_no_retry_no_secret(self, request):
        request.side_effect = HTTPError('https://test.invalid',403,'secret',{},None)
        with self.assertRaises(ApiError) as cm:
            request_json('google','https://test.invalid',{})
        self.assertEqual(str(cm.exception), 'google: 403')
        self.assertEqual(request.call_count, 1)

    @patch('match_places.time.sleep')
    @patch('match_places.urlopen')
    def test_network_error_not_unmatched(self, request, sleep):
        request.side_effect = URLError('secret')
        with self.assertRaises(ApiError):
            request_json('google','https://test.invalid',{})
        self.assertEqual(request.call_count, 3)

    def test_output_contains_no_place_content(self):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / 'test.json'
            write_json(p, classify_match(self.kakao, [self.candidate()]))
            text = p.read_text()
            self.assertNotIn('테스트카페', text)
            self.assertNotIn('126.985', text)
            self.assertEqual(json.loads(text)['googlePlaceId'], 'google-1')


if __name__ == '__main__':
    unittest.main()
