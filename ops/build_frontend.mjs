// Only explicitly selected browser configuration may enter a production bundle.
// The model/server environment file is never a build input.
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync, readdirSync, existsSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'

function fail(message) { throw new Error(message) }
const config = JSON.parse(readFileSync('/run/secrets/terra_frontend_config', 'utf8'))
const allowed = ['schema', 'map_provider', 'tianditu_key', 'api_base_url']
if (Object.keys(config).some(key => !allowed.includes(key)) || config.schema !== 1) fail('Invalid public build configuration')
if (!['tianditu', 'osm'].includes(config.map_provider)) fail('Explicit map provider required')
if (config.map_provider === 'tianditu' && (!/^[a-fA-F0-9]{32}$/.test(config.tianditu_key) || new Set(config.tianditu_key).size === 1)) fail('Browser map key required')
if (config.map_provider === 'osm' && config.tianditu_key) fail('OSM mode must not carry a map key')
if (!/^\/[A-Za-z0-9_/-]+$/.test(config.api_base_url) || config.api_base_url.includes('//')) fail('Same-origin API path required')
const canonical = JSON.stringify(Object.fromEntries(Object.entries(config).sort(([a], [b]) => a.localeCompare(b))))
const fingerprint = createHash('sha256').update(canonical).digest('hex')
if (fingerprint !== process.env.FRONTEND_CONFIG_SHA256) fail('Build configuration fingerprint mismatch')
for (const name of ['.env', '.env.local', '.env.production', '.env.production.local']) {
  if (existsSync(name)) fail('Implicit Vite environment files are forbidden in a release build')
}
const env = { ...process.env, VITE_API_BASE_URL: config.api_base_url, VITE_TIANDITU_KEY: config.tianditu_key }
// Fail rather than accidentally copying an unrelated public environment variable.
for (const key of Object.keys(env)) if (key.startsWith('VITE_') && !['VITE_API_BASE_URL', 'VITE_TIANDITU_KEY'].includes(key)) fail('Unexpected Vite environment variable')
const result = spawnSync('npm', ['run', 'build'], { env, stdio: 'inherit', timeout: 900_000 })
if (result.error || result.status !== 0) process.exit(result.status || 1)
const assets = readdirSync('dist/assets').filter(name => name.endsWith('.js'))
const scripts = assets.map(name => [name, readFileSync(join('dist/assets', name), 'utf8')])
const all = scripts.map(([, text]) => text).join('\n')
if (!all.includes('tile.openstreetmap.org')) fail('Fallback tile capability missing from final artifact')
if (config.map_provider === 'tianditu') {
  if (!all.includes('tianditu.gov.cn/DataServer') || !all.includes('api.tianditu.gov.cn/geocoder') || !all.includes(config.tianditu_key)) fail('Primary tiles/geocoder missing from final artifact')
} else if (all.includes('tianditu.gov.cn')) fail('Unexpected primary provider in OSM-only artifact')
const html = readFileSync('dist/index.html', 'utf8')
const entry = [...html.matchAll(/(?:src|href)=["']\/assets\/([^"']+\.js)["']/g)].map(match => match[1])
const selected = scripts.filter(([name, text]) => entry.includes(name) || /tile\.openstreetmap\.org|tianditu\.gov\.cn/.test(text))
if (!selected.length || selected.length > 8) fail('Unexpected critical asset set')
const descriptor = {
  schema: 1, fingerprint, map_provider: config.map_provider, api_base_url: config.api_base_url,
  capabilities: config.map_provider === 'tianditu' ? ['osm-fallback', 'tianditu-geocoder', 'tianditu-tiles'] : ['osm-fallback'],
  assets: Object.fromEntries(selected.map(([name, text]) => ['/assets/' + name, createHash('sha256').update(text).digest('hex')])),
}
mkdirSync('dist/.well-known', { recursive: true })
writeFileSync('dist/.well-known/terrafood-build.json', JSON.stringify(descriptor))
console.log('Public build contract verified; provider=' + config.map_provider)
