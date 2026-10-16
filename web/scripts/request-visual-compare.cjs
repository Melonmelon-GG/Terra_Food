const fs = require('node:fs')
const assert = require('node:assert/strict')
const { PNG } = require('pngjs')
const root = process.env.REQUEST_EVIDENCE || '/evidence'
const result = []
for (const [name, image] of [['home-390', 'home-390'], ['home-1366', 'home-1366'], ['detail-identity-and-draft', 'detail']]) {
  const before = JSON.parse(fs.readFileSync(`${root}/browser-baseline/${name}.json`)).observations.layout
  const after = JSON.parse(fs.readFileSync(`${root}/browser-fixed/${name}.json`)).observations.layout
  assert.deepEqual(after, before, name + ': layout, colors and fonts')
  const a = PNG.sync.read(fs.readFileSync(`${root}/browser-baseline/${image}.png`))
  const b = PNG.sync.read(fs.readFileSync(`${root}/browser-fixed/${image}.png`))
  assert.equal(b.width, a.width); assert.equal(b.height, a.height)
  let changed = 0
  for (let i = 0; i < a.data.length; i += 4) {
    if ([0, 1, 2, 3].some(c => Math.abs(a.data[i + c] - b.data[i + c]) > 8)) changed++
  }
  const ratio = changed / (a.width * a.height)
  result.push({ name, layoutIdentical: true, changedPixels: changed, totalPixels: a.width * a.height, ratio })
  assert(ratio <= .01, name + ': screenshot changed by more than 1%')
}
fs.writeFileSync(`${root}/visual.json`, JSON.stringify(result, null, 2))
console.log(JSON.stringify(result))
