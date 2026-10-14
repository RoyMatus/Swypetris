"""Behavior RED: a reuse claim cannot bypass immutable stage validation."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import os
import shutil
import subprocess
import sys
from unittest.mock import patch
import local_verification as gate
import verification_checkpoint as checkpoint

class ReuseEvidenceTests(unittest.TestCase):
    def test_unbound_reuse_claim_is_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'whitespace.log').write_bytes(b'passed')
            selected = dict(mode='none', android='', jvm='', github_scripts='false', release_scripts='false', ci_scripts='false', windows_release_tools='false')
            doc = dict(schema=1, platform='Windows', head='a'*40, base='b'*40,
                       mode='PR', selection=selected, result='passed',
                       checks=[dict(name='whitespace', result='passed', log='whitespace.log', durationSeconds=1,
                                    reuse=dict(checkpoint='missing.json', checkpointSha256='0'*64))],
                       files={'whitespace.log': hashlib.sha256(b'passed').hexdigest()})
            path = root / 'evidence.json'
            path.write_text(json.dumps(doc))
            with patch.object(gate, 'clean'), patch.object(gate, 'selection', return_value=selected):
                with self.assertRaisesRegex(ValueError, 'checkpoint'):
                    gate.validate(path, root, doc['head'], doc['base'])


class CheckpointTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.reports = self.root / 'run'
        self.reports.mkdir()
        (self.reports / 'python.log').write_bytes(b'tests passed')
        self.identity = dict(schema=1, head='a'*40, base='b'*40, sourceTree='c'*40, mode='PR',
                             selection={'mode': 'none'}, platform='Windows',
                             inputs={'tools/ci/local_verification.py': 'd'*64}, runtime={'python': '3.14', 'powershell': '7.5'})
        self.check = dict(name='python', result='passed', durationSeconds=12.25, log='python.log')
        self.store = self.root / 'snapshot'

    def record(self, **kwargs):
        return checkpoint.record(self.store, self.identity, 'python', self.check, self.reports, ['python.log'], **kwargs)

    def test_explicit_reuse_preserves_origin_duration_and_reports(self):
        manifest = self.record()
        destination = self.root / 'resumed'
        result = checkpoint.reuse(Path(manifest['checkpoint']), manifest['checkpointSha256'], self.identity, destination)
        self.assertEqual(result['durationSeconds'], 12.25)
        self.assertEqual(result['reuse']['originalDurationSeconds'], 12.25)
        self.assertEqual(result['reuse']['origin'], str(self.reports.resolve()))
        self.assertEqual((destination / 'python.log').read_bytes(), b'tests passed')
        self.assertTrue((destination / result['reuse']['checkpoint']).is_file())

    def test_fingerprint_binds_source_selection_tool_bytes_and_runtime(self):
        (self.root / 'tools').mkdir()
        (self.root / 'tools/check.py').write_bytes(b'first tool')
        selected = {'mode': 'none'}
        with patch.object(gate, 'clean'), patch.object(gate, 'git', return_value='tools/check.py'), \
             patch.object(gate, 'tree', return_value='c'*40), patch.object(gate, 'selection', return_value=selected):
            first = checkpoint.fingerprint(self.root, 'a'*40, 'b'*40, 'PR', self.identity['runtime'])
            self.assertEqual(first['selection'], selected)
            self.assertEqual(first['inputs']['tools/check.py'], hashlib.sha256(b'first tool').hexdigest())
            (self.root / 'tools/check.py').write_bytes(b'changed tool')
            self.assertNotEqual(first, checkpoint.fingerprint(self.root, 'a'*40, 'b'*40, 'PR', self.identity['runtime']))
            with self.assertRaisesRegex(ValueError, 'runtime'):
                checkpoint.fingerprint(self.root, 'a'*40, 'b'*40, 'PR', {'python': '3.14'})
            with self.assertRaisesRegex(ValueError, 'runtime'):
                checkpoint.fingerprint(self.root, 'a'*40, 'b'*40, 'PR', dict(self.identity['runtime'], token='private'))
            selected['mode'] = 'full'
            with self.assertRaisesRegex(ValueError, 'Android'):
                checkpoint.fingerprint(self.root, 'a'*40, 'b'*40, 'Full', self.identity['runtime'])

    def test_rejects_changed_head_base_tree_mode_selection_tool_and_runtime(self):
        manifest = self.record()
        for key, value in [('head', 'e'*40), ('base', 'e'*40), ('sourceTree', 'e'*40), ('mode', 'Full'),
                           ('selection', {'mode': 'full'}), ('inputs', {}), ('runtime', {'python': 'other'})]:
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'Incompatible'):
                checkpoint.reuse(Path(manifest['checkpoint']), manifest['checkpointSha256'], dict(self.identity, **{key: value}), self.root / 'resumed')

    def test_failed_stage_and_invalid_duration_are_never_reused(self):
        self.check['result'] = 'failed'
        manifest = self.record()
        with self.assertRaisesRegex(ValueError, 'did not pass'):
            checkpoint.reuse(Path(manifest['checkpoint']), manifest['checkpointSha256'], self.identity, self.root / 'resumed')
        self.assertTrue((self.store / 'files/python.log').is_file())
        for duration in (-1, float('nan'), True, None):
            self.check['durationSeconds'] = duration
            with self.assertRaises(ValueError):
                checkpoint.record(self.root / 'invalid', self.identity, 'python', self.check, self.reports, ['python.log'])

    def test_rejects_altered_manifest_or_report_and_unbound_file(self):
        manifest = self.record()
        path = Path(manifest['checkpoint'])
        original = path.read_bytes()
        path.write_bytes(original + b' ')
        with self.assertRaisesRegex(ValueError, 'manifest'):
            checkpoint.reuse(path, manifest['checkpointSha256'], self.identity, self.root / 'resumed')
        path.write_bytes(original)
        report = self.store / 'files/python.log'
        report.write_bytes(b'altered')
        with self.assertRaisesRegex(ValueError, 'report'):
            checkpoint.reuse(path, manifest['checkpointSha256'], self.identity, self.root / 'resumed')
        report.write_bytes(b'tests passed')
        (self.store / 'files/unbound.log').write_bytes(b'unbound')
        with self.assertRaisesRegex(ValueError, 'Unbound'):
            checkpoint.reuse(path, manifest['checkpointSha256'], self.identity, self.root / 'resumed')

    def test_requires_exact_stage_dependencies_and_no_overwrite(self):
        self.record()
        with self.assertRaisesRegex(ValueError, 'already exists'):
            self.record()
        check = dict(name='sonar', result='passed', durationSeconds=1, log='python.log')
        with self.assertRaisesRegex(ValueError, 'dependencies'):
            checkpoint.record(self.root / 'sonar', self.identity, 'sonar', check, self.reports, ['python.log'])
        dependencies = {'quality': 'e'*64, 'quality-reports': 'f'*64}
        manifest = checkpoint.record(self.root / 'sonar', self.identity, 'sonar', check, self.reports, ['python.log'], dependencies)
        with self.assertRaisesRegex(ValueError, 'dependency'):
            checkpoint.reuse(Path(manifest['checkpoint']), manifest['checkpointSha256'], self.identity, self.root / 'resumed', dict(dependencies, quality='0'*64))

    def test_final_validation_rejects_failed_dependency_snapshot(self):
        (self.reports / 'quality.log').write_bytes(b'failed quality')
        failed = dict(name='quality', result='failed', durationSeconds=1, log='quality.log')
        dependency = checkpoint.record(self.root / 'quality', self.identity, 'quality', failed, self.reports, ['quality.log'])
        check = dict(name='quality-reports', result='passed', durationSeconds=2, log='python.log')
        dependencies = {'quality': dependency['checkpointSha256']}
        manifest = checkpoint.record(self.store, self.identity, 'quality-reports', check, self.reports, ['python.log'], dependencies)
        destination = self.root / 'resumed'
        reused = checkpoint.reuse(Path(manifest['checkpoint']), manifest['checkpointSha256'], self.identity, destination, dependencies)
        shutil.copytree(self.root / 'quality', destination / 'checkpoints/quality')
        shutil.copy2(self.reports / 'quality.log', destination)
        checkpoint.write(destination / 'fingerprint.json', self.identity)
        files = {p.relative_to(destination).as_posix(): checkpoint.digest(p) for p in destination.rglob('*') if p.is_file()}
        doc = dict(head=self.identity['head'], base=self.identity['base'], mode='PR', selection=self.identity['selection'],
                   checks=[dict(failed, result='passed'), reused], files=files)
        with self.assertRaisesRegex(ValueError, 'did not pass'):
            checkpoint.validate_reused_checks(destination / 'evidence.json', doc)

    def test_rejects_path_escape_missing_report_and_conflicting_export(self):
        for files in (['python.log', '../outside'], ['python.log', 'absent.log'], ['python.log', 'python.log']):
            with self.assertRaises(ValueError):
                checkpoint.record(self.store, self.identity, 'python', self.check, self.reports, files)
            self.assertFalse(self.store.exists())
        manifest = self.record()
        destination = self.root / 'resumed'
        destination.mkdir()
        (destination / 'python.log').write_bytes(b'conflict')
        with self.assertRaisesRegex(ValueError, 'Conflicting'):
            checkpoint.reuse(Path(manifest['checkpoint']), manifest['checkpointSha256'], self.identity, destination)
        self.assertFalse((destination / 'checkpoints').exists())

    def test_reused_result_validates_inside_legacy_evidence_schema(self):
        self.check = dict(name='whitespace', result='passed', durationSeconds=12.25, log='python.log')
        selected = dict(mode='none', android='', jvm='', github_scripts='false', release_scripts='false', ci_scripts='false', windows_release_tools='false')
        self.identity['selection'] = selected
        manifest = checkpoint.record(self.store, self.identity, 'whitespace', self.check, self.reports, ['python.log'])
        destination = self.root / 'resumed'
        check = checkpoint.reuse(Path(manifest['checkpoint']), manifest['checkpointSha256'], self.identity, destination)
        checkpoint.write(destination / 'fingerprint.json', self.identity)
        files = {p.relative_to(destination).as_posix(): checkpoint.digest(p) for p in destination.rglob('*') if p.is_file()}
        doc = dict(schema=1, head=self.identity['head'], base=self.identity['base'], mode='PR', platform='Windows', selection=selected,
                   result='passed', checks=[check], files=files)
        path = destination / 'evidence.json'
        path.write_text(json.dumps(doc))
        with patch.object(gate, 'clean'), patch.object(gate, 'selection', return_value=selected):
            self.assertEqual(gate.validate(path, self.root, doc['head'], doc['base'])['schema'], 1)
            saved_fingerprint = doc['files'].pop('fingerprint.json')
            (destination / 'fingerprint.json').unlink()
            path.write_text(json.dumps(doc))
            with self.assertRaisesRegex(ValueError, 'fingerprint'):
                gate.validate(path, self.root, doc['head'], doc['base'])
            checkpoint.write(destination / 'fingerprint.json', self.identity)
            doc['files']['fingerprint.json'] = saved_fingerprint
            doc['checks'][0]['durationSeconds'] = 0
            path.write_text(json.dumps(doc))
            with self.assertRaisesRegex(ValueError, 'provenance'):
                gate.validate(path, self.root, doc['head'], doc['base'])


if os.name == 'nt':
    class WindowsCheckpointTransactionTests(unittest.TestCase):
        """Real orchestrator/validator, controlled command fixtures; no Android run."""
        def test_failed_run_resumes_only_passed_stage_and_corruption_reruns(self):
            with tempfile.TemporaryDirectory(prefix='swypetris-checkpoint-') as temporary:
                root = Path(temporary)
                tools = root / 'tools'
                (tools / 'ci').mkdir(parents=True)
                original = Path(__file__).resolve().parents[1]
                shutil.copy2(original / 'Invoke-LocalChecks.ps1', tools)
                for module in original.glob('*.psm1'):
                    shutil.copy2(module, tools)
                (tools / 'release').mkdir()
                for module in (original / 'release').glob('*.psm1'):
                    shutil.copy2(module, tools / 'release')
                for module in (original / 'ci').glob('*.py'):
                    if not module.name.startswith('test_'):
                        shutil.copy2(module, tools / 'ci')
                (tools / 'ci/run_local_tool_tests.py').write_text(
                    "from pathlib import Path\np = Path('app/build/python-invocations')\n"
                    "p.parent.mkdir(parents=True, exist_ok=True)\n"
                    "p.write_text(p.read_text() + 'run\\n' if p.exists() else 'run\\n')\n"
                    "print('controlled Python command passed')\n")
                (tools / 'Invoke-LocalToolTests.ps1').write_text(
                    "if (Test-Path (Join-Path $PSScriptRoot '../app/build/fail-pester')) { exit 1 }\n"
                    "Write-Output 'controlled PowerShell command passed'\nexit 0\n")
                (root / '.gitignore').write_text('app/build/\n__pycache__/\n')
                def git(*args):
                    return subprocess.check_output(['git', '-C', str(root), *args], text=True, stderr=subprocess.PIPE).strip()
                git('init', '-b', 'main')
                git('add', '.')
                git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', 'base')
                base = git('rev-parse', 'HEAD')
                with (tools / 'Verification.psm1').open('a') as changed:
                    changed.write('\n# Fixture tool-only change\n')
                git('add', '.')
                git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', 'tool change')
                build = root / 'app/build'
                build.mkdir(parents=True)
                flag = build / 'fail-pester'
                flag.touch()
                pwsh = shutil.which('pwsh')
                self.assertIsNotNone(pwsh, 'Mandatory Windows verification requires pwsh')
                def run(name, resume=None, selected_base=None):
                    output = build / name
                    command = [pwsh, '-NoProfile', '-File', str(tools / 'Invoke-LocalChecks.ps1'),
                               '-BaseSha', selected_base or base, '-Python', sys.executable, '-OutputDirectory', str(output), '-EnableCheckpoints']
                    if resume:
                        command += ['-ResumeFrom', str(resume)]
                    result = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                    return result, output
                first, first_dir = run('first')
                self.assertNotEqual(first.returncode, 0, first.stdout)
                self.assertFalse((first_dir / 'evidence.json').exists())
                candidate = first_dir / 'candidate.json'
                self.assertTrue(candidate.exists(), first.stdout)
                failed = checkpoint.load(candidate)
                self.assertEqual(failed['result'], 'failed')
                self.assertEqual({c['name']: c['result'] for c in failed['checks']},
                                 {'whitespace': 'passed', 'python': 'passed', 'pester': 'failed'})
                flag.unlink()
                second, second_dir = run('second', candidate)
                self.assertEqual(second.returncode, 0, second.stdout)
                resumed = checkpoint.load(second_dir / 'evidence.json')
                checks = {c['name']: c for c in resumed['checks']}
                self.assertIn('reuse', checks['python'])
                self.assertNotIn('reuse', checks['pester'])
                self.assertNotIn('reuse', checks['whitespace'])
                self.assertEqual(checks['python']['durationSeconds'], next(c['durationSeconds'] for c in failed['checks'] if c['name'] == 'python'))
                self.assertEqual((build / 'python-invocations').read_text().splitlines(), ['run'])
                old_report = first_dir / 'checkpoints/python/files/python.log'
                original_report = old_report.read_bytes()
                old_report.write_text('altered')
                third, third_dir = run('third', candidate)
                self.assertEqual(third.returncode, 0, third.stdout)
                repeated = checkpoint.load(third_dir / 'evidence.json')
                self.assertNotIn('reuse', next(c for c in repeated['checks'] if c['name'] == 'python'))
                self.assertEqual((build / 'python-invocations').read_text().splitlines(), ['run', 'run'])
                self.assertIn('altered checkpoint report', (third_dir / 'checkpoint-inputs/python/reuse-rejected.log').read_text(encoding='utf-8-sig'))
                old_report.write_bytes(original_report)
                old_manifest = first_dir / 'checkpoints/python/checkpoint.json'
                old_manifest.write_bytes(old_manifest.read_bytes() + b' ')
                fourth, fourth_dir = run('manifest-change', candidate)
                self.assertEqual(fourth.returncode, 0, fourth.stdout)
                fourth_doc = checkpoint.load(fourth_dir / 'evidence.json')
                self.assertNotIn('reuse', next(c for c in fourth_doc['checks'] if c['name'] == 'python'))
                self.assertIn('altered checkpoint manifest', (fourth_dir / 'checkpoint-inputs/python/reuse-rejected.log').read_text(encoding='utf-8-sig'))
                previous_head = git('rev-parse', 'HEAD')
                with (tools / 'Verification.psm1').open('a') as changed:
                    changed.write('\n# New source identity\n')
                git('add', '.')
                git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', 'new head')
                fifth, fifth_dir = run('source-change', fourth_dir / 'evidence.json')
                self.assertEqual(fifth.returncode, 0, fifth.stdout)
                self.assertIn('Incompatible checkpoint inputs/runtime', (fifth_dir / 'checkpoint-inputs/python/reuse-rejected.log').read_text(encoding='utf-8-sig'))
                sixth, sixth_dir = run('base-change', fifth_dir / 'evidence.json', previous_head)
                self.assertEqual(sixth.returncode, 0, sixth.stdout)
                self.assertIn('Incompatible checkpoint inputs/runtime', (sixth_dir / 'checkpoint-inputs/python/reuse-rejected.log').read_text(encoding='utf-8-sig'))
                self.assertEqual(len((build / 'python-invocations').read_text().splitlines()), 5)
