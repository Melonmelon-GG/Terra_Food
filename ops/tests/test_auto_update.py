import contextlib
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import auto_update as updater
import release
import configuration
from fixture_configuration import PUBLIC,RUNTIME,FRONTEND,write_public

SHA='a'*40
OLD='b'*40
IMAGES={name:'sha256:'+str(i)*64 for i,name in enumerate(release.SERVICES,1)}


class UpdateTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root=Path(self.temp.name)
        self.config={'repo':root/'repo','state':root/'state','lock':str(root/'lock'),
                     'compose':root/'compose.yml','env_file':root/'runtime.env','project':'isolated','health_url':'http://127.0.0.1:1'}
        self.config['state'].mkdir(mode=0o700)
        self.config['frontend_config']=write_public(root)
        self.prepared={'schema':3,'revision':SHA,'images':IMAGES,'web_entry_sha256':'c'*64,'frontend':FRONTEND,
                       'runtime_fingerprint':configuration.runtime_identity(RUNTIME)}
        release.save(self.config['state']/'deployed.json',{**self.prepared,'revision':OLD})
        self.stack=contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
        self.sync=self.stack.enter_context(patch.object(updater,'synchronize',return_value=SHA))
        self.runtime=self.stack.enter_context(patch.object(release,'validate_runtime',return_value=RUNTIME))
        self.effective=self.stack.enter_context(patch.object(release,'effective_runtime_identity',return_value=configuration.runtime_identity(RUNTIME)))
        self.manifest=self.stack.enter_context(patch.object(release,'validate_manifest'))
        self.verify=self.stack.enter_context(patch.object(release,'verify_running'))
        self.migrations=self.stack.enter_context(patch.object(updater,'migration_changes',return_value=False))
        self.build=self.stack.enter_context(patch.object(release,'run',side_effect=self.finish_build))
        self.apply=self.stack.enter_context(patch.object(release,'apply',side_effect=self.finish_apply))

    def finish_build(self,*args,**kwargs): release.save(self.config['state']/'prepared.json',self.prepared)
    def finish_apply(self,args): release.save(args.deployed,self.prepared)
    def saved(self,name): return json.loads((self.config['state']/name).read_text())

    def test_old_deployment_with_current_checkout_still_applies(self):
        self.assertEqual(updater.update(self.config),0)
        self.build.assert_called_once()
        self.apply.assert_called_once()

    def test_failed_build_remains_retryable(self):
        self.build.side_effect=subprocess.CalledProcessError(1,['build'])
        with self.assertRaises(subprocess.CalledProcessError): updater.update(self.config)
        self.assertEqual(self.saved('deployed.json')['revision'],OLD)
        self.apply.assert_not_called()
        self.build.side_effect=self.finish_build
        self.assertEqual(updater.update(self.config),0)

    def test_failed_apply_reuses_prepared_images(self):
        self.apply.side_effect=RuntimeError('health failed')
        with self.assertRaises(RuntimeError): updater.update(self.config)
        self.assertEqual(self.saved('deployed.json')['revision'],OLD)
        self.apply.side_effect=self.finish_apply
        self.assertEqual(updater.update(self.config),0)
        self.assertEqual(self.build.call_count,1)

    def test_current_requires_actual_runtime_verification(self):
        release.save(self.config['state']/'deployed.json',self.prepared)
        self.assertEqual(updater.update(self.config),0)
        self.verify.assert_called_once()
        self.build.assert_not_called()
        self.apply.assert_not_called()

    def test_current_source_with_old_frontend_configuration_rebuilds(self):
        release.save(self.config['state']/'deployed.json',{**self.prepared,'frontend':{}})
        self.assertEqual(updater.update(self.config),0)
        self.build.assert_called_once();self.apply.assert_called_once()

    def test_changed_runtime_reapplies_without_rebuilding_matching_images(self):
        release.save(self.config['state']/'deployed.json',{**self.prepared,'runtime_fingerprint':'old'})
        release.save(self.config['state']/'prepared.json',self.prepared)
        self.assertEqual(updater.update(self.config),0)
        self.build.assert_not_called();self.apply.assert_called_once()

    def test_missing_frontend_input_blocks_before_fetch(self):
        self.config['frontend_config'].unlink()
        with self.assertRaisesRegex(updater.UpdateError,'FrontendConfigurationNotReady'): updater.update(self.config)
        self.sync.assert_not_called();self.apply.assert_not_called()

    def test_same_revision_wrong_container_is_reapplied(self):
        release.save(self.config['state']/'deployed.json',self.prepared)
        release.save(self.config['state']/'prepared.json',self.prepared)
        self.verify.side_effect=RuntimeError('wrong image')
        self.assertEqual(updater.update(self.config),0)
        self.build.assert_not_called()
        self.apply.assert_called_once()

    def test_missing_image_requires_rebuild(self):
        release.save(self.config['state']/'prepared.json',self.prepared)
        self.manifest.side_effect=ValueError('missing image')
        self.assertEqual(updater.update(self.config),0)
        self.build.assert_called_once()

    def test_missing_prepared_report_fails(self):
        self.build.side_effect=None
        with self.assertRaisesRegex(updater.UpdateError,'PreparedManifestMissing'): updater.update(self.config)
        self.apply.assert_not_called()

    def test_fetch_failure_cannot_report_current(self):
        self.sync.side_effect=updater.UpdateError('FetchFailed')
        with self.assertRaises(updater.UpdateError): updater.update(self.config)
        self.assertEqual(self.saved('status.json')['code'],'FetchFailed')
        self.build.assert_not_called()

    def test_bad_runtime_blocks_deployment(self):
        self.runtime.side_effect=ValueError('credentials')
        with self.assertRaisesRegex(updater.UpdateError,'RuntimeCredentialsNotReady'): updater.update(self.config)
        self.build.assert_not_called()

    def test_unknown_deployment_does_not_automigrate(self):
        (self.config['state']/'deployed.json').unlink()
        with self.assertRaisesRegex(updater.UpdateError,'InitialReviewedDeploymentRequired'): updater.update(self.config)
        self.apply.assert_not_called()

    def test_migrations_require_review(self):
        self.migrations.return_value=True
        with self.assertRaisesRegex(updater.UpdateError,'MigrationReviewRequired'): updater.update(self.config)
        self.apply.assert_not_called()

    def test_timeout_does_not_mark_deployed(self):
        self.build.side_effect=subprocess.TimeoutExpired(['build'],6000)
        with self.assertRaises(subprocess.TimeoutExpired): updater.update(self.config)
        self.assertEqual(self.saved('deployed.json')['revision'],OLD)
        self.assertEqual(self.saved('status.json')['code'],'TimeoutExpired')

    def test_interrupt_does_not_mark_deployed(self):
        self.build.side_effect=updater.UpdateError('Interrupted')
        with self.assertRaises(updater.UpdateError): updater.update(self.config)
        self.assertEqual(self.saved('deployed.json')['revision'],OLD)

    def test_missing_deployed_report_fails(self):
        self.apply.side_effect=None
        with self.assertRaisesRegex(updater.UpdateError,'DeployedManifestMissing'): updater.update(self.config)

    def test_check_does_not_fetch_or_write(self):
        self.stack.enter_context(patch.object(updater,'git',return_value=SHA))
        (self.config['state']/'deployed.json').unlink()
        before={p.name:p.read_bytes() for p in self.config['state'].iterdir()}
        self.assertEqual(updater.inspect(self.config),1)
        self.assertEqual(before,{p.name:p.read_bytes() for p in self.config['state'].iterdir()})
        self.sync.assert_not_called()
        self.build.assert_not_called()


