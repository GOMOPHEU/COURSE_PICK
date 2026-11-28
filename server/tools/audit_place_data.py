#!/usr/bin/env python3
"""Offline ID audit. Does not call APIs or certify that two IDs are the same place."""
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def audit(data):
    rows = data['mappings']
    owners = {}
    for row in rows:
        if row['status'] == 'matched':
            owners.setdefault(row['googlePlaceId'], []).append(row['kakaoId'])
    pending = []
    for row in rows:
        if row['status'] not in ('review', 'unmatched'):
            continue
        candidates = row.get('candidatePlaceIds', [])
        collisions = [{'googlePlaceId': gid, 'matchedKakaoIds': owners[gid]}
                      for gid in candidates if gid in owners]
        pending.append({
            'kakaoId': row['kakaoId'], 'themes': row['themes'], 'savedStatus': row['status'],
            'candidatePlaceIds': candidates, 'candidateCount': len(candidates),
            'alreadyAssignedCandidates': collisions,
            'identityVerification': 'pending',
            'nextAction': 'check_parent_or_duplicate_before_linking' if collisions else 'compare_current_name_branch_address_coordinates',
        })
    return {
        'schemaVersion': 1, 'auditDate': '2026-11-26', 'auditScope': 'saved_identifiers_only',
        'sourceGeneratedAt': data['generatedAt'], 'providerRequests': 0,
        'counts': dict(Counter(row['status'] for row in rows)),
        'pendingCount': len(pending),
        'candidateCollisionCount': sum(bool(row['alreadyAssignedCandidates']) for row in pending),
        'duplicateConfirmedGoogleIds': {gid: ids for gid, ids in owners.items() if len(ids) > 1},
        'identityChecksCompleted': 0,
        'reasonLiveChecksDeferred': 'free_only_requested_and_billing_usage_not_available',
        'places': pending,
    }


if __name__ == '__main__':
    data = json.loads((ROOT / 'mobile/tools/place-data/data/place-mappings.json').read_text())
    print(json.dumps(audit(data), ensure_ascii=False, indent=2))
