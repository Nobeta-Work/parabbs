/** Vditor's native renderMenu hook runs after code rendering and before copy controls mount. */
export function labelCodeLanguage(code: HTMLElement): void {
  const pre = code.parentElement
  if (!pre || pre.tagName !== 'PRE') return
  const language = Array.from(code.classList).find(name => name.startsWith('language-'))?.slice(9)
  pre.setAttribute('data-code-language', language || 'plaintext')
}
