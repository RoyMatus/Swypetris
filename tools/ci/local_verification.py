"""Local evidence integrity and scope checks; not independent remote attestation."""
import argparse
import hashlib
import json
import math
import os
import re
import shutil
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
    "ru.itoltec.swypetris.MenuFrameEvidenceTest#realMenuFrames",
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
    return checks


def validate(path, root, head, base, full=False, check_clean=True):
    if check_clean:
        clean(root, head, base)
    doc = json.loads(path.read_text(encoding='utf-8-sig'))
    if doc.get('schema') not in (1, 2) or doc.get('head') != head or doc.get('base') != base or doc.get('result') != 'passed':
        raise ValueError('Evidence identity/result mismatch')
    mode = doc.get('mode')
    if mode not in {'PR', 'Full'} or (full and mode != 'Full'):
        raise ValueError('Evidence scope mismatch')
    if doc['schema'] == 2 and mode != 'Full':
        raise ValueError('Promoted evidence must cover Full regression')
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
    from verification_checkpoint import validate_reused_checks
    validate_reused_checks(path, doc)
    if doc['schema'] == 2:
        validate_promotion(path, root, doc)
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


def tree(root, commit):
    return git(root, 'rev-parse', commit + '^{tree}')


def validate_promotion(path, root, doc):
    proof = doc.get('promotion') or {}
    source_path = path.parent / 'source/evidence.json'
    if proof.get('sourceEvidence') != 'source/evidence.json' or not source_path.is_file():
        raise ValueError('Missing original promotion evidence')
    source = json.loads(source_path.read_text(encoding='utf-8-sig'))
    if source.get('schema') != 1 or source.get('mode') != 'Full' or source.get('result') != 'passed':
        raise ValueError('Promotion requires passed version-one Full evidence')
    if source.get('head') == doc['head'] or source.get('base') != doc['base'] or type(proof.get('prNumber')) is not int or proof['prNumber'] <= 0:
        raise ValueError('Invalid promotion identity')
    if proof.get('sourceHead') != source['head'] or proof.get('sourceBase') != source['base'] or proof.get('destinationHead') != doc['head']:
        raise ValueError('Promotion provenance mismatch')
    if proof.get('sourceEvidenceSha256') != hashlib.sha256(source_path.read_bytes()).hexdigest():
        raise ValueError('Original promotion evidence changed')
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', source['base'], source['head']], check=True)
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', source['base'], doc['head']], check=True)
    source_tree, destination_tree = tree(root, source['head']), tree(root, doc['head'])
    if source_tree != destination_tree or proof.get('sourceTree') != source_tree or proof.get('destinationTree') != destination_tree:
        raise ValueError('Promotion requires identical Git trees')
    validate(source_path, root, source['head'], source['base'], full=True, check_clean=False)
    if {c['name']: c for c in source['checks'] if c['name'] != 'sonar'} != {c['name']: c for c in doc['checks'] if c['name'] != 'sonar'}:
        raise ValueError('Reused check identity changed')
    if any(doc['files'].get(name) != digest for name, digest in source['files'].items() if name != 'sonar.log'):
        raise ValueError('Reused report hash changed')
    if doc['files'].get('sonar.log') == source['files'].get('sonar.log'):
        raise ValueError('Main Sonar analysis must be fresh')
    sonar = next(c for c in doc['checks'] if c['name'] == 'sonar')
    if not isinstance(sonar.get('durationSeconds'), (int, float)) or not math.isfinite(sonar['durationSeconds']) or sonar['durationSeconds'] < 0:
        raise ValueError('Invalid fresh main Sonar duration')


def preflight_promotion(source_path, root, head, base):
    clean(root, head, base)
    source = json.loads(source_path.read_text(encoding='utf-8-sig'))
    if source.get('schema') != 1 or source.get('mode') != 'Full' or source.get('base') != base:
        raise ValueError('Only version-one Full evidence from the stated base can be promoted')
    subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', base, source['head']], check=True)
    if tree(root, source['head']) != tree(root, head):
        raise ValueError('PR and main Git trees differ')
    validate(source_path, root, source['head'], base, full=True, check_clean=False)
    return source


def promote(source_path, root, head, base, destination, pr_number, sonar_log, sonar_seconds):
    source = preflight_promotion(source_path, root, head, base)
    if type(pr_number) is not int or pr_number <= 0 or not math.isfinite(sonar_seconds) or sonar_seconds < 0:
        raise ValueError('Invalid promotion input')
    source_dir, target_dir = source_path.parent.resolve(), destination.resolve()
    if target_dir.is_relative_to(source_dir) or source_dir.is_relative_to(target_dir):
        raise ValueError('Promotion source and output must be disjoint')
    if destination.exists():
        raise ValueError('Promotion output already exists')
    destination.mkdir(parents=True)
    try:
        shutil.copytree(source_path.parent, destination / 'source')
        for name in source['files']:
            target = destination / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_path.parent / name, target)
        shutil.copy2(sonar_log, destination / 'sonar.log')
        checks = [dict(check, durationSeconds=sonar_seconds) if check['name'] == 'sonar' else check for check in source['checks']]
        files = {str(item.relative_to(destination)).replace('\\', '/'): hashlib.sha256(item.read_bytes()).hexdigest()
                 for item in destination.rglob('*') if item.is_file()}
        doc = dict(source, schema=2, head=head, checks=checks, files=files,
                   promotion=dict(prNumber=pr_number, sourceEvidence='source/evidence.json',
                                  sourceEvidenceSha256=hashlib.sha256((destination / 'source/evidence.json').read_bytes()).hexdigest(),
                                  sourceHead=source['head'], sourceBase=base, destinationHead=head,
                                  sourceTree=tree(root, source['head']), destinationTree=tree(root, head)))
        output = destination / 'evidence.json'
        output.write_text(json.dumps(doc, indent=2) + '\n', encoding='utf-8')
        validate(output, root, head, base, full=True)
        return output
    except BaseException:
        shutil.rmtree(destination)
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['plan', 'validate', 'source-inventory', 'preflight-promotion', 'promote'])
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--head', required=True)
    parser.add_argument('--base', required=True)
    parser.add_argument('--mode', default='PR', choices=['PR', 'Full'])
    parser.add_argument('--evidence', type=Path)
    parser.add_argument('--full', action='store_true')
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--pr-number', type=int)
    parser.add_argument('--sonar-log', type=Path)
    parser.add_argument('--sonar-seconds', type=float)
    args = parser.parse_args()
    clean(args.root, args.head, args.base)
    if args.action == 'source-inventory':
        if selection(args.root, args.mode, args.head, args.base)['mode'] != 'none':
            validate_source_inventory(args.root)
    elif args.action == 'validate':
        validate(args.evidence, args.root, args.head, args.base, args.full)
    elif args.action == 'preflight-promotion':
        if not args.evidence:
            parser.error('preflight-promotion requires evidence')
        preflight_promotion(args.evidence, args.root, args.head, args.base)
    elif args.action == 'promote':
        if not all((args.evidence, args.destination, args.pr_number, args.sonar_log, args.sonar_seconds is not None)):
            parser.error('promote requires evidence, destination, PR number, Sonar log and duration')
        print(promote(args.evidence, args.root, args.head, args.base, args.destination,
                      args.pr_number, args.sonar_log, args.sonar_seconds))
    else:
        print(json.dumps(selection(args.root, args.mode, args.head, args.base)))


if __name__ == '__main__':
    main()
