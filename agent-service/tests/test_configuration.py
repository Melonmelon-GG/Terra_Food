import dataclasses
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from app import main
from app.config import Settings, model_configuration_identity, model_configuration_ready


class ConfigurationTests(unittest.TestCase):
    def config(self, **changes):
        return dataclasses.replace(Settings(), internal_token='internal-test-token-'*3,
            openai_api_key='test-model-credential-'*2, openai_base_url='https://provider.example/v1',
            model='test-model', **changes)

    def test_missing_key_is_not_ready(self):
        value=dataclasses.replace(self.config(),openai_api_key='')
        self.assertFalse(model_configuration_ready(value))

    def test_endpoint_credentials_or_query_are_rejected(self):
        for base in ['https://user:password@provider.example/v1','https://provider.example/v1?key=hidden']:
            self.assertFalse(model_configuration_ready(dataclasses.replace(self.config(),openai_base_url=base)))

    def test_each_model_input_changes_identity(self):
        value=self.config(); original=model_configuration_identity(value)
        for key,replacement in [('openai_api_key','different-test-credential'),('openai_base_url','https://second.example/v1'),('model','next-model')]:
            self.assertNotEqual(original,model_configuration_identity(dataclasses.replace(value,**{key:replacement})))

    def test_ready_requires_authentication_and_does_not_contact_provider(self):
        value=self.config()
        with patch.object(main,'settings',value), patch.object(main,'ChatOpenAI',side_effect=AssertionError('Model must not start')):
            client=TestClient(main.app)
            self.assertEqual(client.get('/ready').status_code,403)
            self.assertEqual(client.get('/ready',headers={'X-Agent-Internal-Token':'wrong'}).status_code,403)
            response=client.get('/ready',headers={'X-Agent-Internal-Token':value.internal_token})
            self.assertEqual(response.status_code,200)
            self.assertEqual(response.json()['configuration'],model_configuration_identity(value))
            self.assertNotIn(value.openai_api_key,response.text)
            self.assertNotIn(value.openai_base_url,response.text)

    def test_health_does_not_claim_configuration_is_ready(self):
        value=dataclasses.replace(self.config(),openai_api_key='')
        with patch.object(main,'settings',value):
            client=TestClient(main.app)
            self.assertEqual(client.get('/health').status_code,200)
            self.assertEqual(client.get('/ready',headers={'X-Agent-Internal-Token':value.internal_token}).status_code,503)


if __name__=='__main__': unittest.main()
