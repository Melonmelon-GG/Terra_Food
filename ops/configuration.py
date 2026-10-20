"""Explicit public build inputs and private runtime configuration identities."""
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
from urllib.parse import urlsplit

CONTRACT = 1
FRONTEND_LABEL = 'org.terrafood.frontend-config'
BUILD_DESCRIPTOR = '/.well-known/terrafood-build.json'
RUNTIME_KEYS = ('AGENT_INTERNAL_TOKEN', 'MCP_INTERNAL_TOKEN', 'MCP_BACKEND_TOKEN',
                'AGENT_CONTEXT_SECRET', 'OPENAI_API_KEY', 'OPENAI_BASE_URL', 'AGENT_MODEL')


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()


def load_frontend(path=None):
    source = path or os.environ.get('TERRA_FRONTEND_CONFIG')
    if not source:
        raise ValueError('FrontendConfigurationRequired')
    path = Path(source)
    if path.stat().st_mode & 0o077:
        raise ValueError('FrontendConfigurationMustBePrivate')
    data = json.loads(path.read_text())
    allowed = {'schema', 'map_provider', 'tianditu_key', 'api_base_url'}
    if not isinstance(data, dict) or set(data)-allowed:
        raise ValueError('UnexpectedFrontendConfigurationField')
    if data.get('schema') != CONTRACT or data.get('map_provider') not in ('tianditu', 'osm'):
        raise ValueError('ExplicitMapProviderRequired')
    key = data.get('tianditu_key', '')
    api = data.get('api_base_url', '/api')
    if not isinstance(api, str) or not re.fullmatch(r'/[A-Za-z0-9_/-]+', api) or '//' in api:
        raise ValueError('SameOriginApiPathRequired')
    if data['map_provider'] == 'tianditu':
        if not isinstance(key, str) or not re.fullmatch(r'[0-9a-fA-F]{32}', key) or len(set(key)) == 1:
            raise ValueError('ValidBrowserTiandituKeyRequired')
    elif key:
        raise ValueError('UnexpectedKeyForOsmMode')
    return {'schema': CONTRACT, 'map_provider': data['map_provider'], 'tianditu_key': key,
            'api_base_url': api.rstrip('/')}


def frontend_identity(data):
    return hashlib.sha256(canonical(data)).hexdigest()


def frontend_matches(manifest, data):
    frontend = manifest.get('frontend', {})
    return (frontend.get('schema') == CONTRACT and frontend.get('fingerprint') == frontend_identity(data)
            and frontend.get('map_provider') == data['map_provider']
            and frontend.get('api_base_url') == data['api_base_url'])


def runtime_values(path):
    """Selected Compose inputs must be literal; reject ambiguous interpolation.

    Never include values in parse errors. Other application variables are left to
    Compose; the release tool does not copy the full runtime file into a build.
    """
    result = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        key = key.strip()
        if key not in RUNTIME_KEYS:
            continue
        if key in result:
            raise ValueError('DuplicateRuntimeConfigurationField')
        value = value.strip()
        if value.startswith('"'):
            try: value = json.loads(value)
            except (ValueError, TypeError): raise ValueError('InvalidQuotedRuntimeConfiguration') from None
        elif value.startswith("'") and value.endswith("'"):
            value = value[1:-1]
        if not isinstance(value, str) or any(ord(c)<32 for c in value) or '$' in value:
            raise ValueError('LiteralRuntimeConfigurationRequired')
        result[key] = value
    internal = RUNTIME_KEYS[:4]
    if any(len(result.get(k, ''))<32 for k in internal) or len({result.get(k) for k in internal}) != 4:
        raise ValueError('DistinctInternalCredentialsRequired')
    model_key = result.get('OPENAI_API_KEY', '')
    if (len(model_key)<16 or model_key != model_key.strip() or any(c.isspace() for c in model_key)
            or model_key.lower().startswith(('your-', 'replace-', 'change-me', 'sk-your', 'ci-dummy'))):
        raise ValueError('ModelCredentialRequired')
    model = result.get('AGENT_MODEL', '')
    if not model.strip() or model != model.strip() or len(model)>200:
        raise ValueError('ExplicitModelRequired')
    base = result.get('OPENAI_BASE_URL', '')
    url = urlsplit(base)
    if url.scheme not in ('http', 'https') or not url.hostname or url.username or url.password or url.query or url.fragment:
        raise ValueError('ExplicitModelEndpointRequired')
    return result


def runtime_identity(values):
    return hmac.new(values['AGENT_CONTEXT_SECRET'].encode(), canonical(values), hashlib.sha256).hexdigest()


def agent_identity(values):
    model = {'api_key': values['OPENAI_API_KEY'], 'base_url': values['OPENAI_BASE_URL'], 'model': values['AGENT_MODEL']}
    return hmac.new(values['AGENT_INTERNAL_TOKEN'].encode(), canonical(model), hashlib.sha256).hexdigest()
