"""Fail-closed local evidence validation without builds or devices."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import subprocess
from unittest.mock import patch
import local_verification as gate


class LocalCommitTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.command('init', '-b', 'main')
        (self.root / 'README.md').write_text('base\n')
        self.commit()
        self.base = self.command('rev-parse', 'HEAD')
        (self.root / 'README.md').write_text('changed\n')
        self.commit()
        self.head = self.command('rev-parse', 'HEAD')

    def command(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], text=True, stderr=subprocess.PIPE).strip()

    def commit(self):
        self.command('add', 'README.md')
        self.command('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', 'fixture')

    def test_accepts_clean_exact_head_and_ancestor_base(self):
        gate.clean(self.root, self.head, self.base)

    def test_production_source_inventory_rejects_unknown_before_build(self):
        source = self.root / 'app/src/main/java/ru/itoltec/swypetris'
        source.mkdir(parents=True)
        (source / 'NewUnclassified.kt').write_text('package ru.itoltec.swypetris\n')
        with self.assertRaisesRegex(ValueError, 'NewUnclassified.kt'):
            gate.validate_source_inventory(self.root)
        (source / 'NewUnclassified.kt').unlink()
        (source / 'GameMusic.kt').write_text('package ru.itoltec.swypetris\n')
        gate.validate_source_inventory(self.root)

    def test_rejects_wrong_head(self):
        with self.assertRaises(ValueError):
            gate.clean(self.root, self.base, self.base)

    def test_rejects_tracked_and_untracked_changes(self):
        for filename in ('README.md', 'unexpected.txt'):
            path = self.root / filename
            previous = path.read_text() if path.exists() else None
            path.write_text('unverified\n')
            with self.assertRaises(ValueError):
                gate.clean(self.root, self.head, self.base)
            if previous is None:
                path.unlink()
            else:
                path.write_text(previous)

    def test_rejects_base_not_in_verified_history(self):
        self.command('checkout', '-b', 'other', self.base)
        (self.root / 'README.md').write_text('divergent\n')
        self.commit()
        divergent = self.command('rev-parse', 'HEAD')
        self.command('checkout', 'main')
        with self.assertRaises(subprocess.CalledProcessError):
            gate.clean(self.root, self.head, divergent)


class LocalEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.path = self.root / 'evidence.json'
        self.selection = dict(mode='none', android='', jvm='', github_scripts='false', release_scripts='false', ci_scripts='false', windows_release_tools='false')
        self.doc = dict(schema=1, platform='Windows', head='a'*40, base='b'*40, mode='PR', selection=self.selection, result='passed', checks=[dict(name='whitespace', result='passed', log='whitespace.log')], files={'whitespace.log':hashlib.sha256(b'ok').hexdigest()})
        (self.root / 'whitespace.log').write_bytes(b'ok')
        self.clean = patch.object(gate, 'clean').start()
        self.choose = patch.object(gate, 'selection', return_value=self.selection).start()
        self.addCleanup(patch.stopall)

    def validate(self, **kwargs):
        self.path.write_text(json.dumps(self.doc))
        return gate.validate(self.path, self.root, 'a'*40, 'b'*40, **kwargs)

    def test_valid_documentation_evidence(self):
        self.assertEqual(self.validate()['result'], 'passed')

    def test_head_base_result_and_schema_mismatch(self):
        for key, value in [('head','c'*40),('base','c'*40),('result','failed'),('schema',0)]:
            with self.subTest(key=key):
                saved = self.doc[key]
                self.doc[key] = value
                with self.assertRaises(ValueError): self.validate()
                self.doc[key] = saved

    def test_missing_failed_skipped_duplicate_check(self):
        for checks in [[], [dict(name='whitespace',result='skipped',log='whitespace.log')], [dict(name='whitespace',result='failed',log='whitespace.log')], self.doc['checks']*2]:
            self.doc['checks'] = checks
            with self.assertRaises(ValueError): self.validate()

    def test_missing_and_modified_report(self):
        (self.root / 'whitespace.log').write_bytes(b'changed')
        with self.assertRaises(ValueError): self.validate()
        (self.root / 'whitespace.log').unlink()
        with self.assertRaises(ValueError): self.validate()

    def test_scope_and_full_requirement(self):
        with self.assertRaises(ValueError): self.validate(full=True)
        self.doc['selection'] = dict(self.selection, mode='full')
        with self.assertRaises(ValueError): self.validate()

    def test_unhashed_log_and_path_escape(self):
        self.doc['checks'][0]['log'] = 'unbound.log'
        with self.assertRaises(ValueError): self.validate()
        self.doc['files']['../outside'] = '0'*64
        with self.assertRaises(ValueError): self.validate()

    def test_linux_evidence_not_accepted_as_pr(self):
        self.doc['mode'] = 'LinuxTools'
        with self.assertRaises(ValueError): self.validate()

    def test_windows_tool_evidence_needs_no_linux_report(self):
        self.selection['github_scripts'] = 'true'
        for name in ('python', 'pester'):
            filename = name + '.log'
            (self.root / filename).write_bytes(b'passed')
            self.doc['files'][filename] = hashlib.sha256(b'passed').hexdigest()
            self.doc['checks'].append(dict(name=name, result='passed', log=filename))
        self.assertEqual(self.validate()['result'], 'passed')
        self.doc['checks'].pop()
        with self.assertRaises(ValueError): self.validate()

    def test_non_windows_or_missing_platform_cannot_authorize_merge(self):
        for platform in ('Linux', None):
            self.doc['platform'] = platform
            with self.assertRaises(ValueError): self.validate()

    def test_dirty_tree_rejected(self):
        self.clean.side_effect = ValueError('dirty')
        with self.assertRaisesRegex(ValueError,'dirty'): self.validate()

    def test_full_requires_tools_and_android(self):
        selected = dict(self.selection, mode='full')
        self.assertEqual(gate.required('Full', selected), {'whitespace','python','pester','source-inventory','quality','quality-reports','sonar','android','install-debug','launcher-smoke'})


class PromotionStructureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'repo'
        self.root.mkdir()
        self.git('init', '-b', 'main')
        (self.root / 'README.md').write_text('base\n')
        self.commit()
        self.base = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-b', 'feature')
        (self.root / 'README.md').write_text('same content\n')
        self.commit('feature')
        self.source_head = self.git('rev-parse', 'HEAD')
        self.git('checkout', 'main')
        (self.root / 'README.md').write_text('same content\n')
        self.commit('squash main')
        self.main_head = self.git('rev-parse', 'HEAD')
        self.source_dir = Path(self.temp.name) / 'original'
        self.source_dir.mkdir()
        self.log = self.source_dir / 'sonar.log'
        self.log.write_text('PR quality gate passed')
        self.original = self.source_dir / 'evidence.json'
        self.source = dict(schema=1, mode='Full', result='passed', platform='Windows',
                           head=self.source_head, base=self.base,
                           checks=[dict(name='sonar', result='passed', log='sonar.log', durationSeconds=10)],
                           files={'sonar.log': hashlib.sha256(self.log.read_bytes()).hexdigest()})
        self.original.write_text(json.dumps(self.source))
        self.fresh = Path(self.temp.name) / 'main-sonar.log'
        self.fresh.write_text('Main quality gate passed')

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], text=True, stderr=subprocess.PIPE).strip()

    def commit(self, message='fixture'):
        self.git('add', 'README.md')
        self.git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', message)

    def test_promotes_identical_tree_with_bound_original_and_fresh_main_sonar(self):
        destination = Path(self.temp.name) / 'promoted'
        with patch.object(gate, 'validate'):
            output = gate.promote(self.original, self.root, self.main_head, self.base,
                                  destination, 42, self.fresh, 3.5)
        doc = json.loads(output.read_text())
        self.assertEqual(doc['schema'], 2)
        self.assertEqual(doc['head'], self.main_head)
        self.assertEqual(doc['promotion']['sourceHead'], self.source_head)
        self.assertEqual(doc['checks'][0]['durationSeconds'], 3.5)
        with patch.object(gate, 'validate'):
            gate.validate_promotion(output, self.root, doc)
            for key, value in [('prNumber', 0), ('prNumber', True), ('sourceTree', '0'*40),
                               ('sourceEvidenceSha256', '0'*64)]:
                damaged = json.loads(json.dumps(doc))
                damaged['promotion'][key] = value
                with self.subTest(key=key), self.assertRaises(ValueError):
                    gate.validate_promotion(output, self.root, damaged)
            damaged = json.loads(json.dumps(doc))
            damaged['files']['sonar.log'] = self.source['files']['sonar.log']
            with self.assertRaisesRegex(ValueError, 'fresh'):
                gate.validate_promotion(output, self.root, damaged)
            damaged = json.loads(json.dumps(doc))
            damaged['checks'][0]['durationSeconds'] = float('nan')
            with self.assertRaisesRegex(ValueError, 'duration'):
                gate.validate_promotion(output, self.root, damaged)

    def test_rejects_different_tree_before_copying_or_main_analysis(self):
        (self.root / 'README.md').write_text('different\n')
        self.commit()
        other_head = self.git('rev-parse', 'HEAD')
        with self.assertRaisesRegex(ValueError, 'trees differ'):
            gate.preflight_promotion(self.original, self.root, other_head, self.base)
        self.assertFalse((Path(self.temp.name) / 'promoted').exists())

    def test_version_two_validates_copied_full_reports_and_rejects_corruption(self):
        selected = dict(mode='full', android='', jvm='', github_scripts='false',
                        release_scripts='false', ci_scripts='false', windows_release_tools='false')
        self.source['selection'] = selected
        identity = 'ru.itoltec.swypetris.SmokeTest#launch'
        files = {}
        for name in gate.required('Full', selected):
            path = self.source_dir / (name + '.log')
            if name == 'quality':
                path.write_text('\n'.join('> Task ' + task for task in gate.QUALITY_TASKS))
            elif name == 'sonar':
                path.write_text('PR quality gate passed')
            else:
                path.write_text(name + ' passed')
            files[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
        for name, data in {
            'android/TEST-SmokeTest.xml': b'<testsuite><testcase classname="ru.itoltec.swypetris.SmokeTest" name="launch"/></testsuite>',
            'app/build/outputs/apk/debug/app-debug.apk': b'debug apk',
            'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk': b'test apk',
        }.items():
            path = self.source_dir / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            files[name] = hashlib.sha256(data).hexdigest()
        self.source['files'] = files
        self.source['checks'] = [dict(name=name, result='passed', log=name + '.log', durationSeconds=1)
                                 for name in gate.required('Full', selected)]
        self.original.write_text(json.dumps(self.source))
        destination = Path(self.temp.name) / 'promoted-full'
        with patch.object(gate, 'selection', return_value=selected), \
             patch.object(gate, 'validate_source_inventory'), \
             patch.object(gate, 'validate_jvm_inventory'), \
             patch('full_regression_proof.validate_quality'), \
             patch.object(gate, 'test_inventory', return_value={identity}):
            output = gate.promote(self.original, self.root, self.main_head, self.base,
                                  destination, 42, self.fresh, 3.5)
            self.assertEqual(gate.validate(output, self.root, self.main_head, self.base, full=True)['schema'], 2)
            (destination / 'source/quality.log').write_text('tampered')
            with self.assertRaises(ValueError):
                gate.validate(output, self.root, self.main_head, self.base, full=True)


if __name__ == '__main__':
    unittest.main()

class LocalReportContractTests(unittest.TestCase):
    def test_successful_and_cached_mandatory_tasks(self):
        for outcome in ('', ' UP-TO-DATE', ' FROM-CACHE'):
            gate.validate_tasks('\n'.join('> Task ' + task + outcome for task in gate.QUALITY_TASKS))

    def test_disabled_missing_failed_or_duplicate_task(self):
        lines = ['> Task ' + task for task in gate.QUALITY_TASKS]
        for replacement in ('', lines[-1]+' SKIPPED', lines[-1]+' NO-SOURCE', lines[-1]+' FAILED', lines[-1]+'\n'+lines[-1]):
            with self.subTest(replacement=replacement), self.assertRaises(ValueError):
                gate.validate_tasks('\n'.join(lines[:-1]+[replacement]))

    def test_only_explicit_opt_in_cases_may_skip(self):
        for identity in gate.OPT_IN:
            name, method = identity.split('#')
            gate.validate_skips([gate.ET.fromstring(f'<testcase classname="{name}" name="{method}"><skipped/></testcase>')])
        for identity in (
            'ru.itoltec.swypetris.SmokeTest#launch',
            'ru.itoltec.swypetris.UpdateCheckTest#checks',
            'ru.itoltec.swypetris.EnergyScenarioTest#anotherMethod',
            'ru.itoltec.swypetris.MaterialRenderingTest#realActivityFrames',
            'ru.itoltec.swypetris.MaterialFrameEvidenceTest#anotherMethod',
            'ru.itoltec.swypetris.MaterialFrameEvidenceTest#realActivityFramesExtra',
        ):
            name, method = identity.split('#')
            with self.assertRaises(ValueError):
                gate.validate_skips([gate.ET.fromstring(f'<testcase classname="{name}" name="{method}"><skipped/></testcase>')])

    def test_jvm_inventory_rejects_missing_method_and_skipped_case(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / 'app/src/test/ExampleTest.kt'
            source.parent.mkdir(parents=True)
            source.write_text('package example\nclass ExampleTest {\n@Test fun first() {}\n@Test fun second() {}\n}')
            reports = root / 'build/test-results/testDebugUnitTest'
            reports.mkdir(parents=True)
            report = reports / 'TEST-example.ExampleTest.xml'
            def xml(second):
                return '<testsuite><testcase classname="example.ExampleTest" name="first"/>'+second+'</testsuite>'
            report.write_text(xml('<testcase classname="example.ExampleTest" name="second"/>'))
            gate.validate_jvm_inventory(root, root/'build')
            for second in ('', '<testcase classname="example.ExampleTest" name="second"><skipped/></testcase>'):
                report.write_text(xml(second))
                with self.assertRaises(ValueError): gate.validate_jvm_inventory(root, root/'build')
