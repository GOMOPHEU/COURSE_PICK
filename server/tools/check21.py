#!/usr/bin/env python3
"""Measure shopping matching/hours coverage from the server's actual candidate policy.

Only aggregate counts are emitted. Provider names, coordinates and hours stay in memory.
Uses the existing area-5 matching implementation, not a second matching algorithm.
"""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import random
import sys
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'mobile/tools/place-data'))
from match_places import classify_match, load_env, request_json


def bucket(category):
    parts = {s.strip() for s in category.split('>')}
    if '테마거리' in parts:
        return 'street'
    if '시장' in parts:
        return 'market'
    if '복합쇼핑몰' in parts:
        return 'mall'
    return 'small_shop'


def summarize(rows):
    report = {}
    for kind in ('street', 'market', 'small_shop', 'mall'):
        group = [r for r in rows if r['kind'] == kind]
        matched = sum(r['status'] == 'matched' for r in group)
        hours = sum(r['hours'] for r in group)
        report[kind] = {
            'sampled': len(group), 'matched': matched,
            'review': sum(r['status'] == 'review' for r in group),
            'unmatched': sum(r['status'] == 'unmatched' for r in group),
            'hoursPresent': hours,
            'matchRate': matched / len(group) if group else None,
            'hoursRateAmongMatched': hours / matched if matched else None,
            'hoursRateAmongSampled': hours / len(group) if group else None,
        }
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', default='http://localhost:8080')
    parser.add_argument('--env-file', type=Path, default=ROOT / 'mobile/.env')
    parser.add_argument('--lat', type=float, default=37.5765)
    parser.add_argument('--lng', type=float, default=126.9855)
    parser.add_argument('--per-kind', type=int, default=5)
    parser.add_argument('--seed', type=int, default=21)
    args = parser.parse_args()
    if not 1 <= args.per_kind <= 10:
        parser.error('--per-kind must be 1..10 (at most 40 Google searches)')
    if urlparse(args.server).scheme not in ('http', 'https'):
        parser.error('--server must be an HTTP(S) URL')
    load_env(args.env_file)
    google_key = os.environ.get('GOOGLE_PLACES_API_KEY') or os.environ.get('GOOGLE_PLACES_KEY')
    if not google_key:
        parser.error('GOOGLE_PLACES_API_KEY or GOOGLE_PLACES_KEY is required')
    calls = Counter()
    response = request_json('coursepick', args.server.rstrip('/') + '/candidates',
                            {'Content-Type': 'application/json'},
                            {'lat': args.lat, 'lng': args.lng, 'theme': 'shopping', 'companion': 'solo'})
    available = response['candidates']
    rng = random.Random(args.seed)
    sample = []
    for kind in ('street', 'market', 'small_shop', 'mall'):
        choices = sorted([p for p in available if bucket(p['category']) == kind], key=lambda p: p['kakaoId'])
        sample.extend(rng.sample(choices, min(args.per_kind, len(choices))))
    rows = []
    for p in sample:
        kakao = {'place_name': p['name'], 'x': p['lng'], 'y': p['lat'], 'address_name': p['address']}
        calls['googleTextSearch'] += 1
        results = request_json('google', 'https://places.googleapis.com/v1/places:searchText', {
            'X-Goog-Api-Key': google_key,
            'X-Goog-FieldMask': 'places.id,places.displayName,places.formattedAddress,places.location,places.regularOpeningHours,places.currentOpeningHours',
            'Content-Type': 'application/json',
        }, {
            'textQuery': f"{p['name']} {p['address']}", 'languageCode': 'ko', 'regionCode': 'KR', 'pageSize': 5,
            'locationBias': {'circle': {'center': {'latitude': p['lat'], 'longitude': p['lng']}, 'radius': 500}},
        }).get('places', [])
        match = classify_match(kakao, results)
        found = next((g for g in results if g['id'] == match.get('googlePlaceId')), {})
        hours = any(isinstance(found.get(field), dict) and isinstance(found[field].get('periods'), list)
                    for field in ('regularOpeningHours', 'currentOpeningHours'))
        rows.append({'kind': bucket(p['category']), 'status': match['status'], 'hours': hours})
    print(json.dumps({'seed': args.seed, 'radiusMeters': response['radiusMeters'],
                      'availableByKind': dict(Counter(bucket(p['category']) for p in available)),
                      'coverage': summarize(rows), 'requestCounts': dict(calls),
                      'note': 'Current candidate sample only; not a census. Hours presence does not confirm visit availability.'},
                     ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
