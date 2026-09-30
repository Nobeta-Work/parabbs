type Tree = { name?: string; children?: Tree[] }
type Chart = { resize: (size: { width: number; height: number }) => void; setOption: (option: object) => void }
type DiagramWindow = Window & {
  mermaid?: { initialize: (options: object) => void; render: (id: string, source: string) => Promise<{ svg: string; bindFunctions?: (element: Element) => void }> }
  echarts?: { getInstanceByDom: (element: HTMLElement) => Chart | undefined }
}
let mermaidQueue = Promise.resolve()
let diagramId = 0

// Leaves determine vertical spacing; label widths and depth determine horizontal spacing.
export function mindmapSize(tree: Tree, availableWidth: number, measure: (text: string) => number) {
  let leaves = 0, depth = 1, labelWidth = 0
  const visit = (node: Tree, level: number) => {
    depth = Math.max(depth, level)
    labelWidth = Math.max(labelWidth, ...String(node.name ?? '').split('\n').map(measure))
    if (node.children?.length) node.children.forEach(child => visit(child, level + 1))
    else leaves++
  }
  visit(tree, 1)
  const margin = Math.ceil(labelWidth + 36)
  return { width: Math.max(availableWidth, depth * (labelWidth + 60) + 48), height: Math.max(620, leaves * 36 + 64), left: margin, right: 32 }
}

