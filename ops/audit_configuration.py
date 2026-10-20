#!/usr/bin/env python3
"""Read-only source -> Compose -> container configuration inventory.

Only field names, booleans and error categories are emitted. No models are run.
--probe-provider optionally calls the configured provider's GET /models once;
this is authentication/model-list evidence, not an inference readiness proof.
"""
import argparse
import json
from pathlib import Path
import re
from types import SimpleNamespace
import urllib.request
import urllib.error

import configuration
import release


def declarations(repo):
    entries=[]
    for directory,consumer in [('dayanfood-backend/src/main','backend'),('agent-service/app','agent'),('web/src','browser')]:
        for path in sorted((repo/directory).rglob('*')):
            if not path.is_file() or path.suffix not in ('.yml','.yaml','.java','.py','.ts','.vue'):
                continue
            for number,line in enumerate(path.read_text(encoding='utf-8').splitlines(),1):
                patterns=[(r'\$\{([A-Z][A-Z0-9_]*)(?::([^}]*))?\}', 'spring'),
                          (r'os\.getenv\(["\']([A-Z][A-Z0-9_]*)["\']', 'python'),
                          (r'import\.meta\.env\.([A-Z][A-Z0-9_]*)', 'vite')]
                for pattern,kind in patterns:
                    for match in re.finditer(pattern,line):
                        entries.append({'consumer':consumer,'name':match[1],'kind':kind,
                            'source':str(path.relative_to(repo)),'line':number})
    return entries


def environment(container):
    return dict(value.split('=',1) for value in container['Config'].get('Env',[]) if '=' in value)


def audit(config):
    repo=Path(config['repo']);state=Path(config['state'])
    report={'source_inputs':declarations(repo),'services':{},'findings':[]}
    def finding(code, service=None, names=None):
        report['findings'].append({'code':code,'service':service,'fields':names or []})
    args=SimpleNamespace(project=config['project'],env_file=Path(config['env_file']),compose=Path(config['compose']))
    command=release.compose_command(args,state/'deployed.compose.json')
    resolved=json.loads(release.capture(command+['config','--format','json']))
    containers={}
    for name,service in resolved['services'].items():
        ids=release.capture(command+['ps','-aq',name]).split()
        if not ids:
            report['services'][name]={'present':False};finding('ContainerMissing',name);continue
        row=json.loads(release.capture(release.DOCKER+['inspect',ids[0]]))[0]
        containers[name]=row
        actual=environment(row);expected=service.get('environment',{})
        mismatch=[key for key,value in expected.items() if value is not None and actual.get(key)!=str(value)]
        report['services'][name]={'present':True,'running':row['State']['Running'],
            'compose_fields':sorted(expected),'missing_or_different_fields':mismatch}
        if mismatch: finding('ComposeContainerDrift',name,mismatch)
        bindings=row['HostConfig'].get('PortBindings') or {}
        if name in ('mysql','redis') and any(binding.get('HostIp') not in ('127.0.0.1','::1') for values in bindings.values() for binding in values or []):
            finding('DataPortNotLoopback',name)
    try:
        public=configuration.load_frontend(config.get('frontend_config'))
        deployed=json.loads((state/'deployed.json').read_text())
        report['frontend']={'configured':True,'provider':public['map_provider'],
            'deployed_configuration_matches':configuration.frontend_matches(deployed,public)}
        if not report['frontend']['deployed_configuration_matches']: finding('FrontendBuildConfigurationDrift','web')
    except (OSError,ValueError,KeyError):
        report['frontend']={'configured':False};finding('FrontendBuildConfigurationNotReady','web')
    try:
        values=release.validate_runtime(Path(config['env_file']))
        agent=environment(containers['agent-api'])
        fields=['OPENAI_API_KEY','OPENAI_BASE_URL','AGENT_MODEL']
        mismatch=[key for key in fields if agent.get(key)!=values[key]]
        report['agent']={'model_key_configured':bool(agent.get('OPENAI_API_KEY')),
            'runtime_matches_file':not mismatch,'provider_access':'not-probed'}
        if mismatch: finding('ModelRuntimeConfigurationDrift','agent-api',mismatch)
        for service,names in {'backend':['AGENT_INTERNAL_TOKEN','MCP_BACKEND_TOKEN','AGENT_CONTEXT_SECRET'],
                              'agent-api':['AGENT_INTERNAL_TOKEN','MCP_INTERNAL_TOKEN'],
                              'agent-mcp':['MCP_INTERNAL_TOKEN','MCP_BACKEND_TOKEN']}.items():
            actual=environment(containers[service]);bad=[name for name in names if actual.get(name)!=values[name]]
            if bad: finding('ServiceCredentialDrift',service,bad)
    except (OSError,ValueError,KeyError): finding('RuntimeCredentialContractNotReady','agent-api')
    if 'web' in containers and environment(containers['web']).get('OPENAI_API_KEY'):
        finding('ModelCredentialInBrowserContainer','web')
    for name in release.SERVICES:
        if name not in containers: continue
        actual=environment(containers[name])
        extras=[key for key in configuration.RUNTIME_KEYS if actual.get(key) and key not in release.SERVICE_INPUTS[name]]
        if extras: finding('UnexpectedCredentialRole',name,extras)
    if 'backend' in containers:
        backend=environment(containers['backend'])
        if backend.get('DB_USERNAME')=='root': finding('ApplicationUsesDatabaseRoot','backend')
        if backend.get('SESSION_COOKIE_SECURE','true').lower()!='true': finding('SessionCookieNotSecure','backend')
        if backend.get('MAIL_HOST'):
            if backend.get('MAIL_CHECK_SERVER_IDENTITY','true').lower()!='true': finding('MailHostnameVerificationDisabled','backend')
            if (backend.get('MAIL_SSL_ENABLED','false').lower()!='true'
                    and backend.get('MAIL_STARTTLS_REQUIRED','true').lower()!='true'):
                finding('MailEncryptionNotRequired','backend')
        report['backend']={'mail_configured':bool(backend.get('MAIL_HOST')),
            'upload_directory_explicit':bool(backend.get('UPLOAD_DIRECTORY')),
            'private_environment_fields':sorted(backend)}
    # Frozen, fully expanded Compose JSON can match a stale container while the
    # operator's runtime file has already changed. Report direct-name drift too.
    literals={};unresolved=[]
    for line in Path(config['env_file']).read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line: continue
        key,value=line.split('=',1);key=key.strip();value=value.strip()
        if '$' in value:
            unresolved.append(key);continue
        if value.startswith('"'):
            try: value=json.loads(value)
            except ValueError: unresolved.append(key);continue
        elif value.startswith("'") and value.endswith("'"): value=value[1:-1]
        literals[key]=value
    for name,row in containers.items():
        actual=environment(row)
        drift=[key for key,value in literals.items() if key in actual and actual[key]!=value]
        if drift: finding('RuntimeFileContainerDrift',name,drift)
    report['runtime_fields_needing_compose_interpolation_review']=sorted(unresolved)
    for item in report['source_inputs']:
        consumer=item['consumer'];name=item['name']
        services=['backend'] if consumer=='backend' else ['agent-api','agent-mcp'] if consumer=='agent' else []
        item['explicit_in_container']=any(name in environment(containers[service]) for service in services if service in containers)
    # Liveness/config wiring is deliberately distinct from third-party delivery.
    report['not_verified']=['SMTP delivery','Provider inference','Affected user network','Database account grants',
                            'Loaded Spring properties overridden by CLI or mounted configuration']
    report['passed']=not report['findings']
    return report


