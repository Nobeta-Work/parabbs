const pairs: Record<string, string> = { '(': ')', '[': ']', '{': '}', '"': '"', "'": "'", '`': '`', '（': '）', '【': '】', '“': '”', '‘': '’' }

/** Keep browser insertion and Vditor's input/undo handling in charge of document changes. */
export function installSymbolPairs(host: HTMLElement) {
  const onKeydown = (event: KeyboardEvent) => {
    if (event.defaultPrevented || event.isComposing || event.ctrlKey || event.metaKey || event.altKey) return
    const target = event.target instanceof HTMLElement ? event.target : null
    if (target instanceof HTMLTextAreaElement && target.classList.contains('vditor-sv')) {
      const start = target.selectionStart, end = target.selectionEnd
      const left = target.value[start - 1] ?? '', right = target.value[end] ?? ''
      if (start === end && Object.values(pairs).includes(event.key) && right === event.key) {
        event.preventDefault(); event.stopPropagation(); target.setSelectionRange(end + 1, end + 1); return
      }
      if (start === end && event.key === 'Backspace' && left && pairs[left] === right) {
        event.preventDefault(); event.stopPropagation(); target.setSelectionRange(start - 1, end + 1); document.execCommand('delete'); return
      }
      const close = pairs[event.key]
      if (!close || left === '\\' || (start === end && ((/[\w]/.test(left) && ['"', "'", '`'].includes(event.key)) || (right && !/[\s)\]}，。；：！？]/.test(right))))) return
      event.preventDefault(); event.stopPropagation()
      const selected = target.value.slice(start, end)
      const position = () => target.setSelectionRange(start + 1, start + 1 + selected.length)
      target.addEventListener('input', position, { capture: true, once: true })
      document.execCommand('insertText', false, event.key + selected + close)
      target.removeEventListener('input', position, true)
      position()
      return
    }
    const root = target?.closest<HTMLElement>('.vditor-reset[contenteditable="true"]')
    const selection = window.getSelection()
    if (!root || !host.contains(root) || !selection?.rangeCount || typeof selection.modify !== 'function') return
    const range = selection.getRangeAt(0)
    if (!root.contains(range.startContainer) || !root.contains(range.endContainer)) return
    const anchor = range.startContainer instanceof Element ? range.startContainer : range.startContainer.parentElement
    const block = anchor?.closest('p,h1,h2,h3,h4,h5,h6,code,td,th,li') ?? root
    if (!block.contains(range.endContainer)) return
    const before = range.cloneRange(); before.selectNodeContents(block); before.setEnd(range.startContainer, range.startOffset)
    const after = range.cloneRange(); after.selectNodeContents(block); after.setStart(range.endContainer, range.endOffset)
    const left = before.toString().slice(-1), right = after.toString()[0] ?? ''
    const move = (direction: 'forward' | 'backward', alter: 'move' | 'extend' = 'move') => selection.modify(alter, direction, 'character')
    if (range.collapsed && Object.values(pairs).includes(event.key) && right === event.key) {
      event.preventDefault(); event.stopPropagation(); move('forward'); return
    }
    if (range.collapsed && event.key === 'Backspace' && left && pairs[left] === right) {
      event.preventDefault(); event.stopPropagation(); move('backward'); move('forward', 'extend'); move('forward', 'extend')
      document.execCommand('delete'); return
    }
    const close = pairs[event.key]
    if (!close || left === '\\') return
    // Apostrophes in words and quotes in front of text should remain ordinary input.
    if (range.collapsed && ((/[\w]/.test(left) && ['"', "'", '`'].includes(event.key)) || (right && !/[\s)\]}，。；：！？]/.test(right)))) return
    const selected = selection.toString()
    event.preventDefault(); event.stopPropagation()
    // Move the caret before Vditor captures it during its native input handler.
    let positioned = false
    const position = () => {
      positioned = true
      move('backward')
      for (const _character of selected) move('backward', 'extend')
    }
    root.addEventListener('input', position, { capture: true, once: true })
    document.execCommand('insertText', false, event.key + selected + close)
    root.removeEventListener('input', position, true)
    if (!positioned) position()
  }
  host.addEventListener('keydown', onKeydown, true)
  return () => host.removeEventListener('keydown', onKeydown, true)
}