/** Enhance Vditor's rendered output without replacing editable code or resetting undo history. */
export function observeDiagrams(host: HTMLElement, isDark: () => boolean) {
  const runtime = window as DiagramWindow
  const sources = new WeakMap<HTMLElement, string>()
  const rendered = new WeakMap<HTMLElement, { svg: Element; dark: boolean }>()
  const pending = new WeakSet<HTMLElement>()
  const chartStates = new Map<HTMLElement, { chart: Chart; key: string }>()
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')
  if (context) context.font = '12px sans-serif'
  let active = true, frame = 0
  let cleanupFrame = 0
  // Mermaid measures in body-level containers. Rejected renders can leave these behind.
  const cleanupErrors = () => {
    cleanupFrame = 0
    document.body.querySelectorAll<HTMLElement>('div[id^="dmermaid"], div[id^="dpara-diagram-"]').forEach(element => {
      if (!host.contains(element) && element.querySelector('.error-icon, .error-text')) element.remove()
    })
  }
  const errorObserver = new MutationObserver(() => {
    if (!cleanupFrame) cleanupFrame = requestAnimationFrame(cleanupErrors)
  })
  errorObserver.observe(document.body, { subtree: true, childList: true })
  const schedule = () => { if (active && !frame) frame = requestAnimationFrame(update) }
  const capture = () => {
    host.querySelectorAll<HTMLElement>('.language-mermaid').forEach(element => {
      if (!element.querySelector('svg') && element.dataset.processed !== 'true' && element.textContent?.trim()) sources.set(element, element.textContent)
    })
    schedule()
  }
  const resize = new ResizeObserver(schedule)
  resize.observe(host)
  const mutations = new MutationObserver(capture)
  mutations.observe(host, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ['data-processed'] })

  function update() {
    frame = 0
    if (!active) return
    const dark = isDark()
    host.querySelectorAll<HTMLElement>('.language-mermaid').forEach(element => {
      const svg = element.querySelector('svg')
      if (!svg || element.dataset.processed !== 'true' || pending.has(element)) return
      if (svg.querySelector('.error-icon, .error-text')) return
      const previous = rendered.get(element)
      if (previous?.svg === svg && previous.dark === dark) return
      // Editable modes keep the original code next to the rendered block.
      const source = sources.get(element) ?? element.closest('[data-type="code-block"]')?.querySelector('pre code')?.textContent
      if (!source || !runtime.mermaid) return
      pending.add(element)
      mermaidQueue = mermaidQueue.then(async () => {
        if (!active || !host.contains(element) || element.querySelector('svg') !== svg) return
        const currentDark = isDark()
        runtime.mermaid!.initialize({
          startOnLoad: false, securityLevel: 'loose', theme: currentDark ? 'dark' : 'default',
          fontFamily: 'sans-serif', altFontFamily: 'sans-serif',
          flowchart: { htmlLabels: true, useMaxWidth: true },
          sequence: { useMaxWidth: true, diagramMarginX: 8, diagramMarginY: 8, boxMargin: 8, showSequenceNumbers: true },
          gantt: { leftPadding: 75, rightPadding: 20 },
        })
        const id = `para-diagram-${++diagramId}`
        let result: { svg: string; bindFunctions?: (element: Element) => void }
        try { result = await runtime.mermaid!.render(id, source) }
        finally { document.getElementById(`d${id}`)?.remove() }
        if (!active || !host.contains(element) || element.querySelector('svg') !== svg) return
        element.innerHTML = result.svg
        result.bindFunctions?.(element)
        const output = element.querySelector('svg')
        if (output) rendered.set(element, { svg: output, dark: currentDark })
      }).catch(() => {
        // Keep Vditor's existing result if the renderer rejects the source.
        rendered.set(element, { svg, dark })
      }).finally(() => { pending.delete(element); schedule() })
    })
    // Preserve the native fitted coordinate system when the sidebar or viewport changes width.
    host.querySelectorAll<SVGSVGElement>('.language-markmap > svg:not([viewBox])').forEach(svg => {
      if (!svg.querySelector('.markmap-node')) return
      const bounds = svg.getBoundingClientRect()
      if (bounds.width && bounds.height) svg.setAttribute('viewBox', `0 0 ${bounds.width} ${bounds.height}`)
    })
    for (const element of chartStates.keys()) {
      if (!host.contains(element)) { resize.unobserve(element.parentElement ?? element); chartStates.delete(element) }
    }
    host.querySelectorAll<HTMLElement>('.language-mindmap[data-processed="true"]').forEach(element => {
      const chart = runtime.echarts?.getInstanceByDom(element)
      let parent = element.parentElement
      if (!chart || !parent || !parent.clientWidth) return
      if (parent.classList.contains('vditor-reset')) {
        const viewport = document.createElement('div')
        viewport.className = 'diagram-viewport'
        viewport.tabIndex = 0
        viewport.setAttribute('role', 'region')
        viewport.setAttribute('aria-label', '思维导图，可横向滚动')
        parent.insertBefore(viewport, element)
        viewport.appendChild(element)
        parent = viewport
      }
      try {
        const size = mindmapSize(JSON.parse(decodeURIComponent(element.dataset.code ?? '')), parent.clientWidth, text => context?.measureText(text).width ?? text.length * 12)
        const key = `${size.width}:${size.height}:${dark}`
        const previous = chartStates.get(element)
        if (previous?.chart === chart && previous.key === key) return
        if (!previous) resize.observe(parent)
        chartStates.set(element, { chart, key })
        element.style.width = `${size.width}px`
        element.style.height = `${size.height}px`
        chart.resize({ width: size.width, height: size.height })
        const styles = getComputedStyle(host)
        chart.setOption({ backgroundColor: 'transparent', series: [{
          left: size.left, right: size.right, top: 32, bottom: 32,
          itemStyle: { color: styles.getPropertyValue('--editor-quote-color').trim() },
          label: { color: styles.getPropertyValue('--text-primary').trim(), backgroundColor: styles.getPropertyValue('--bg-secondary').trim(), borderColor: styles.getPropertyValue('--line-color').trim() },
          lineStyle: { color: styles.getPropertyValue('--text-tertiary').trim() },
        }] })
      } catch { /* Invalid JSON remains Vditor's responsibility. */ }
    })
  }
  capture()
  return {
    refresh: schedule,
    dispose() { active = false; cancelAnimationFrame(frame); cancelAnimationFrame(cleanupFrame); cleanupErrors(); errorObserver.disconnect(); mutations.disconnect(); resize.disconnect(); chartStates.clear() },
  }
}
