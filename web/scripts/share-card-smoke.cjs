// The share card is now a dish-only ticket. Keep the public test command stable.
process.argv.push('--share-only')
require('./archive-redesign-smoke.cjs')
