"""Release evidence must bind complete tests to the latest trusted CI attempt."""

import copy
import io
import json
import tempfile
import unittest
import urllib.request
import zipfile
from pathlib import Path
from unittest.mock import patch

import full_regression_proof as proof
from wait_for_android_ci import ArtifactRedirect, verify_latest

REPOSITORY = "RoyMatus/Swypetris"
SHA = "a" * 40


def zip_bytes(files):
    target = io.BytesIO()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    return target.getvalue()


class FullRegressionProofTests(unittest.TestCase):
    def setUp(self):
        self.run = dict(id=7, run_attempt=2, workflow_id=9, path=proof.WORKFLOW,
                        repository=dict(full_name=REPOSITORY), head_sha=SHA,
                        event="push", head_branch="main", status="completed", conclusion="success")
        self.jobs = [dict(name="android", run_id=7, run_attempt=2, status="completed",
                          conclusion="success", labels=["self-hosted", "windows", "X64", "swypetris-android"],
                          steps=[dict(name=name, status="completed", conclusion="success")
                                 for name in proof.REQUIRED_STEPS])]
        environment = dict(os="Windows", api="35", abi="x86_64", serial="emulator-5556",
                           avd="SwypetrisCI35", mode="full", android_classes="",
                           instrumentation="success", smoke="success", java="21", emulator="35.6",
                           fingerprint="android/test", emulator_options="-no-window -wipe-data",
                           instrumentation_command="adb -s emulator-5556 shell am instrument -w -r ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner")
        self.payload = {
            "configuration.json": b'{"gradle.properties": "blob-id"}',
            "environment.json": json.dumps(environment).encode(),
            "smoke.txt": b"Events injected: 1\n",
            "instrumentation.txt": b"INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS: class=ru.itoltec.swypetris.SmokeTest\nINSTRUMENTATION_STATUS: test=launch\nINSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n",
            "app-debug.apk": zip_bytes({"AndroidManifest.xml": b"manifest"}),
            "app-debug-androidTest.apk": zip_bytes({"AndroidManifest.xml": b"manifest"}),
        }
        self.evidence = dict(schema=1, repository=REPOSITORY, workflow=proof.WORKFLOW,
                             workflow_ref=f"{REPOSITORY}/{proof.WORKFLOW}@refs/heads/main",
                             sha=SHA, event="push", ref="refs/heads/main", run_id=7, attempt=2,
                             mode="full", android_classes="", instrumentation="success", smoke="success")
        self.artifact = dict(id=11, name="full-regression-7-2", expired=False,
                             workflow_run=dict(id=7, head_sha=SHA))
        self.config_patch = patch.object(proof, "configuration", return_value={"gradle.properties": "blob-id"})
        self.classes_patch = patch.object(proof, "test_inventory", return_value={"ru.itoltec.swypetris.SmokeTest#launch"})
        self.config_patch.start()
        self.classes_patch.start()
        self.addCleanup(self.config_patch.stop)
        self.addCleanup(self.classes_patch.stop)

    def archive(self):
        self.evidence["sha256"] = {name: proof.digest(data) for name, data in self.payload.items()}
        data = zip_bytes(dict(self.payload, **{"evidence.json": json.dumps(self.evidence)}))
        self.artifact["digest"] = "sha256:" + proof.digest(data)
        return data

    def validate(self):
        return proof.validate_archive(self.archive(), self.artifact, self.run, self.jobs,
                                      REPOSITORY, SHA, 9, Path.cwd())

    def test_complete_evidence_passes(self):
        self.assertEqual(2, self.validate()["attempt"])

    def test_windows_crlf_report_passes_without_changing_hashed_bytes(self):
        self.payload['instrumentation.txt'] = self.payload['instrumentation.txt'].replace(b'\n', b'\r\n')
        self.validate()
        self.assertEqual(proof.digest(self.payload['instrumentation.txt']),
                         self.evidence['sha256']['instrumentation.txt'])

    def test_wrong_run_repository_sha_workflow_attempt_event_and_branch(self):
        for key, value in (('repository', 'other/repo'), ('sha', 'b' * 40),
                           ('workflow', '.github/workflows/other.yml'), ('run_id', 8),
                           ('attempt', 1), ('event', 'pull_request'), ('ref', 'refs/tags/v1')):
            with self.subTest(key=key):
                previous = copy.deepcopy(self.evidence)
                self.evidence[key] = value
                with self.assertRaises(ValueError):
                    self.validate()
                self.evidence = previous
        for key, value in (('workflow_id', 8), ('path', 'other.yml'), ('head_sha', 'b' * 40),
                           ('repository', dict(full_name='other/repo'))):
            with self.subTest(run_key=key):
                previous = copy.deepcopy(self.run)
                self.run[key] = value
                with self.assertRaises(ValueError):
                    self.validate()
                self.run = previous

    def test_selected_none_filtered_and_failed_checks_rejected(self):
        for changes in ({'mode': 'selected'}, {'mode': 'none'}, {'android_classes': 'SmokeTest'},
                        {'instrumentation': 'failure'}, {'smoke': 'failure'}):
            with self.subTest(changes=changes):
                previous = copy.deepcopy(self.evidence)
                self.evidence.update(changes)
                with self.assertRaises(ValueError):
                    self.validate()
                self.evidence = previous

    def test_missing_reports_incomplete_inventory_and_completion(self):
        for name in proof.FILES:
            with self.subTest(missing=name):
                data = self.payload.pop(name)
                with self.assertRaises(ValueError):
                    self.validate()
                self.payload[name] = data
        complete = self.payload['instrumentation.txt']
        self.payload['instrumentation.txt'] = complete.replace(b'INSTRUMENTATION_CODE: -1', b'')
        with self.assertRaises(ValueError):
            self.validate()
        self.payload['instrumentation.txt'] = complete
        with patch.object(proof, 'test_inventory', return_value={'example.MissingTest#missing'}), self.assertRaises(ValueError):
            self.validate()
        self.payload['instrumentation.txt'] = self.payload['instrumentation.txt'].replace(b'STATUS_CODE: 0', b'STATUS_CODE: -2')
        with self.assertRaises(ValueError):
            self.validate()

    def test_skipped_smoke_or_missing_launcher_and_changed_environment(self):
        original = dict(self.payload)
        for name, data in (
            ('smoke.txt', b''),
            ('instrumentation.txt', original['instrumentation.txt'].replace(b'STATUS_CODE: 0', b'STATUS_CODE: -4')),
            ('environment.json', original['environment.json'].replace(b'Windows', b'Linux')),
            ('configuration.json', b'{}'),
        ):
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.payload[name] = data
                self.validate()
            self.payload = dict(original)

    def test_cancelled_failed_skipped_jobs_or_steps(self):
        for conclusion in ('cancelled', 'failure', 'skipped', 'timed_out', 'neutral'):
            with self.subTest(conclusion=conclusion), self.assertRaises(ValueError):
                self.run['conclusion'] = conclusion
                self.validate()
        self.run['conclusion'] = 'success'
        self.jobs[0]['run_attempt'] = 1
        with self.assertRaises(ValueError):
            self.validate()
        self.jobs[0]['run_attempt'] = 2
        for step in self.jobs[0]['steps']:
            with self.subTest(step=step['name']), self.assertRaises(ValueError):
                step['conclusion'] = 'skipped'
                self.validate()
            step['conclusion'] = 'success'

    def test_corrupt_expired_wrong_attempt_artifacts(self):
        data = self.archive()
        for changes in ({'expired': True}, {'name': 'full-regression-7-1'},
                        {'digest': 'sha256:bad'}, {'workflow_run': {'id': 8, 'head_sha': SHA}}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                proof.validate_archive(data, dict(self.artifact, **changes), self.run, self.jobs,
                                       REPOSITORY, SHA, 9, Path.cwd())
        with self.assertRaises(ValueError):
            proof.validate_archive(data + b'corruption', self.artifact, self.run, self.jobs,
                                   REPOSITORY, SHA, 9, Path.cwd())

    def test_manifest_hash_does_not_accept_changed_apk_or_report(self):
        self.archive()
        for name in ('app-debug.apk', 'instrumentation.txt'):
            previous = self.payload[name]
            self.payload[name] += b'changed'
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'hash mismatch'):
                proof.validate_payload(self.evidence, self.payload, Path.cwd())
            self.payload[name] = previous

    def test_inventory_handles_bom_and_rejects_unrecognized_declarations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = root / 'app/src/androidTest'
            sources.mkdir(parents=True)
            source = sources / 'OneTest.kt'
            source.write_text('package example\nclass OneTest {\n@Test fun one() {}\n}', encoding='utf-8-sig')
            # Disable the payload fixture's inventory mock for this actual parser test.
            self.classes_patch.stop()
            self.assertEqual({'example.OneTest#one'}, proof.test_inventory(root))
            source.write_text('package example\nclass OneTest { @Test(timeout=100) fun one() {} }', encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'Unsupported'):
                proof.test_inventory(root)

    def test_redirect_does_not_send_token_to_storage(self):
        request = urllib.request.Request('https://api.github.com/artifact', headers={'Authorization': 'Bearer private'})
        redirected = ArtifactRedirect().redirect_request(request, None, 302, '', {}, 'https://storage.example/signed')
        self.assertIsNone(redirected.get_header('Authorization'))

    def test_gate_rejects_missing_evidence_new_attempt_and_new_run(self):
        data = self.archive()
        owner = self

        class FakeAPI:
            def __init__(self, refreshed=None, artifacts=None):
                self.refreshed = refreshed or [owner.run]
                self.artifacts = [owner.artifact] if artifacts is None else artifacts
                self.collections = 0

            def collection(self, path, key):
                if key == 'workflow_runs':
                    self.collections += 1
                    return [owner.run] if self.collections == 1 else self.refreshed
                return self.artifacts if key == 'artifacts' else owner.jobs

            def read(self, path, binary=False):
                if binary:
                    return data
                if path == 'actions/workflows/ci.yml':
                    return dict(id=9, path=proof.WORKFLOW, state='active')
                return owner.run

        self.assertEqual(2, verify_latest(FakeAPI(), REPOSITORY, SHA, Path.cwd())['attempt'])
        for api in (FakeAPI(artifacts=[]),
                    FakeAPI(refreshed=[dict(self.run, run_attempt=3, status='in_progress')]),
                    FakeAPI(refreshed=[self.run, dict(self.run, id=8)])):
            with self.assertRaises(ValueError):
                verify_latest(api, REPOSITORY, SHA, Path.cwd())


if __name__ == '__main__':
    unittest.main()
