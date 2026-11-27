#!/usr/bin/env python3
"""Compile and test with JDK 17+, downloading pinned Jackson jars if absent."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from urllib.request import urlopen

from match_places import ROOT, load_env


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deps-dir', type=Path, default=ROOT / '.cache/java')
    parser.add_argument('--live', action='store_true', help='Also make ONE real Google details request')
    args = parser.parse_args()
    args.deps_dir.mkdir(parents=True, exist_ok=True)
    for artifact, version in [('jackson-databind', '2.20.1'), ('jackson-core', '2.20.1'), ('jackson-annotations', '2.20')]:
        jar = args.deps_dir / f'{artifact}.jar'
        if not jar.exists():
            url = f'https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/{artifact}/{version}/{artifact}-{version}.jar'
            with urlopen(url, timeout=30) as response:
                jar.write_bytes(response.read())
    classes = ROOT / 'java/target/classes'
    classes.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join([str(classes), str(args.deps_dir.resolve() / '*')])
    sources = sorted(str(p) for p in (ROOT / 'java/src').rglob('*.java'))
    subprocess.run(['javac', '--release', '17', '-cp', classpath, '-d', str(classes), *sources], check=True)
    subprocess.run(['java', '-cp', classpath, 'kr.coursepick.places.PlacesTest'], check=True)
    if args.live:
        load_env(ROOT.parents[1] / '.env')
        data = json.loads((ROOT / 'data/place-mappings.json').read_text())
        place_id = next(r['googlePlaceId'] for r in data['mappings'] if r['status'] == 'matched')
        subprocess.run(['java', '-cp', classpath, 'kr.coursepick.places.LiveCheck', place_id], check=True)


if __name__ == '__main__':
    main()
