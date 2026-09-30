/** Native title captions take precedence; alt captions are presentation only. */
export function observeImageCaptions(host: HTMLElement) {
  const update = () => {
    host.querySelectorAll<HTMLElement>('p').forEach(paragraph => {
      const image = paragraph.querySelector('img')
      if (!image) { paragraph.removeAttribute('data-alt-caption'); return }
      const prose = paragraph.cloneNode(true) as HTMLElement
      prose.querySelectorAll('.vditor-ir__marker').forEach(marker => marker.remove())
      const standalone = image && !prose.textContent?.replace(/\u200b/g, '').trim() && paragraph.querySelectorAll('img').length === 1
      const caption = standalone && !image.closest('.vditor-image') && !image.title.trim() ? image.alt.trim() : ''
      if (caption) paragraph.dataset.altCaption = caption
      else paragraph.removeAttribute('data-alt-caption')
    })
  }
  const observer = new MutationObserver(update)
  observer.observe(host, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ['alt', 'title'] })
  update()
  return () => observer.disconnect()
}
