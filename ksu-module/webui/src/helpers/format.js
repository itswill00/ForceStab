// Pure parsing helpers for VideoModeFix status output.
// No Vue reactivity here — safe to unit test and reuse.

export function parseStatus(text) {
  const out = { module: '?', hookApk: '?', lsposed: '?', conf: [] }
  if (!text) return out
  for (const line of String(text).split('\n')) {
    const t = line.trim()
    if (t.startsWith('module=')) out.module = t.slice(7)
    else if (t.startsWith('hook-apk=')) out.hookApk = t.slice(9)
    else if (t.startsWith('lsposed=')) out.lsposed = t.slice(8)
    else if (t && !t.startsWith('--') && t.includes('=')) out.conf.push(t)
  }
  return out
}

export function shortHookLog(text, max = 12) {
  if (!text) return []
  return String(text)
    .split('\n')
    .map(l => l.trim())
    .filter(l => l.length > 0)
    .slice(-max)
}
