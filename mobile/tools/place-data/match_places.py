#!/usr/bin/env python3
"""Bounded demo matching; API payloads live in memory, only identifiers are saved."""
import argparse
from collections import Counter
from datetime import datetime
from difflib import SequenceMatcher
import json
import math
import os
from pathlib import Path
import re
import sys
import time
import unicodedata
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent


class ApiError(Exception):
    def __init__(self, service, status):
        self.service, self.status = service, status
        super().__init__(f"{service}: {status}")


def load_env(path):
    """Simple KEY=value parser; never executes shell expressions or logs values."""
    if not path.exists():
        return
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, value = line.removeprefix('export ').split('=', 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        os.environ.setdefault(key.strip(), value)


def request_json(service, url, headers, body=None):
    data = None if body is None else json.dumps(body).encode()
    for attempt in range(3):
        try:
            req = Request(url, data=data, headers=headers)
            with urlopen(req, timeout=20) as response:
                return json.load(response)
        except HTTPError as exc:
            if exc.code not in (429, 500, 502, 503, 504) or attempt == 2:
                raise ApiError(service, exc.code) from None
        except (URLError, TimeoutError, OSError):
            if attempt == 2:
                raise ApiError(service, 'network_error') from None
        except (ValueError, UnicodeError):
            raise ApiError(service, 'invalid_json') from None
        time.sleep(2 ** attempt)


def distance(lat1, lng1, lat2, lng2):
    a, b = math.radians(lat1), math.radians(lat2)
    h = math.sin((b-a)/2)**2 + math.cos(a)*math.cos(b)*math.sin(math.radians(lng2-lng1)/2)**2
    return 6371000 * 2 * math.asin(min(1, math.sqrt(h)))


def normalized(value):
    return re.sub(r'[^a-z0-9가-힣]', '', unicodedata.normalize('NFKC', value).lower())


def street(value):
    # Road + building number is useful across Seoul/서울특별시 and postal-code differences.
    matches = re.findall(r'([가-힣0-9·]+(?:대로|로|길))\s*(?:지하\s*)?(\d+(?:-\d+)?)', value)
    return matches[-1] if matches else None


def classify_match(kakao, candidates):
    ranked = []
    for place in candidates:
        loc = place.get('location', {})
        name = normalized(place.get('displayName', {}).get('text', ''))
        if not place.get('id') or not name or 'latitude' not in loc or 'longitude' not in loc:
            continue
        meters = distance(float(kakao['y']), float(kakao['x']), loc['latitude'], loc['longitude'])
        similarity = SequenceMatcher(None, normalized(kakao['place_name']), name).ratio()
        road = street(kakao.get('road_address_name', ''))
        same_address = road is not None and road == street(place.get('formattedAddress', ''))
        exact = normalized(kakao['place_name']) == name
        accepted = (exact and (meters <= 40 or (meters <= 150 and same_address))) or (similarity >= .88 and same_address and meters <= 100)
        score = similarity * .7 + max(0, 1-meters/300) * .2 + .1 * same_address
        ranked.append((score, place['id'], accepted, similarity, meters))
    ranked.sort(reverse=True)
    if not ranked:
        return {'status': 'unmatched', 'googlePlaceId': None, 'reason': 'no_candidates'}
    best = ranked[0]
    ambiguous = len(ranked) > 1 and best[0] - ranked[1][0] < .08
    if best[2] and not ambiguous:
        return {'status': 'matched', 'googlePlaceId': best[1], 'reason': 'name_and_distance_or_address'}
    plausible = any(r[3] >= .45 and r[4] <= 500 for r in ranked)
    return {
        'status': 'review' if plausible else 'unmatched', 'googlePlaceId': None,
        'candidatePlaceIds': [r[1] for r in ranked],
        'reason': 'ambiguous_or_insufficient_evidence' if plausible else 'no_plausible_candidate',
    }


class Matcher:
    def __init__(self):
        self.kakao_key = os.environ.get('KAKAO_REST_API_KEY', '')
        self.google_key = os.environ.get('GOOGLE_PLACES_API_KEY', '')
        if not self.kakao_key or not self.google_key:
            raise ValueError('KAKAO_REST_API_KEY and GOOGLE_PLACES_API_KEY are required')
        self.calls = Counter()

    def kakao(self, kind, params):
        self.calls['kakao'] += 1
        return request_json('kakao', f'https://dapi.kakao.com/v2/local/search/{kind}.json?{urlencode(params)}',
                            {'Authorization': f'KakaoAK {self.kakao_key}'})

    def google(self, place):
        self.calls['googleTextSearch'] += 1
        return request_json('google', 'https://places.googleapis.com/v1/places:searchText', {
            'X-Goog-Api-Key': self.google_key,
            'X-Goog-FieldMask': 'places.id,places.displayName,places.formattedAddress,places.location',
            'Content-Type': 'application/json',
        }, {
            'textQuery': f"{place['place_name']} {place.get('road_address_name') or place.get('address_name', '')}",
            'languageCode': 'ko', 'regionCode': 'KR', 'pageSize': 5,
            'locationBias': {'circle': {'center': {'latitude': float(place['y']), 'longitude': float(place['x'])}, 'radius': 500}},
        }).get('places', [])

    def collect(self, config):
        centers = self.kakao('keyword', {'query': config['centerQuery'], 'category_group_code': 'SW8'}).get('documents', [])
        centers = [p for p in centers if p['place_name'].startswith('안국역') and p.get('category_group_code') == 'SW8']
        if len(centers) != 1:
            raise ValueError('Cannot uniquely resolve Anguk station')
        center = centers[0]
        places, summaries = {}, []
        for spec in config['searches']:
            params = {'x': center['x'], 'y': center['y'], 'radius': config['radiusMeters'], 'sort': 'distance', 'size': 15}
            kind = 'category' if 'category' in spec else 'keyword'
            params['category_group_code' if kind == 'category' else 'query'] = spec.get('category', spec.get('query'))
            theme_ids, last = [], {}
            for page in range(1, 4):
                last = self.kakao(kind, {**params, 'page': page})
                for p in last.get('documents', []):
                    if not p.get('id') or not p.get('x') or not p.get('y'):
                        continue
                    if not eligible(spec['theme'], p):
                        continue
                    if distance(float(center['y']), float(center['x']), float(p['y']), float(p['x'])) > config['radiusMeters']:
                        continue
                    theme_ids.append(p['id'])
                    places.setdefault(p['id'], {**p, '_themes': set()})['_themes'].add(spec['theme'])
                if last.get('meta', {}).get('is_end', True):
                    break
            summaries.append({'theme': spec['theme'], 'query': spec.get('query', spec.get('category')),
                              'retrieved': len(set(theme_ids)), 'searchTotal': last.get('meta', {}).get('total_count', 0)})
        # Round-robin selection prevents cafe/food from consuming the entire run.
        selected = {}
        for theme in dict.fromkeys(s['theme'] for s in config['searches']):
            options = [p for p in places.values() if theme in p['_themes']]
            options.sort(key=lambda p: (p['place_name'] not in config.get('priorityNames', []), distance(float(center['y']), float(center['x']), float(p['y']), float(p['x'])), p['id']))
            for p in options[:config['maxPerTheme']]:
                selected[p['id']] = p
        return list(selected.values()), summaries


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    temporary.replace(path)


def eligible(theme, place):
    if theme not in ('food', 'cafe') and place.get('category_group_code') in ('FD6', 'CE7'):
        return False
    category = place.get('category_name', '')
    name = place.get('place_name', '')
    if any(s in name for s in ('주차', '화장실', '관리사무소')):
        return False
    if theme == 'culture' and not ('문화' in category or '미술관' in category or '박물관' in category or '갤러리' in category):
        return False
    if theme == 'walk':
        return ('공원' in category or '산책로' in category) and not any(s in name for s in ('입구', '주차', '화장실', '무료급식'))
    if theme == 'history':
        return '문화유적' in category or '고궁' in category or '궁궐' in category
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--env-file', type=Path, default=ROOT.parents[1] / '.env')
    parser.add_argument('--config', type=Path, default=ROOT / 'config.json')
    parser.add_argument('--output', type=Path, default=ROOT / 'data/place-mappings.json')
    parser.add_argument('--max-per-theme', type=int)
    parser.add_argument('--refresh', action='store_true', help='Recheck previously reviewed/unmatched IDs too')
    args = parser.parse_args()
    load_env(args.env_file)
    config = json.loads(args.config.read_text())
    if args.max_per_theme is not None:
        config['maxPerTheme'] = args.max_per_theme
    if not 1 <= config['maxPerTheme'] <= 45:
        raise ValueError('max-per-theme must be between 1 and 45')
    matcher = Matcher()
    places, coverage = matcher.collect(config)
    checked = datetime.now(ZoneInfo('Asia/Seoul')).date().isoformat()
    previous = json.loads(args.output.read_text()) if args.output.exists() else {}
    existing = {p['kakaoId']: p for p in previous.get('mappings', [])
                if p['status'] == 'matched' or (not args.refresh and p['status'] in ('review', 'unmatched'))}
    rows, failed = [], False
    print(f'Candidates: {len(places)}; Google requests at most {len(places)} (retries excluded)', flush=True)
    for index, p in enumerate(places, 1):
        if p['id'] in existing:
            rows.append({**existing[p['id']], 'themes': sorted(p['_themes'])})
            continue
        try:
            result = classify_match(p, matcher.google(p))
        except ApiError as exc:
            # Authentication, quota and network failures are never a no-match signal.
            result = {'status': 'api_error', 'googlePlaceId': None, 'reason': str(exc)}
            failed = True
        row = {'kakaoId': p['id'], 'themes': sorted(p['_themes']), 'checkedAt': checked, **result}
        rows.append(row)
        print(f"{index}/{len(places)} {p['id']} {result['status']}", flush=True)
        if result['status'] in ('unmatched', 'review') and p['_themes'] & {'history', 'culture', 'walk'}:
            print(f"  Official information review: {p['place_name']}", flush=True)
        if failed:
            break  # Avoid repeatedly charging/retrying a broken account.
        time.sleep(.15)
    # Keep earlier successful mappings during a partial run, and mark incomplete coverage.
    selected_ids = {p['id'] for p in places}
    rows.extend(v for k, v in existing.items() if k in selected_ids and k not in {r['kakaoId'] for r in rows})
    output = {'schemaVersion': 1, 'regionId': config['regionId'], 'radiusMeters': config['radiusMeters'],
              'generatedAt': checked, 'runComplete': not failed, 'coverage': 'bounded_demo_sample',
              'maxPerTheme': config['maxPerTheme'], 'searches': coverage,
              'requestCounts': dict(matcher.calls), 'mappings': rows}
    write_json(args.output, output)
    print(json.dumps(dict(Counter(r['status'] for r in rows)), ensure_ascii=False))
    return 2 if failed else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ApiError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)