class ReleaseTests(unittest.TestCase):
    def response(self,body,kind='text/html'):
        result=io.BytesIO(body);result.status=200;result.headers={'Content-Type':kind}
        return result

    def test_wrong_html_cannot_pass_health(self):
        with patch.object(release.urllib.request,'urlopen',return_value=self.response(b'<html>old version</html>')):
            self.assertFalse(release.healthy('http://fixture','0'*64))

    def test_spa_html_instead_of_javascript_is_failure(self):
        html=b'<html><script src="/assets/index-new.js"></script></html>'
        with patch.object(release.urllib.request,'urlopen',side_effect=[self.response(html),self.response(b'<html>fallback</html>')]):
            self.assertFalse(release.healthy('http://fixture',hashlib.sha256(html).hexdigest()))

    def test_client_timeout_is_nonzero(self):
        with self.assertRaises(subprocess.TimeoutExpired):
            release.run([sys.executable,'-c','import time;time.sleep(60)'],timeout=0.1,capture_output=True)

    def test_wrong_source_label_rejected(self):
        with patch.object(release,'capture',side_effect=['exists',OLD]):
            with self.assertRaisesRegex(ValueError,'revision mismatch'):
                release.validate_manifest({'revision':SHA,'images':IMAGES})

    def test_missing_component_rejected(self):
        with self.assertRaisesRegex(ValueError,'release must contain'):
            release.validate_manifest({'revision':SHA,'images':{'web':IMAGES['web']}})

    def test_reused_credentials_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            env=Path(tmp)/'runtime.env'
            env.write_text('\n'.join(k+'='+'x'*32 for k in ['AGENT_INTERNAL_TOKEN','MCP_INTERNAL_TOKEN','MCP_BACKEND_TOKEN','AGENT_CONTEXT_SECRET']))
            env.chmod(0o600)
            with self.assertRaises(ValueError): release.validate_runtime(env)


class TransportTests(unittest.TestCase):
    def test_retry_uses_same_remote_with_alternate_transport(self):
        config={'repo':Path('/fixture/repo')}
        with patch.object(updater,'git',side_effect=['main','',SHA]), patch.object(release,'run',side_effect=[subprocess.TimeoutExpired(['git'],60),None,None]) as command, patch.object(updater.time,'sleep'):
            self.assertEqual(updater.synchronize(config),SHA)
        second=command.call_args_list[1].args[0]
        self.assertIn('--ipv4',second);self.assertIn('http.version=HTTP/1.1',second)
        self.assertIn('origin',second)

    def test_all_fetch_failures_do_not_merge_local_head(self):
        with patch.object(updater,'git',side_effect=['main','']), patch.object(release,'run',side_effect=subprocess.TimeoutExpired(['git'],60)) as command, patch.object(updater.time,'sleep'):
            with self.assertRaisesRegex(updater.UpdateError,'FetchFailed'): updater.synchronize({'repo':Path('/fixture/repo')})
        self.assertEqual(command.call_count,3)
        self.assertFalse(any('merge' in call.args[0] for call in command.call_args_list))


if __name__=='__main__': unittest.main()
