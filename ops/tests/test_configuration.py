import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

from fixture_configuration import PUBLIC, RUNTIME, FRONTEND, write_public
import configuration
import release
import audit_configuration


class ConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)

    def public_file(self,data):
        path=self.root/'public.json';path.write_text(json.dumps(data));path.chmod(0o600);return path

    def test_release_requires_explicit_provider_configuration(self):
        with patch.dict('os.environ',{},clear=True):
            with self.assertRaisesRegex(ValueError,'FrontendConfigurationRequired'): configuration.load_frontend()
        for key in ['', 'your-tianditu-key', '0'*32]:
            with self.assertRaisesRegex(ValueError,'BrowserTiandituKey'):
                configuration.load_frontend(self.public_file({**PUBLIC,'map_provider':'tianditu','tianditu_key':key}))

    def test_server_secret_cannot_be_a_public_build_field(self):
        with self.assertRaisesRegex(ValueError,'UnexpectedFrontend'):
            configuration.load_frontend(self.public_file({**PUBLIC,'OPENAI_API_KEY':RUNTIME['OPENAI_API_KEY']}))

    def test_provider_key_and_api_path_change_image_identity(self):
        original=configuration.frontend_identity(PUBLIC)
        primary={**PUBLIC,'map_provider':'tianditu','tianditu_key':'ab'*16}
        changed={**primary,'tianditu_key':'cd'*16}
        self.assertNotEqual(original,configuration.frontend_identity(primary))
        self.assertNotEqual(configuration.frontend_identity(primary),configuration.frontend_identity(changed))
        self.assertNotEqual(original,configuration.frontend_identity({**PUBLIC,'api_base_url':'/api/v2'}))
        self.assertTrue(configuration.frontend_matches({'frontend':FRONTEND},PUBLIC))
        self.assertFalse(configuration.frontend_matches({'frontend':FRONTEND},primary))

    def test_private_build_file_and_same_origin_api_are_enforced(self):
        path=write_public(self.root);path.chmod(0o644)
        with self.assertRaisesRegex(ValueError,'Private'): configuration.load_frontend(path)
        for api in ['https://external.example/api','//external.example/api','/api?token=bad']:
            with self.assertRaisesRegex(ValueError,'SameOrigin'): configuration.load_frontend(self.public_file({**PUBLIC,'api_base_url':api}))

    def test_final_descriptor_must_have_capabilities_and_real_asset_hashes(self):
        release.validate_frontend_descriptor(FRONTEND,PUBLIC)
        for bad in [{**FRONTEND,'capabilities':[]},{**FRONTEND,'assets':{}},
                    {**FRONTEND,'assets':{'/assets/../escape.js':'1'*64}}]:
            with self.assertRaises(ValueError): release.validate_frontend_descriptor(bad,PUBLIC)

    def test_spa_fallback_cannot_impersonate_a_build_descriptor(self):
        response=io.BytesIO(b'<html>fallback</html>');response.status=200
        with patch.object(release.urllib.request,'urlopen',return_value=response):
            with self.assertRaises(ValueError): release.verify_frontend_http('http://fixture',FRONTEND)

    def runtime_file(self,data):
        path=self.root/'runtime.env';path.write_text('\n'.join(k+'='+v for k,v in data.items()));path.chmod(0o600);return path

    def test_model_key_endpoint_and_model_are_all_required(self):
        for name in ['OPENAI_API_KEY','OPENAI_BASE_URL','AGENT_MODEL']:
            data=dict(RUNTIME);data.pop(name)
            with self.assertRaises(ValueError): configuration.runtime_values(self.runtime_file(data))
        self.assertEqual(configuration.runtime_values(self.runtime_file(RUNTIME)),RUNTIME)

    def test_no_credential_values_in_validation_errors(self):
        secret='private-test-value-with-$-interpolation'
        with self.assertRaises(ValueError) as error:
            configuration.runtime_values(self.runtime_file({**RUNTIME,'OPENAI_API_KEY':secret}))
        self.assertNotIn(secret,str(error.exception))

    def test_runtime_rotation_changes_identity_without_saving_values(self):
        old=configuration.runtime_identity(RUNTIME)
        for name in configuration.RUNTIME_KEYS:
            replacement='https://new.example/v1' if name=='OPENAI_BASE_URL' else RUNTIME[name]+'changed'
            self.assertNotEqual(old,configuration.runtime_identity({**RUNTIME,name:replacement}))
        self.assertNotIn(RUNTIME['OPENAI_API_KEY'],old)

    def test_effective_mail_and_mount_changes_invalidate_same_source_deployment(self):
        args=SimpleNamespace(project='fixture',env_file=Path('/fixture.env'),compose=Path('/fixture.json'))
        def identity(mail,mount):
            services={name:{'environment':{},'volumes':[mount]} for name in release.SERVICES}
            services['backend']['environment']['MAIL_HOST']=mail
            with patch.object(release,'capture',return_value=json.dumps({'services':services})):
                return release.effective_runtime_identity(args,Path('/override.json'),RUNTIME)
        first=identity('first.example','first-volume')
        self.assertNotEqual(first,identity('second.example','first-volume'))
        self.assertNotEqual(first,identity('first.example','second-volume'))

    def test_server_model_key_cannot_leak_to_web_or_mcp_roles(self):
        for role in ['web','agent-mcp']:
            services={name:{'environment':{}} for name in release.SERVICES}
            services[role]['environment']['OPENAI_API_KEY']=RUNTIME['OPENAI_API_KEY']
            with self.assertRaisesRegex(ValueError,'UnexpectedCredentialRole'):
                release.validate_credential_roles(services)

    def test_public_model_list_does_not_prove_api_key_validity(self):
        anonymous=io.BytesIO(b'{}');anonymous.status=200
        authorized=io.BytesIO(json.dumps({'data':[{'id':RUNTIME['AGENT_MODEL']}]}).encode());authorized.status=200
        with patch.object(audit_configuration.urllib.request,'build_opener') as build:
            build.return_value.open.side_effect=[anonymous,authorized]
            result=audit_configuration.probe_provider(RUNTIME)
        self.assertTrue(result['authorized_list_request_succeeded'])
        self.assertIsNone(result['authentication_accepted'])
        self.assertTrue(result['configured_model_listed'])


if __name__=='__main__': unittest.main()
