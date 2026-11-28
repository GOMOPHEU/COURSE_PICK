import unittest
from check21 import bucket, summarize


class Check21Test(unittest.TestCase):
    def test_categories(self):
        self.assertEqual(bucket('여행 > 관광명소 > 테마거리'), 'street')
        self.assertEqual(bucket('가정,생활 > 시장'), 'market')
        self.assertEqual(bucket('가정,생활 > 문구 > 디자인문구'), 'small_shop')
        self.assertEqual(bucket('가정,생활 > 복합쇼핑몰'), 'mall')

    def test_rates_have_explicit_denominators_and_no_sample_is_unknown(self):
        rows = [{'kind': 'street', 'status': 'matched', 'hours': True},
                {'kind': 'street', 'status': 'matched', 'hours': False},
                {'kind': 'street', 'status': 'review', 'hours': False},
                {'kind': 'street', 'status': 'unmatched', 'hours': False}]
        report = summarize(rows)
        self.assertEqual(report['street']['matchRate'], .5)
        self.assertEqual(report['street']['hoursRateAmongMatched'], .5)
        self.assertEqual(report['street']['hoursRateAmongSampled'], .25)
        self.assertIsNone(report['market']['matchRate'])
        self.assertIsNone(report['market']['hoursRateAmongMatched'])
