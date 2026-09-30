import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { pinToolbar } from './toolbarPosition'

describe('native toolbar positioning', () => {
  let host: HTMLDivElement
  let toolbar: HTMLDivElement
  let top: number
  let bottom: number
  let left: number
  let width: number
  let toolbarHeight: number
  let dispose: () => void
  const frames = new Map<number, FrameRequestCallback>()
  let frameId = 0
  const flush = () => {
    const pending = [...frames.values()]
    frames.clear()
    pending.forEach(callback => callback(0))
  }
  beforeEach(() => {
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      frames.set(++frameId, callback)
      return frameId
    })
    vi.stubGlobal('cancelAnimationFrame', (id: number) => frames.delete(id))
    vi.stubGlobal('ResizeObserver', class { observe() {} disconnect() {} })
    document.body.innerHTML = '<header class="nav-header"></header><div class="scroller"><div class="vditor"><div class="vditor-toolbar"></div></div></div>'
    host = document.querySelector('.vditor')!
    toolbar = document.querySelector('.vditor-toolbar')!
    top = 200; bottom = 1200; left = 320; width = 700; toolbarHeight = 36
    vi.spyOn(host, 'getBoundingClientRect').mockImplementation(() => ({ top, bottom, left } as DOMRect))
    vi.spyOn(toolbar, 'getBoundingClientRect').mockImplementation(() => ({ height: toolbarHeight } as DOMRect))
    vi.spyOn(document.querySelector('.nav-header')!, 'getBoundingClientRect').mockReturnValue({ top: 0, bottom: 70 } as DOMRect)
    Object.defineProperty(host, 'clientWidth', { get: () => width })
    dispose = pinToolbar(host, toolbar)
    flush()
  })
  afterEach(() => {
    dispose()
    frames.clear()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    document.body.innerHTML = ''
  })
  function scroll() {
    document.querySelector('.scroller')!.dispatchEvent(new Event('scroll'))
    flush()
  }
  it('docks on a non-bubbling nested scroll, preserving toolbar space and horizontal alignment', () => {
    expect(toolbar.style.position).toBe('')
    top = -100
    scroll()
    expect(toolbar.style.position).toBe('fixed')
    expect(toolbar.style.top).toBe('70px')
    expect(toolbar.style.left).toBe('320px')
    expect(toolbar.style.width).toBe('700px')
    expect(host.style.paddingTop).toBe('36px')
    width = 350; left = 20; toolbarHeight = 108
    window.dispatchEvent(new Event('resize'))
    flush()
    expect(toolbar.style.width).toBe('350px')
    expect(toolbar.style.left).toBe('20px')
    expect(host.style.paddingTop).toBe('108px')
  })
  it('stops at the editor bottom and restores flow above the editor', () => {
    top = -100; bottom = 90
    scroll()
    expect(toolbar.style.top).toBe('54px')
    top = 200; bottom = 1200
    scroll()
    expect(toolbar.getAttribute('style')).toBeNull()
    expect(host.style.paddingTop).toBe('')
  })
  it('hands positioning back to fullscreen and releases captured listeners on cleanup', () => {
    top = -100
    scroll()
    host.classList.add('vditor--fullscreen')
    scroll()
    expect(toolbar.style.position).toBe('')
    expect(host.style.paddingTop).toBe('')
    host.classList.remove('vditor--fullscreen')
    scroll()
    expect(toolbar.style.position).toBe('fixed')
    dispose()
    scroll()
    expect(toolbar.style.position).toBe('')
    expect(frames.size).toBe(0)
  })
})
