import json
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import configuration


PUBLIC={'schema':1,'map_provider':'osm','tianditu_key':'','api_base_url':'/api'}
RUNTIME={**dict(zip(configuration.RUNTIME_KEYS[:4],(c*40 for c in 'abcd'))),
         'OPENAI_API_KEY':'unit-model-credential-not-real','OPENAI_BASE_URL':'https://provider.example/v1','AGENT_MODEL':'unit-model'}
FRONTEND={'schema':1,'fingerprint':configuration.frontend_identity(PUBLIC),'map_provider':'osm','api_base_url':'/api',
          'capabilities':['osm-fallback'],'assets':{'/assets/Home-test.js':'1'*64}}


def write_public(root):
    path=root/'frontend.json';path.write_text(json.dumps(PUBLIC));path.chmod(0o600);return path


def service_environment(name):
    fields={'backend':['AGENT_INTERNAL_TOKEN','MCP_BACKEND_TOKEN','AGENT_CONTEXT_SECRET'],
            'agent-api':['AGENT_INTERNAL_TOKEN','MCP_INTERNAL_TOKEN','OPENAI_API_KEY','OPENAI_BASE_URL','AGENT_MODEL'],
            'agent-mcp':['MCP_INTERNAL_TOKEN','MCP_BACKEND_TOKEN'],'web':[]}[name]
    values={key:RUNTIME[key] for key in fields}
    if name=='backend': values['UPLOAD_DIRECTORY']='/data/uploads'
    return values
