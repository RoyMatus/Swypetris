"""Explicit immutable local stage snapshots; never discover success from old logs.

This has the same local trust boundary as evidence.json: hashes detect accidental
changes, not an operator forging both a manifest and its expected digest.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import shutil

SCHEMA = 1
DEPENDENCIES = {
    'whitespace': (), 'source-inventory': (), 'python': (), 'pester': (),
    'quality': (), 'quality-reports': ('quality',),
    'sonar': ('quality', 'quality-reports'), 'install-debug': ('quality',),
    'launcher-smoke': ('install-debug',), 'android': ('quality', 'launcher-smoke'),
}
RUNTIME_KEYS = {'python', 'powershell', 'java', 'adb', 'sdkApi', 'device', 'sonar', 'maxWorkers'}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write(path, doc):
    path.write_text(json.dumps(doc, indent=2, sort_keys=True, allow_nan=False) + '\n', encoding='utf-8')


def bound(directory, name):
    if not isinstance(name, str) or not name or '\\' in name:
        raise ValueError('Invalid checkpoint report path')
    item = (directory / name).resolve()
    if Path(name).is_absolute() or not item.is_relative_to(directory.resolve()):
        raise ValueError('Unsafe checkpoint report path')
    return item


def fingerprint(root, head, base, mode, runtime):
    from local_verification import clean, git, selection, tree
    clean(root, head, base)
    if mode not in {'PR', 'Full'} or not isinstance(runtime, dict):
        raise ValueError('Invalid checkpoint fingerprint scope/runtime')
    if not {'python', 'powershell'} <= runtime.keys() or runtime.keys() - RUNTIME_KEYS:
        raise ValueError('Missing or unsupported checkpoint runtime metadata')
    selected = selection(root, mode, head, base)
    if selected['mode'] != 'none' and not {'java', 'adb', 'sdkApi', 'device', 'sonar', 'maxWorkers'} <= runtime.keys():
        raise ValueError('Incomplete Android checkpoint runtime metadata')
    if any(value is None or value == '' for value in runtime.values()):
        raise ValueError('Incomplete checkpoint runtime metadata')
    inputs = {}
    for name in git(root, 'ls-files').splitlines():
        if name.startswith(('tools/', 'gradle/', 'config/', '.agents/skills/ci-policy/', '.agents/skills/android-test-selection/')) or name in {'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle.kts'}:
            item = bound(root, name)
            if item.is_file():
                inputs[name] = digest(item)
    return dict(schema=SCHEMA, head=head, base=base, sourceTree=tree(root, head),
                mode=mode, selection=selected, platform='Windows', inputs=inputs, runtime=runtime)


def validate_fingerprint(doc):
    if not isinstance(doc, dict) or doc.get('schema') != SCHEMA or doc.get('platform') != 'Windows' or doc.get('mode') not in {'PR', 'Full'}:
        raise ValueError('Invalid checkpoint fingerprint')
    for name in ('head', 'base', 'sourceTree'):
        if not isinstance(doc.get(name), str) or len(doc[name]) != 40 or any(c not in '0123456789abcdef' for c in doc[name]):
            raise ValueError('Invalid checkpoint source identity')
    if not isinstance(doc.get('selection'), dict) or not isinstance(doc.get('inputs'), dict) or not isinstance(doc.get('runtime'), dict):
        raise ValueError('Incomplete checkpoint fingerprint')


def validate_dependencies(stage, dependencies):
    if stage not in DEPENDENCIES or not isinstance(dependencies, dict) or set(dependencies) != set(DEPENDENCIES[stage]):
        raise ValueError('Incomplete checkpoint stage dependencies')
    if any(not isinstance(value, str) or len(value) != 64 or any(c not in '0123456789abcdef' for c in value) for value in dependencies.values()):
        raise ValueError('Invalid checkpoint dependency hash')


def record(store, identity, stage, check, reports, files, dependencies=None, origin=None):
    """Record passed/failed attempts without overwriting previous diagnostics."""
    validate_fingerprint(identity)
    dependencies = dependencies or {}
    validate_dependencies(stage, dependencies)
    if check.get('name') != stage or check.get('result') not in {'passed', 'failed'} or 'reuse' in check:
        raise ValueError('Invalid original checkpoint result')
    duration = check.get('durationSeconds')
    if type(duration) not in (int, float) or not math.isfinite(duration) or duration < 0:
        raise ValueError('Invalid original checkpoint duration')
    if not isinstance(files, list) or not files or len(files) != len(set(files)) or check.get('log') not in files:
        raise ValueError('Missing or duplicated checkpoint reports')
    if store.exists():
        raise ValueError('Checkpoint snapshot already exists')
    store.mkdir(parents=True)
    try:
        hashes = {}
        for name in files:
            source = bound(reports, name)
            if not source.is_file():
                raise ValueError('Missing checkpoint report: ' + name)
            target = bound(store / 'files', name)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            hashes[name] = digest(target)
        path = store / 'checkpoint.json'
        write(path, dict(schema=SCHEMA, fingerprint=identity, stage=stage, check=check,
                         files=hashes, dependencies=dependencies, origin=origin or str(reports.resolve())))
        return dict(checkpoint=str(path), checkpointSha256=digest(path))
    except BaseException:
        shutil.rmtree(store)
        raise


def validate_snapshot(path, expected_hash, identity, dependencies):
    if not path.is_file() or digest(path) != expected_hash:
        raise ValueError('Missing or altered checkpoint manifest')
    doc = load(path)
    validate_fingerprint(doc.get('fingerprint'))
    if doc.get('schema') != SCHEMA or doc['fingerprint'] != identity:
        raise ValueError('Incompatible checkpoint inputs/runtime')
    stage = doc.get('stage')
    validate_dependencies(stage, doc.get('dependencies'))
    if doc['dependencies'] != dependencies:
        raise ValueError('Incompatible checkpoint dependency results')
    check = doc.get('check', {})
    if check.get('name') != stage or check.get('result') != 'passed' or 'reuse' in check:
        raise ValueError('Checkpoint stage did not pass')
    duration = check.get('durationSeconds')
    if type(duration) not in (int, float) or not math.isfinite(duration) or duration < 0:
        raise ValueError('Invalid checkpoint original duration')
    files = doc.get('files', {})
    if not isinstance(files, dict) or not files or check.get('log') not in files:
        raise ValueError('Incomplete checkpoint reports')
    for name, value in files.items():
        item = bound(path.parent / 'files', name)
        if not item.is_file() or digest(item) != value:
            raise ValueError('Missing or altered checkpoint report: ' + name)
    actual = {p.relative_to(path.parent / 'files').as_posix() for p in (path.parent / 'files').rglob('*') if p.is_file()}
    if actual != set(files):
        raise ValueError('Unbound checkpoint report files')
    return doc


def reuse(path, expected_hash, identity, destination, dependencies=None):
    """Export only an explicitly selected, hash-bound passed snapshot."""
    dependencies = dependencies or {}
    doc = validate_snapshot(path, expected_hash, identity, dependencies)
    target = destination / 'checkpoints' / doc['stage']
    if target.exists():
        raise ValueError('Destination checkpoint already exists')
    # Preflight every collision before mutating the destination.
    for name, value in doc['files'].items():
        report = bound(destination, name)
        if report.exists() and (not report.is_file() or digest(report) != value):
            raise ValueError('Conflicting checkpoint export: ' + name)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(path.parent, target)
    for name in doc['files']:
        report = bound(destination, name)
        report.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(bound(path.parent / 'files', name), report)
    return dict(doc['check'], reuse=dict(checkpoint=f'checkpoints/{doc["stage"]}/checkpoint.json',
                checkpointSha256=expected_hash, origin=doc['origin'], originalDurationSeconds=doc['check']['durationSeconds']))


def validate_reused_checks(path, doc):
    """Additional integrity validation; final schema-one gate remains mandatory."""
    checks = {check['name']: check for check in doc['checks']}

    def validate_stage(stage, identity):
        dependency_hashes = {}
        for name in DEPENDENCIES.get(stage, ()):
            dependency_path = f'checkpoints/{name}/checkpoint.json'
            if dependency_path not in doc['files']:
                raise ValueError('Missing reused checkpoint dependency')
            dependency_hashes[name] = doc['files'][dependency_path]
            validate_stage(name, identity)
        relative = f'checkpoints/{stage}/checkpoint.json'
        if relative not in doc['files'] or stage not in checks:
            raise ValueError('Missing reused checkpoint stage')
        original = validate_snapshot(bound(path.parent, relative), doc['files'][relative], identity, dependency_hashes)
        if {k: v for k, v in checks[stage].items() if k != 'reuse'} != original['check']:
            raise ValueError('Reused checkpoint result/provenance changed')
        for name, value in original['files'].items():
            if doc['files'].get(name) != value:
                raise ValueError('Reused checkpoint exported report changed')
        return original

    for check in doc['checks']:
        claim = check.get('reuse')
        if claim is None:
            continue
        if not isinstance(claim, dict) or claim.get('checkpoint') not in doc['files'] or doc['files'][claim['checkpoint']] != claim.get('checkpointSha256'):
            raise ValueError('Unbound reuse checkpoint')
        if 'fingerprint.json' not in doc['files']:
            raise ValueError('Missing bound current checkpoint fingerprint')
        current_identity = load(path.parent / 'fingerprint.json')
        validate_fingerprint(current_identity)
        checkpoint = bound(path.parent, claim['checkpoint'])
        original = load(checkpoint)
        identity = original.get('fingerprint', {})
        if identity != current_identity:
            raise ValueError('Incompatible current checkpoint fingerprint')
        # Promotion intentionally changes head to the identical-tree main commit;
        # copied original evidence still validates its exact source identity.
        expected_head = doc.get('promotion', {}).get('sourceHead', doc['head'])
        if any(identity.get(key) != value for key, value in dict(head=expected_head, base=doc['base'], mode=doc['mode'], selection=doc['selection']).items()):
            raise ValueError('Reuse checkpoint source/scope mismatch')
        if claim['checkpoint'] != f'checkpoints/{check["name"]}/checkpoint.json':
            raise ValueError('Reuse checkpoint stage/path mismatch')
        original = validate_stage(check['name'], identity)
        if {k: v for k, v in check.items() if k != 'reuse'} != original['check'] or claim.get('originalDurationSeconds') != original['check']['durationSeconds'] or claim.get('origin') != original['origin']:
            raise ValueError('Reused checkpoint result/provenance changed')
        for name, value in original['files'].items():
            if doc['files'].get(name) != value:
                raise ValueError('Reused checkpoint exported report changed')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=['fingerprint', 'record', 'reuse'])
    for name in ('root', 'runtime', 'fingerprint', 'store', 'check', 'reports', 'files', 'dependencies', 'checkpoint', 'destination'):
        parser.add_argument('--' + name, type=Path)
    for name in ('head', 'base', 'mode', 'stage', 'checkpoint-sha256', 'origin'):
        parser.add_argument('--' + name)
    args = parser.parse_args()
    if args.action == 'fingerprint':
        result = fingerprint(args.root, args.head, args.base, args.mode, load(args.runtime))
    elif args.action == 'record':
        result = record(args.store, load(args.fingerprint), args.stage, load(args.check), args.reports,
                        load(args.files), load(args.dependencies) if args.dependencies else {}, args.origin)
    else:
        result = reuse(args.checkpoint, args.checkpoint_sha256, load(args.fingerprint), args.destination,
                       load(args.dependencies) if args.dependencies else {})
    print(json.dumps(result, allow_nan=False))


if __name__ == '__main__':
    main()
