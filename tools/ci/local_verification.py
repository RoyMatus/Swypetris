"""Local evidence integrity and scope checks; not independent remote attestation."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import xml.etree.ElementTree as ET
from select_checks import select, changed_paths, SOURCE_GROUPS, FULL_SOURCES
from full_regression_proof import test_inventory



OPT_IN = {
    "ru.itoltec.swypetris.EnergyScenarioTest#gameplay",
    "ru.itoltec.swypetris.FireworkRenderingTest#exportsDeterministicBoundaryFrames",
    "ru.itoltec.swypetris.MaterialFrameEvidenceTest#realActivityFrames",
    "ru.itoltec.swypetris.SignedUpdateScenarioTest#liveGitHubDownloadChecksSignerBeforeInstaller",
}
QUALITY_TASKS = (":app:assembleDebug", ":app:assembleDebugAndroidTest", ":app:lintDebug", ":app:detekt", ":app:testDebugUnitTest", ":app:jacocoDebugUnitTestReport")


def validate_tasks(log):
    for task in QUALITY_TASKS:
        matches = re.findall(r"(?m)^> Task " + re.escape(task) + r"(?: ([A-Z-]+))?[ \t]*$", log)
        if len(matches) != 1 or matches[0] not in {"", "UP-TO-DATE", "FROM-CACHE"}:
            raise ValueError("Mandatory task missing or unsuccessful: " + task)


def validate_skips(cases):
    for case in cases:
        identity = case.get('classname', '') + '#' + case.get('name', '')
        if case.find('skipped') is not None and identity not in OPT_IN:
            raise ValueError('Unexpected skipped Android test: ' + identity)


def validate_jvm_inventory(root, build):
    cases = [c for report in (build / 'test-results/testDebugUnitTest').glob('TEST-*.xml') for c in ET.parse(report).getroot().iter('testcase')]
    actual = {}
    for case in cases:
        if case.find('skipped') is not None:
            raise ValueError('Unexpected skipped JVM test')
        actual.setdefault(case.get('classname'), set()).add(case.get('name'))
    expected_classes = set()
    for source in (root / 'app/src/test').rglob('*.kt'):
        text = source.read_text(encoding='utf-8-sig')
        if '@Test' not in text and 'BehaviorSpec' not in text:
            continue
        package = re.search(r'(?m)^package ([\w.]+)', text)
        if not package:
            raise ValueError('Unsupported JVM package')
        name = package[1] + '.' + source.stem
        expected_classes.add(name)
        methods = set(re.findall(r'@Test\s+fun\s+(\w+)', text))
        if 'BehaviorSpec' in text:
            leaves = re.findall(r'Then\("([^"\n]+)"\)', text)
            if not leaves or len(actual.get(name, set())) != len(leaves):
                raise ValueError('Incomplete Kotest leaf inventory: ' + name)
        elif len(methods) != len(re.findall(r'@Test\b', text)) or actual.get(name) != methods:
            raise ValueError('Incomplete JVM method inventory: ' + name)
    if set(actual) != expected_classes or not expected_classes:
        raise ValueError('JVM class inventory mismatch')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def clean(root, head, base):
    if git(root, 'rev-parse', 'HEAD') != head or git(root, 'status', '--porcelain'):
        raise ValueError('Verification requires the exact clean HEAD (including untracked files)')
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', base, head], check=True)


def selection(root, mode, head, base):
    previous = Path.cwd()
    os.chdir(root)
    try:
        return select(['unresolved-change-set']) if mode == 'Full' else select(changed_paths(base, head))
    finally:
        os.chdir(previous)


def validate_source_inventory(root):
    source_dir = root / 'app/src/main'
    prefix = 'java/ru/itoltec/swypetris/'
    sources = {path.relative_to(source_dir).as_posix() for path in source_dir.rglob('*.kt')}
    known = {prefix + name for name in SOURCE_GROUPS.keys() | FULL_SOURCES}
    unknown = sources - known
    if unknown:
        raise ValueError('Unclassified production Kotlin source: ' + ', '.join(sorted(unknown)))


def required(mode, selected):
    checks = {'whitespace'}
    if mode == 'Full' or any(selected[k] == 'true' for k in ('github_scripts', 'release_scripts', 'ci_scripts', 'windows_release_tools')):
        checks |= {'python', 'pester'}
    if selected['mode'] != 'none':
        checks |= {'source-inventory', 'quality', 'quality-reports', 'sonar', 'android', 'install-debug', 'launcher-smoke'}
        if selected['mode'] == 'full':
            checks |= {'picker-preflight', 'ui-preflight'}
    return checks


def validate(path, root, head, base, full=False):
    clean(root, head, base)
    doc = json.loads(path.read_text(encoding='utf-8-sig'))
    if doc.get('schema') != 1 or doc.get('head') != head or doc.get('base') != base or doc.get('result') != 'passed':
        raise ValueError('Evidence identity/result mismatch')
    mode = doc.get('mode')
    if mode not in {'PR', 'Full'} or (full and mode != 'Full'):
        raise ValueError('Evidence scope mismatch')
    if doc.get('platform') != 'Windows':
        raise ValueError('Mandatory verification requires Windows evidence')
    expected = selection(root, mode, head, base)
    if doc.get('selection') != expected:
        raise ValueError('Evidence selection mismatch')
    checks = doc.get('checks', [])
    if {c['name'] for c in checks} != required(mode, expected) or len(checks) != len(required(mode, expected)):
        raise ValueError('Missing or duplicated mandatory check')
    if any(c.get('result') != 'passed' for c in checks):
        raise ValueError('Required check did not pass')
    files = doc.get('files', {})
    if not files:
        raise ValueError('No evidence files')
    for name, digest in files.items():
        item = (path.parent / name).resolve()
        if not item.is_relative_to(path.parent.resolve()) or not item.is_file() or hashlib.sha256(item.read_bytes()).hexdigest() != digest:
            raise ValueError('Missing, unsafe or corrupt evidence file: ' + name)
    actual_files = {str(p.relative_to(path.parent)).replace('\\', '/') for p in path.parent.rglob('*') if p.is_file() and p != path}
    if actual_files != set(files):
        raise ValueError('Unbound or missing report files')
    if any(c.get('log') not in files for c in checks):
        raise ValueError('Missing check logs')
    if expected['mode'] != 'none':
        validate_source_inventory(root)
        for apk_dir in ('app/build/outputs/apk/debug/', 'app/build/outputs/apk/androidTest/debug/'):
            if not any(n.startswith(apk_dir) and n.endswith('.apk') for n in files):
                raise ValueError('Missing bound APK artifact: ' + apk_dir)
        names = [n for n in files if n.startswith('android/') and n.endswith('.xml')]
        cases = [c for n in names for c in ET.parse(path.parent / n).getroot().iter('testcase')]
        identities = [c.get('classname', '') + '#' + c.get('name', '') for c in cases]
        inventory = test_inventory(root)
        if expected['mode'] == 'selected':
            classes = set(expected['android'].split(','))
            inventory = {i for i in inventory if i.split('#')[0] in classes}
        if not cases or len(identities) != len(set(identities)) or set(identities) != inventory:
            raise ValueError('Android inventory incomplete or duplicate')
        if any(c.find('failure') is not None or c.find('error') is not None for c in cases):
            raise ValueError('Android tests failed')
        validate_skips(cases)
        mandatory = set(expected['android'].split(',')) if expected['mode'] == 'selected' else {'ru.itoltec.swypetris.SmokeTest'}
        passed = {c.get('classname') for c in cases if c.find('skipped') is None}
        if not mandatory <= passed:
            raise ValueError('Mandatory Android class has no passing case')
        validate_tasks((path.parent / 'quality.log').read_text(encoding='utf-8-sig'))
        from full_regression_proof import validate_quality
        validate_quality({'quality/' + name.removeprefix('app/build/'): (path.parent / name).read_bytes() for name in files if name.startswith(('app/build/reports/', 'app/build/test-results/'))})
        validate_jvm_inventory(root, path.parent / 'app/build')
    return doc


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['plan', 'validate', 'source-inventory'])
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--head', required=True)
    parser.add_argument('--base', required=True)
    parser.add_argument('--mode', default='PR', choices=['PR', 'Full'])
    parser.add_argument('--evidence', type=Path)
    parser.add_argument('--full', action='store_true')
    args = parser.parse_args()
    clean(args.root, args.head, args.base)
    if args.action == 'source-inventory':
        if selection(args.root, args.mode, args.head, args.base)['mode'] != 'none':
            validate_source_inventory(args.root)
    elif args.action == 'validate':
        validate(args.evidence, args.root, args.head, args.base, args.full)
    else:
        print(json.dumps(selection(args.root, args.mode, args.head, args.base)))


if __name__ == '__main__':
    main()