def probe_provider(values):
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args,**kwargs): return None
    url=values['OPENAI_BASE_URL'].rstrip('/')+'/models'
    anonymous_status=None
    try:
        with urllib.request.build_opener(NoRedirect()).open(urllib.request.Request(url,headers={'User-Agent':'TerraFood-Configuration-Audit/1.0'}),timeout=8) as anonymous:
            anonymous_status=anonymous.status
    except urllib.error.HTTPError as error: anonymous_status=error.code
    except Exception: pass
    request=urllib.request.Request(url,
        headers={'Authorization':'Bearer '+values['OPENAI_API_KEY'],'User-Agent':'TerraFood-Configuration-Audit/1.0'})
    try:
        with urllib.request.build_opener(NoRedirect()).open(request,timeout=8) as response:
            data=json.loads(response.read(1048576))
            listed=data.get('data') if isinstance(data,dict) else None
            return {'status':response.status,'anonymous_status':anonymous_status,
                'authentication_accepted':True if anonymous_status in (401,403) and response.status==200 else None,
                'authorized_list_request_succeeded':response.status==200,
                'model_list_schema_valid':isinstance(listed,list),
                'model_count':len(listed) if isinstance(listed,list) else None,
                'configured_model_listed':any(item.get('id')==values['AGENT_MODEL'] for item in listed if isinstance(item,dict)) if isinstance(listed,list) else None}
    except urllib.error.HTTPError as error: return {'status':error.code,'authentication_accepted':False if error.code in (401,403) else None}
    except Exception as error: return {'error':type(error).__name__,'authentication_accepted':None}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('--probe-provider',action='store_true')
    args=parser.parse_args();config=json.loads(args.config.read_text())
    try:
        result=audit(config)
        if args.probe_provider:
            result['provider_probe']=probe_provider(release.validate_runtime(Path(config['env_file'])))
            if result['provider_probe'].get('authentication_accepted') is False: result['passed']=False
    except Exception as error:
        result={'passed':False,'error':type(error).__name__}
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if result['passed'] else 1


if __name__=='__main__': raise SystemExit(main())
