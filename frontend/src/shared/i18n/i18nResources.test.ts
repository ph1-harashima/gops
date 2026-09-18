import { describe, expect, it } from 'vitest'

// G-OPS i18n完成 #14: guards against exactly the regression this Phase
// found and fixed (129 keys silently missing from en/*.json, 2 namespaces
// - auth/skuDetail - missing entirely, both quietly papered over by
// fallbackLng: 'ja' until a real Language Switcher existed to expose them).
// Uses Vite's import.meta.glob (not Node's fs/path) so this test stays
// browser-buildable like every other file under src/ - no tsconfig split
// needed just to add Node types for one test file.
const modules = import.meta.glob('./locales/*/*.json', { eager: true }) as Record<string, { default: Record<string, unknown> }>

function flatten(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([key, value]) => {
    const full = prefix ? `${prefix}.${key}` : key
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
      return flatten(value as Record<string, unknown>, full)
    }
    return [full]
  })
}

// path looks like './locales/ja/candidates.json'
const byLangAndFile = new Map<string, Map<string, Record<string, unknown>>>()
for (const [modPath, mod] of Object.entries(modules)) {
  const match = /\.\/locales\/([a-z]+)\/([a-zA-Z]+\.json)$/.exec(modPath)
  if (!match) continue
  const [, lang, file] = match
  if (!byLangAndFile.has(lang)) byLangAndFile.set(lang, new Map())
  byLangAndFile.get(lang)!.set(file, mod.default)
}

const jaNamespaces = byLangAndFile.get('ja')!
const enNamespaces = byLangAndFile.get('en')!
const jaFiles = [...jaNamespaces.keys()].sort()
const enFiles = [...enNamespaces.keys()].sort()

describe('i18n resource parity (ja <-> en)', () => {
  it('has the exact same set of namespace files in both languages', () => {
    expect(enFiles).toEqual(jaFiles)
  })

  it.each(jaFiles)('%s has every ja key present in en (no silent fallbackLng gaps)', (file) => {
    const jaKeys = flatten(jaNamespaces.get(file)!)
    const enKeys = new Set(flatten(enNamespaces.get(file)!))
    const missing = jaKeys.filter((k) => !enKeys.has(k))
    expect(missing).toEqual([])
  })

  it.each(jaFiles)('%s has no key present in en but absent from ja (stale/orphaned translation)', (file) => {
    const enKeys = flatten(enNamespaces.get(file)!)
    const jaKeys = new Set(flatten(jaNamespaces.get(file)!))
    const orphaned = enKeys.filter((k) => !jaKeys.has(k))
    expect(orphaned).toEqual([])
  })
})
