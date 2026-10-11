"""Release resolution must reject untrusted refs, stale attempts and mismatched tags."""

import base64
import copy
import json
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import Mock, patch

import release_context as context

SHA = "a" * 40
PARENT = "b" * 40
TAG = "c" * 40
REPO = "RoyMatus/Swypetris"


class FakeAPI:
    def __init__(self):
        self.run = dict(id=7, run_attempt=2, workflow_id=9, path=context.WORKFLOW,
                        repository=dict(full_name=REPO), head_repository=dict(full_name=REPO),
                        head_sha=SHA, event="push", head_branch="main", status="completed", conclusion="success")
        self.runs = [self.run]
        self.values = {
            "actions/workflows/ci.yml": dict(id=9, path=context.WORKFLOW, state="active"),
            "actions/runs/7/attempts/2": self.run,
            f"git/commits/{SHA}": dict(sha=SHA, parents=[dict(sha=PARENT)]),
            f"contents/gradle.properties?ref={SHA}": dict(type="file", encoding="base64", sha="new",
                content=base64.b64encode(b"swypetrisVersion=1.5.0\n").decode()),
            f"contents/gradle.properties?ref={PARENT}": dict(sha="old"),
            f"compare/{SHA}...main": dict(status="ahead", base_commit=dict(sha=SHA), merge_base_commit=dict(sha=SHA)),
        }

    def read(self, path):
        if path not in self.values:
            raise urllib.error.HTTPError(path, 404, "Not found", {}, None)
        value = self.values[path]
        if isinstance(value, Exception):
            raise value
        return copy.deepcopy(value)

    def collection(self, path, key):
        return copy.deepcopy(self.runs)


