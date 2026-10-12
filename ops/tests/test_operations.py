"""Run only on the mini-host: python3 -m unittest discover -s ops/tests -v.

Every process/network/recovery boundary is replaced. No real Docker, systemd,
backup, service restart or release is performed by this suite.
"""
import contextlib
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

OPS = Path(__file__).resolve().parents[1]


def module(name):
    spec = importlib.util.spec_from_file_location('audit_' + name, OPS / (name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


release = module('release')
backup = module('backup')
guardian = module('terrafood_guardian')


class PrivateWorkspace(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='terra-ops-unit-')
        self.root = Path(self.temporary.name)
        self.old_mask = os.umask(0o077)
        self.addCleanup(os.umask, self.old_mask)
        self.addCleanup(self.temporary.cleanup)


class ReleaseTests(PrivateWorkspace):
    def arguments(self, execute=True):
        images = {service: 'sha256:' + character * 64 for service, character in zip(release.SERVICES, 'abcd')}
        manifest = self.root / 'prepared.json'
        manifest.write_text(json.dumps({'schema': 1, 'revision': 'a' * 40, 'images': images}))
        env = self.root / 'runtime.env'
        env.write_text('\n'.join(key + '=' + character * 40 for key, character in zip(
            ('AGENT_INTERNAL_TOKEN', 'MCP_INTERNAL_TOKEN', 'MCP_BACKEND_TOKEN', 'AGENT_CONTEXT_SECRET'), 'abcd')))
        env.chmod(0o600)
        return SimpleNamespace(manifest=manifest, env_file=env, compose=self.root / 'compose.yml',
            project='terra-unit', execute=execute, health_url='http://127.0.0.1:19999', deployed=self.root / 'deployed.json')

    def fake_capture(self, args):
        images = json.loads(self.arguments_cache.manifest.read_text())['images']
        if 'ps' in args:
            return 'container-' + args[-1]
        if 'inspect' in args and any(value.startswith('container-') for value in args):
            container = next(value for value in args if value.startswith('container-'))
            return images[container.removeprefix('container-')]
        return next((value for value in args if value.startswith('sha256:')), 'sha256:' + 'a' * 64)

    @contextlib.contextmanager
    def controlled_apply(self, args, healthy=True, execute_failure=None):
        self.arguments_cache = args
        operations = []
        def run(command, **kwargs):
            operations.append(command)
            if execute_failure and execute_failure(command):
                raise subprocess.CalledProcessError(1, command)
            return SimpleNamespace(returncode=0, stdout='')
        with patch.object(release, 'capture', side_effect=self.fake_capture), \
             patch.object(release, 'run', side_effect=run), \
             patch.object(release, 'healthy', return_value=healthy), \
             patch.object(release, 'open', return_value=io.StringIO(), create=True), \
             patch.object(release.fcntl, 'flock'), \
             patch.object(release.time, 'monotonic', side_effect=[0, 1, 151]), \
             patch.object(release.time, 'sleep'), contextlib.redirect_stdout(io.StringIO()):
            yield operations

    def test_preview_never_starts_services_or_marks_deployed(self):
        args = self.arguments(execute=False)
        with self.controlled_apply(args) as commands:
            release.apply(args)
        self.assertFalse(args.deployed.exists())
        self.assertTrue(any(command[-2:] == ['config', '--quiet'] for command in commands))
        self.assertFalse(any('up' in command or 'exec' in command for command in commands))

    def test_success_requires_api_images_agent_and_unauthenticated_mcp_rejection(self):
        args = self.arguments()
        with self.controlled_apply(args) as commands:
            release.apply(args)
        marker = json.loads(args.deployed.read_text())
        self.assertEqual(marker['revision'], 'a' * 40)
        self.assertIn('verified_at', marker)
        self.assertTrue(any('up' in command and all(service in command for service in release.SERVICES) for command in commands))
        self.assertTrue(any('exec' in command and 'agent-api' in command for command in commands))
        self.assertTrue(any('exec' in command and 'agent-mcp' in command and '401' in command[-1] for command in commands))

    def test_api_failure_preserves_previous_deployed_marker_and_is_retryable(self):
        args = self.arguments()
        args.deployed.write_text('previous deployed marker')
        with self.controlled_apply(args, healthy=False):
            with self.assertRaises(RuntimeError): release.apply(args)
        self.assertEqual(args.deployed.read_text(), 'previous deployed marker')
        with self.controlled_apply(args): release.apply(args)
        self.assertEqual(json.loads(args.deployed.read_text())['revision'], 'a' * 40)

    def test_agent_or_mcp_failure_never_marks_html_only_release_successful(self):
        for service in ('agent-api', 'agent-mcp'):
            with self.subTest(service=service):
                args = self.arguments()
                with self.controlled_apply(args, execute_failure=lambda command: 'exec' in command and service in command):
                    with self.assertRaises(RuntimeError): release.apply(args)
                self.assertFalse(args.deployed.exists())

    def test_image_mismatch_does_not_mark_deployed(self):
        args = self.arguments()
        with self.controlled_apply(args):
            with patch.object(release, 'capture', return_value='wrong-image'):
                with self.assertRaises(RuntimeError): release.apply(args)
        self.assertFalse(args.deployed.exists())

    def test_missing_component_and_public_env_are_rejected_before_apply(self):
        args = self.arguments()
        manifest = json.loads(args.manifest.read_text()); del manifest['images']['agent-mcp']
        args.manifest.write_text(json.dumps(manifest))
        with patch.object(release, 'run') as command:
            with self.assertRaises(ValueError): release.apply(args)
            command.assert_not_called()
        args = self.arguments(); args.env_file.chmod(0o644)
        with patch.object(release, 'capture', return_value='ok'), patch.object(release, 'run') as command:
            with self.assertRaises(ValueError): release.apply(args)
            command.assert_not_called()

    def test_arbitrary_revision_cannot_be_interpolated_into_python_probe(self):
        args = self.arguments()
        manifest = json.loads(args.manifest.read_text()); manifest['revision'] = "'; print('injected') #"
        args.manifest.write_text(json.dumps(manifest))
        with self.controlled_apply(args) as commands:
            with self.assertRaises(ValueError): release.apply(args)
        self.assertFalse(any('exec' in command for command in commands))

    def test_failed_build_has_no_manifest_and_same_revision_can_retry(self):
        output = self.root / 'prepared.json'
        builds = []
        should_fail = True
        def fake_run(command, **kwargs):
            nonlocal should_fail
            builds.append(command)
            if should_fail:
                should_fail = False
                raise subprocess.CalledProcessError(1, command)
        archive = Mock(); archive.__enter__ = Mock(return_value=archive); archive.__exit__ = Mock(return_value=False)
        with patch.object(release, 'capture', side_effect=lambda args: 'a' * 40 if args[0] == 'git' else 'sha256:' + 'a' * 64), \
             patch.object(release.subprocess, 'run'), patch.object(release.tarfile, 'open', return_value=archive), \
             patch.object(release, 'run', side_effect=fake_run):
            with self.assertRaises(subprocess.CalledProcessError): release.prepare(self.root, 'main', output)
            self.assertFalse(output.exists())
            release.prepare(self.root, 'main', output)
        self.assertEqual(set(json.loads(output.read_text())['images']), set(release.SERVICES))
        self.assertEqual(sum('terrafood-backend:' + 'a' * 40 in command for command in builds), 2)


class BackupTests(PrivateWorkspace):
    def args(self):
        password = self.root / 'passphrase'
        password.write_text('unit-test-placeholder-only-' * 3); password.chmod(0o600)
        output = self.root / 'backups' / 'snapshot.enc'
        return password, output, ['backup.py', '--mysql-container', 'unit-db', '--upload-container', 'unit-app',
            '--passphrase-file', str(password), '--output', str(output)]

    @contextlib.contextmanager
    def fake_processes(self, encryption_status=0, snapshot_fails=False, publish_race_output=None):
        calls = []
        def command(args, **kwargs):
            calls.append(args)
            if snapshot_fails: raise subprocess.CalledProcessError(1, args)
            if 'stdout' in kwargs: kwargs['stdout'].write(b'-- synthetic SQL fixture\n')
            elif 'cp' in args:
                directory = Path(args[-1]); directory.mkdir(parents=True); (directory / 'original.png').write_bytes(b'fixture')
            return SimpleNamespace(returncode=0)
        def encrypt(args, **kwargs):
            calls.append(args)
            Path(args[args.index('-out') + 1]).write_bytes(b'fake-encryption-output-for-unit-tests')
            if publish_race_output is not None: publish_race_output.write_bytes(b'concurrent-backup')
            return SimpleNamespace(stdin=io.BytesIO(), wait=lambda timeout: encryption_status, poll=lambda: encryption_status, kill=lambda: None)
        with patch.object(backup.subprocess, 'run', side_effect=command), patch.object(backup.subprocess, 'Popen', side_effect=encrypt), contextlib.redirect_stdout(io.StringIO()):
            yield calls

    def test_private_backup_digest_and_no_restore_claim(self):
        os.umask(0o022)
        password, output, argv = self.args()
        with patch('sys.argv', argv), self.fake_processes() as commands, contextlib.redirect_stdout(io.StringIO()) as printed:
            backup.main()
        self.assertEqual(output.stat().st_mode & 0o077, 0)
        self.assertEqual(output.parent.stat().st_mode & 0o077, 0)
        self.assertIn(backup.digest_file(output), output.with_suffix('.enc.sha256').read_text())
        self.assertFalse(json.loads(printed.getvalue())['restore_verified'])
        self.assertFalse(output.with_suffix('.enc.partial').exists())
        self.assertTrue(any('--single-transaction' in ' '.join(command) for command in commands))
        self.assertFalse(any(password.read_text() in ' '.join(command) for command in commands))

    def test_public_passphrase_file_is_rejected_before_any_process(self):
        password, output, argv = self.args(); password.chmod(0o644)
        with patch('sys.argv', argv), self.fake_processes() as commands:
            with self.assertRaises(ValueError): backup.main()
        self.assertEqual(commands, []); self.assertFalse(output.exists())

    def test_existing_backup_is_never_overwritten(self):
        password, output, argv = self.args(); output.parent.mkdir(); output.write_bytes(b'previous')
        with patch('sys.argv', argv), self.fake_processes() as commands:
            with self.assertRaises(ValueError): backup.main()
        self.assertEqual(output.read_bytes(), b'previous'); self.assertEqual(commands, [])

    def test_failed_snapshot_or_encryption_never_publishes_success(self):
        for snapshot_failure in (True, False):
            with self.subTest(snapshot_failure=snapshot_failure):
                password, output, argv = self.args()
                with patch('sys.argv', argv), self.fake_processes(encryption_status=1, snapshot_fails=snapshot_failure):
                    with self.assertRaises((RuntimeError, subprocess.CalledProcessError)): backup.main()
                self.assertFalse(output.exists()); self.assertFalse(output.with_suffix('.enc.partial').exists())

    def test_empty_passphrase_is_rejected(self):
        password, output, argv = self.args(); password.write_text('')
        with patch('sys.argv', argv), self.fake_processes() as commands:
            with self.assertRaises(ValueError): backup.main()
        self.assertEqual(commands, [])

    def test_unrelated_stale_partial_symlink_is_untouched_by_random_private_temporary(self):
        password, output, argv = self.args(); output.parent.mkdir()
        victim = self.root / 'must-not-change'; victim.write_bytes(b'preserve')
        partial = output.with_suffix('.enc.partial'); partial.symlink_to(victim)
        with patch('sys.argv', argv), self.fake_processes():
            backup.main()
        self.assertEqual(victim.read_bytes(), b'preserve'); self.assertTrue(output.exists())
        self.assertTrue(partial.is_symlink())
        self.assertEqual(list(output.parent.glob('.terra-backup-*.partial')), [])

    def test_concurrently_created_final_output_is_not_overwritten(self):
        password, output, argv = self.args()
        with patch('sys.argv', argv), self.fake_processes(publish_race_output=output):
            with self.assertRaises(FileExistsError): backup.main()
        self.assertEqual(output.read_bytes(), b'concurrent-backup')
        self.assertEqual(list(output.parent.glob('.terra-backup-*.partial')), [])


class GuardianTests(PrivateWorkspace):
    def snapshot(self, bad=()):
        result = {'checks': {key: {'ok': key not in bad} for key in guardian.NAMES + ['storage', 'docker', 'proxy_api', 'public', 'public_api', 'tunnel']},
            'containers': {key: {'exists': True, 'running': True, 'age': 500, 'instance': key + '-v1'} for key in guardian.NAMES}}
        return result

    def test_partial_inspect_failure_is_not_mistaken_for_daemon_failure(self):
        containers = [{'Name': '/terrafood-' + name, 'Id': name, 'State': {'Running': True,
            'StartedAt': '2026-09-20T00:00:00Z'}, 'NetworkSettings': {'Networks': {'unit': {'IPAddress': '127.0.0.1'}}}}
            for name in guardian.NAMES if name != 'agent-mcp']
        def command(args, *unused):
            if args[1] == 'inspect': return 1, json.dumps(containers)
            if args[1] == 'info': return 0, 'unit-daemon'
            if args[2] == 'terrafood-mysql': return 0, '1'
            if args[2] == 'terrafood-redis': return 0, 'PONG'
            raise AssertionError('unexpected command: ' + repr(args))
        with patch.object(guardian, 'command', side_effect=command), \
             patch.object(guardian, 'http', return_value={'ok': True}), \
             patch.object(guardian.socket, 'create_connection', return_value=contextlib.nullcontext()), \
             patch.object(guardian.shutil, 'disk_usage', return_value=SimpleNamespace(free=10*1024**3, total=100*1024**3)), \
             patch.object(guardian.os, 'statvfs', return_value=SimpleNamespace(f_favail=90, f_files=100)):
            snapshot = guardian.collect()
        self.assertTrue(snapshot['checks']['docker']['ok'])
        self.assertTrue(snapshot['checks']['docker']['inspect_partial'])
        self.assertFalse(snapshot['containers']['agent-mcp']['exists'])
        self.assertTrue(snapshot['containers']['backend']['exists'])

    def test_missing_agent_does_not_block_backend_recovery(self):
        snapshot = self.snapshot(['backend', 'agent-mcp']); snapshot['containers']['agent-mcp']['exists'] = False
        state = {'strikes': {'backend': 2}}
        self.assertEqual(guardian.decide(snapshot, state, 10000), {'target': 'backend', 'verb': 'restart'})

    def test_tunnel_recovery_is_independent_of_database_and_disk_failure(self):
        bad = ['tunnel', 'public', 'public_api', 'mysql', 'backend', 'storage', 'docker']
        self.assertEqual(guardian.decide(self.snapshot(bad), {'strikes': dict.fromkeys(bad, 2)}, 10000), {'target': 'tunnel', 'verb': 'restart'})

    def test_running_database_never_restarted_and_budget_limits_remain(self):
        self.assertIsNone(guardian.decide(self.snapshot(['mysql']), {'strikes': {'mysql': 2}}, 10000))
        state = {'strikes': {'backend': 2}, 'actions': [{'time': 9950, 'target': 'backend'}]}
        snapshot = self.snapshot(['backend'])
        self.assertIsNone(guardian.decide(snapshot, state, 10000))
        self.assertTrue(any(item['reason'] == 'recovery_budget_or_cooldown' for item in snapshot['blocked']))

    def test_stale_samples_new_instances_and_startup_grace_reset_strikes(self):
        for state, snapshot in [({'last_sample': 9000, 'strikes': {'backend': 9}}, self.snapshot(['backend'])),
            ({'instances': {'backend': 'old'}, 'strikes': {'backend': 9}}, self.snapshot(['backend']))]:
            self.assertIsNone(guardian.decide(snapshot, state, 10000))
        snapshot = self.snapshot(['backend']); snapshot['containers']['backend']['age'] = 30
        state = {'strikes': {'backend': 9}}
        self.assertIsNone(guardian.decide(snapshot, state, 10000)); self.assertEqual(state['strikes']['backend'], 0)

    @contextlib.contextmanager
    def controlled_main(self, collect=None):
        with patch.object(guardian, 'ROOT', self.root), patch.object(guardian, 'open', return_value=io.StringIO(), create=True), \
             patch.object(guardian.fcntl, 'flock'), patch.object(guardian.Path, 'exists', return_value=False), \
             patch.object(guardian, 'collect', collect or Mock(return_value=self.snapshot())) as collector, \
             patch.object(guardian, 'command') as recovery, patch.object(guardian.time, 'time', return_value=10000), \
             patch('sys.argv', ['terrafood_guardian.py']), contextlib.redirect_stdout(io.StringIO()):
            yield collector, recovery

    def test_corrupt_json_replaces_old_healthy_with_fresh_unknown_without_recovery(self):
        (self.root / 'state.json').write_text('{invalid')
        (self.root / 'status.json').write_text('{"status":"healthy","time":1}')
        with self.controlled_main() as (collector, recovery):
            self.assertEqual(guardian.main(), 2); collector.assert_not_called(); recovery.assert_not_called()
        status = json.loads((self.root / 'status.json').read_text())
        self.assertEqual(status['status'], 'unknown'); self.assertEqual(status['time'], 10000); self.assertGreater(status['expires_at'], 10000)
        self.assertEqual((self.root / 'state.json').read_text(), '{invalid')

    def test_semantically_invalid_state_also_fails_closed_with_fresh_status(self):
        for value in ([], {'strikes': []}, {'actions': [{'time': 'bad'}]}, {'last_sample': 'yesterday'}):
            with self.subTest(value=value):
                (self.root / 'state.json').write_text(json.dumps(value))
                with self.controlled_main() as (_, recovery):
                    self.assertEqual(guardian.main(), 2); recovery.assert_not_called()
                self.assertEqual(json.loads((self.root / 'status.json').read_text())['status'], 'unknown')

    def test_probe_exception_publishes_unknown_instead_of_stale_healthy(self):
        with self.controlled_main(Mock(side_effect=RuntimeError('probe failed'))) as (_, recovery):
            self.assertEqual(guardian.main(), 2); recovery.assert_not_called()
        self.assertEqual(json.loads((self.root / 'status.json').read_text())['reason'], 'probe_exception')

    def test_maintenance_lock_publishes_expiring_status_without_collecting(self):
        with self.controlled_main() as (collector, recovery), patch.object(guardian.fcntl, 'flock', side_effect=BlockingIOError):
            self.assertEqual(guardian.main(), 0); collector.assert_not_called(); recovery.assert_not_called()
        status = json.loads((self.root / 'status.json').read_text())
        self.assertEqual(status['status'], 'maintenance'); self.assertEqual(status['reason'], 'maintenance_lock'); self.assertGreater(status['expires_at'], 10000)


if __name__ == '__main__':
    unittest.main()
