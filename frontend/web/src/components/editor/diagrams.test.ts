import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mindmapSize, observeDiagrams } from './diagrams'

describe('mindmap layout', () => {
  const measure = (text: string) => text.length * 12
  it('reserves room for a long root and leaf labels at every depth', () => {
    const tree = { name: 'a'.repeat(30), children: [{ name: 'b'.repeat(40), children: [{ name: 'leaf' }] }] }
    const size = mindmapSize(tree, 320, measure)
    expect(size.left).toBeGreaterThan(measure(tree.children[0]!.name))
    expect((size.width - size.left - size.right) / 2).toBeGreaterThan(measure(tree.children[0]!.name))
  })
  it('grows vertically with leaves instead of compressing branches into a fixed canvas', () => {
    const size = mindmapSize({ name: 'root', children: Array.from({ length: 40 }, () => ({ name: 'leaf' })) }, 800, measure)
    expect(size.height).toBe(1504)
    expect(size.width).toBeGreaterThanOrEqual(800)
  })
})


describe('diagram theme lifecycle', () => {
  const frames = new Map<number, FrameRequestCallback>()
  let nextFrame = 0
  const flush = () => { const callbacks = [...frames.values()]; frames.clear(); callbacks.forEach(callback => callback(0)) }
  let controller: ReturnType<typeof observeDiagrams>
  beforeEach(() => {
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => { frames.set(++nextFrame, callback); return nextFrame })
    vi.stubGlobal('cancelAnimationFrame', (id: number) => frames.delete(id))
    vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    document.body.innerHTML = '<div id="host"><div class="language-mermaid">graph LR; A-->B</div></div>'
  })
  afterEach(() => { controller?.dispose(); frames.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); document.body.innerHTML = '' })
  it('redraws processed diagrams from preserved source in both theme directions', async () => {
    const initialize = vi.fn()
    const render = vi.fn(async (id: string, _source: string) => ({ svg: `<svg id="${id}"></svg>` }))
    vi.stubGlobal('mermaid', { initialize, render })
    const host = document.querySelector<HTMLElement>('#host')!
    const element = host.firstElementChild as HTMLElement
    let dark = false
    controller = observeDiagrams(host, () => dark)
    element.innerHTML = '<svg id="native"></svg>'
    element.dataset.processed = 'true'
    flush()
    await vi.waitFor(() => expect(render).toHaveBeenCalledTimes(1))
    dark = true; controller.refresh(); flush()
    await vi.waitFor(() => expect(initialize).toHaveBeenLastCalledWith(expect.objectContaining({ theme: 'dark' })))
    dark = false; controller.refresh(); flush()
    await vi.waitFor(() => expect(initialize).toHaveBeenLastCalledWith(expect.objectContaining({ theme: 'default' })))
    expect(render.mock.calls.every(call => call[1] === 'graph LR; A-->B')).toBe(true)
  })
  it('does not replace output after disposal during an asynchronous render', async () => {
    let finish!: (value: { svg: string }) => void
    const render = vi.fn(() => new Promise<{ svg: string }>(resolve => { finish = resolve }))
    vi.stubGlobal('mermaid', { initialize: vi.fn(), render })
    const host = document.querySelector<HTMLElement>('#host')!
    const element = host.firstElementChild as HTMLElement
    controller = observeDiagrams(host, () => true)
    element.innerHTML = '<svg id="native"></svg>'; element.dataset.processed = 'true'
    flush()
    await vi.waitFor(() => expect(render).toHaveBeenCalledOnce())
    controller.dispose(); finish({ svg: '<svg id="late"></svg>' })
    await Promise.resolve(); await Promise.resolve()
    expect(element.firstElementChild?.id).toBe('native')
  })
  it('cleans rejected render containers while keeping an error inside its own block', async () => {
    const render = vi.fn(async (id: string) => {
      const container = document.createElement('div')
      container.id = `d${id}`
      container.innerHTML = '<svg><text class="error-text">Syntax error</text></svg>'
      document.body.appendChild(container)
      throw new Error('Invalid source')
    })
    vi.stubGlobal('mermaid', { initialize: vi.fn(), render })
    const host = document.querySelector<HTMLElement>('#host')!
    const element = host.firstElementChild as HTMLElement
    controller = observeDiagrams(host, () => true)
    element.innerHTML = '<svg id="native"></svg>'; element.dataset.processed = 'true'
    flush()
    await vi.waitFor(() => expect(render).toHaveBeenCalledOnce())
    await Promise.resolve(); await Promise.resolve()
    expect(document.querySelector('[id^="dpara-diagram-"]')).toBeNull()
    const orphan = document.createElement('div')
    orphan.id = 'dmermaid-failed'
    orphan.innerHTML = '<svg><text class="error-text">Syntax error</text></svg>'
    document.body.appendChild(orphan)
    element.innerHTML = '<svg><text class="error-text">Syntax error</text></svg>'
    await Promise.resolve(); flush()
    expect(orphan.isConnected).toBe(false)
    expect(element.querySelector('.error-text')).not.toBeNull()
  })
})
