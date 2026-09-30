/** Keep the native toolbar aligned with an editor inside nested layout scrollers. */
export function pinToolbar(host: HTMLElement, toolbar: HTMLElement): () => void {
  const navigation = document.querySelector<HTMLElement>('.nav-header')
  const originalStyle = toolbar.getAttribute('style')
  const originalPadding = host.style.paddingTop
  let frame = 0
  let disposed = false

  function restore() {
    if (originalStyle === null) toolbar.removeAttribute('style')
    else toolbar.setAttribute('style', originalStyle)
    host.style.paddingTop = originalPadding
  }

  function update() {
    frame = 0
    if (disposed) return
    if (host.classList.contains('vditor--fullscreen')) {
      restore()
      return
    }
    const bounds = host.getBoundingClientRect()
    const nav = navigation?.getBoundingClientRect()
    const top = nav && nav.top <= 0 && nav.bottom > 0 ? nav.bottom : 0
    const height = toolbar.getBoundingClientRect().height
    if (bounds.top < top && bounds.bottom > top) {
      host.style.paddingTop = height + 'px'
      Object.assign(toolbar.style, {
        position: 'fixed',
        top: Math.min(top, bounds.bottom - height) + 'px',
        left: bounds.left + host.clientLeft + 'px',
        width: host.clientWidth + 'px',
      })
    } else {
      restore()
    }
  }

  function schedule() {
    if (!disposed && !frame) frame = requestAnimationFrame(update)
  }
  const resize = new ResizeObserver(schedule)
  resize.observe(host)
  resize.observe(toolbar)
  if (navigation) resize.observe(navigation)
  const mode = new MutationObserver(schedule)
  mode.observe(host, { attributes: true, attributeFilter: ['class'] })
  // Scroll does not bubble; capture also observes Naive UI's inner scrollers.
  window.addEventListener('scroll', schedule, true)
  window.addEventListener('resize', schedule)
  window.visualViewport?.addEventListener('resize', schedule)
  schedule()
  return () => {
    disposed = true
    cancelAnimationFrame(frame)
    resize.disconnect()
    mode.disconnect()
    window.removeEventListener('scroll', schedule, true)
    window.removeEventListener('resize', schedule)
    window.visualViewport?.removeEventListener('resize', schedule)
    restore()
  }
}