class ReleaseContextTests(unittest.TestCase):
    def setUp(self):
        self.api = FakeAPI()
        self.event = dict(action="completed", repository=dict(full_name=REPO), workflow_run=copy.deepcopy(self.api.run))

    def resolve(self, event_name="workflow_run", ref="refs/heads/main"):
        return context.resolve(self.api, REPO, event_name, self.event, ref, SHA)

    def tag(self, annotated=False):
        self.api.values['git/ref/tags/v1.5.0'] = dict(ref='refs/tags/v1.5.0', object=dict(type='tag' if annotated else 'commit', sha=TAG if annotated else SHA))
        if annotated:
            self.api.values[f'git/tags/{TAG}'] = dict(sha=TAG, object=dict(type='commit', sha=SHA))

    def test_automatic_resolution_binds_trigger(self):
        self.assertEqual(dict(sha=SHA, version='1.5.0', publish='true', source_run_id=7, source_attempt=2), self.resolve())

    def test_manual_main_and_annotated_tag_never_publish(self):
        self.assertEqual('false', self.resolve('workflow_dispatch')['publish'])
        self.tag(annotated=True)
        self.event['inputs'] = dict(tag='v1.5.0')
        self.assertEqual(SHA, self.resolve('workflow_dispatch')['sha'])

    def test_wrong_event_ref_and_repository_rejected(self):
        for name, ref in [('push', 'refs/heads/main'), ('workflow_run', 'refs/heads/feature'), ('workflow_dispatch', 'refs/tags/v1.5.0'), ('workflow_dispatch', 'refs/heads/feature')]:
            with self.subTest(name=name, ref=ref), self.assertRaises(ValueError):
                self.resolve(name, ref)
        self.event['repository']['full_name'] = 'other/repo'
        with self.assertRaises(ValueError):
            self.resolve()

    def test_untrusted_trigger_or_api_run_rejected(self):
        for target in (self.event['workflow_run'], self.api.run):
            for key, bad in [('repository', dict(full_name='other/repo')), ('head_repository', dict(full_name='fork/repo')),
                             ('workflow_id', 10), ('path', 'other.yml'), ('event', 'pull_request'),
                             ('head_branch', 'feature'), ('status', 'in_progress'), ('conclusion', 'failure'),
                             ('head_sha', PARENT), ('run_attempt', 1)]:
                original = target[key]
                target[key] = bad
                with self.subTest(key=key), self.assertRaises((ValueError, urllib.error.HTTPError)):
                    self.resolve()
                target[key] = original

    def test_inactive_or_wrong_workflow_rejected(self):
        for key, value in [('state', 'disabled_manually'), ('path', 'other.yml')]:
            self.api.values['actions/workflows/ci.yml'] = dict(id=9, path=context.WORKFLOW, state='active')
            self.api.values['actions/workflows/ci.yml'][key] = value
            with self.assertRaises(ValueError):
                self.resolve()

    def test_new_run_or_attempt_rejected(self):
        for replacement in [[], [dict(self.api.run, run_attempt=3)], [dict(self.api.run, id=8)], [dict(self.api.run, conclusion='failure')]]:
            self.api.runs = replacement
            with self.subTest(runs=replacement), self.assertRaises(ValueError):
                self.resolve()

    def test_nonancestor_and_unchanged_configuration_rejected(self):
        path = f'compare/{SHA}...main'
        for status in ['behind', 'diverged']:
            self.api.values[path]['status'] = status
            with self.assertRaises(ValueError):
                self.resolve()
        self.api.values[path]['status'] = 'ahead'
        self.api.values[f'contents/gradle.properties?ref={PARENT}']['sha'] = 'new'
        with self.assertRaisesRegex(ValueError, 'did not change'):
            self.resolve()

    def test_invalid_version_and_tag_mismatch_rejected(self):
        path = f'contents/gradle.properties?ref={SHA}'
        for value in [b'swypetrisVersion=1.2\n', b'swypetrisVersion=01.2.3\n', b'swypetrisVersion=1.5.0\nswypetrisVersion=1.5.0\n']:
            self.api.values[path]['content'] = base64.b64encode(value).decode()
            with self.assertRaises(ValueError):
                self.resolve()
        self.tag()
        self.event['inputs'] = dict(tag='v1.5.0')
        self.api.values[path]['content'] = base64.b64encode(b'swypetrisVersion=1.6.0\n').decode()
        with self.assertRaisesRegex(ValueError, 'differs'):
            self.resolve('workflow_dispatch')

    def test_existing_tag_exact_commit_allowed_different_rejected(self):
        self.tag(True)
        context.validate_tag(self.api, SHA, '1.5.0')
        self.api.values[f'git/tags/{TAG}']['object']['sha'] = PARENT
        with self.assertRaisesRegex(ValueError, 'different commit'):
            context.validate_tag(self.api, SHA, '1.5.0')

    def test_missing_manual_tag_invalid_tag_and_deep_tag_rejected(self):
        for tag in ['v1.5.0', '../main', 'v1.5.0\noutput=true']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                context.tag_commit(self.api, tag)
        self.tag(True)
        self.api.values[f'git/tags/{TAG}']['object'] = dict(type='tag', sha=TAG)
        with self.assertRaisesRegex(ValueError, 'five levels'):
            context.tag_commit(self.api, 'v1.5.0')

    def test_existing_release_and_api_failure_fail_closed(self):
        self.api.values['releases/tags/v1.5.0'] = dict(id=1)
        with self.assertRaisesRegex(ValueError, 'already exists'):
            self.resolve()
        self.api.values['releases/tags/v1.5.0'] = urllib.error.HTTPError('', 403, 'Forbidden', {}, None)
        with self.assertRaises(urllib.error.HTTPError):
            self.resolve()

    def test_provenance_binds_verified_source_and_release_attempt(self):
        source = dict(run_id=7, attempt=2, artifact_id=10, artifact_digest='sha256:abc')
        with patch.dict('os.environ', GITHUB_RUN_ID='20', GITHUB_RUN_ATTEMPT='3'), patch.object(context, 'verify_latest', return_value=source):
            result = context.provenance(self.api, REPO, SHA, self.event, Path.cwd())
        self.assertEqual(3, result['release_run_attempt'])
        self.assertEqual(2, result['source_run_attempt'])
        self.assertEqual('sha256:abc', result['source_artifact_digest'])
        for bad in [None, dict(source, attempt=3), dict(source, run_id=8)]:
            with patch.object(context, 'verify_latest', return_value=bad), self.assertRaises(ValueError):
                context.provenance(self.api, REPO, SHA, self.event, Path.cwd())

    def test_establish_existing_exact_tag_does_not_write(self):
        self.tag(True)
        with patch.object(context, 'create_tag_ref') as create:
            context.establish_tag(self.api, SHA, '1.5.0')
            create.assert_not_called()

    def test_establish_absent_tag_creates_then_verifies(self):
        def create(api, tag, sha):
            self.assertIs(api, self.api)
            self.assertEqual(('v1.5.0', SHA), (tag, sha))
            self.tag()
        with patch.object(context, 'create_tag_ref', side_effect=create) as write:
            context.establish_tag(self.api, SHA, '1.5.0')
            write.assert_called_once()

    def test_concurrent_tag_creation_only_accepts_exact_commit(self):
        for malicious in (False, True):
            self.api.values.pop('git/ref/tags/v1.5.0', None)
            def conflict(api, tag, sha):
                self.tag()
                if malicious:
                    self.api.values['git/ref/tags/v1.5.0']['object']['sha'] = PARENT
                raise urllib.error.HTTPError('', 422, 'Already exists', {}, None)
            with patch.object(context, 'create_tag_ref', side_effect=conflict):
                if malicious:
                    with self.assertRaisesRegex(ValueError, 'different commit'):
                        context.establish_tag(self.api, SHA, '1.5.0')
                else:
                    context.establish_tag(self.api, SHA, '1.5.0')

    def test_tag_creation_network_and_permission_errors_fail_closed(self):
        for error in (urllib.error.URLError('network'), urllib.error.HTTPError('', 403, 'Forbidden', {}, None)):
            with patch.object(context, 'create_tag_ref', side_effect=error), self.assertRaises(urllib.error.URLError):
                context.establish_tag(self.api, SHA, '1.5.0')

    def test_tag_creation_without_visible_ref_fails_closed(self):
        with patch.object(context, 'create_tag_ref'), self.assertRaises(ValueError):
            context.establish_tag(self.api, SHA, '1.5.0')

    def test_atomic_ref_request_uses_exact_sha_and_checks_response(self):
        api = Mock(base='https://api.github.com/repos/example/repo/', headers={'Accept': 'application/vnd.github+json'})
        response = Mock(status=201)
        manager = Mock()
        manager.__enter__ = Mock(return_value=response)
        manager.__exit__ = Mock(return_value=False)
        api.opener.open.return_value = manager
        context.create_tag_ref(api, 'v1.5.0', SHA)
        request = api.opener.open.call_args.args[0]
        self.assertEqual('POST', request.method)
        self.assertEqual('https://api.github.com/repos/example/repo/git/refs', request.full_url)
        self.assertEqual(dict(ref='refs/tags/v1.5.0', sha=SHA), json.loads(request.data))
        response.status = 200
        with self.assertRaises(ValueError):
            context.create_tag_ref(api, 'v1.5.0', SHA)


if __name__ == '__main__':
    unittest.main()
