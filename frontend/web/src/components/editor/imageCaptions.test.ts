import { describe, expect, it } from 'vitest'
import { observeImageCaptions } from './imageCaptions'

describe('alt image captions', () => {
  it('updates standalone alt text without adding caption text to the document', async () => {
    const host = document.createElement('div')
    host.innerHTML = '<p><img src="image.png" alt="图一"></p><p>正文<img alt="行内图"></p><p><span class="vditor-image" data-image-caption="原生标题"><img alt="替代文本" title="原生标题"></span></p>'
    const dispose = observeImageCaptions(host)
    expect(host.querySelector('p')?.dataset.altCaption).toBe('图一')
    expect(host.querySelectorAll('[data-alt-caption]')).toHaveLength(1)
    expect(host.textContent).toBe('正文')
    host.querySelector('img')!.alt = '图二'
    await Promise.resolve()
    expect(host.querySelector('p')?.dataset.altCaption).toBe('图二')
    host.querySelector('img')!.alt = ''
    await Promise.resolve()
    expect(host.querySelectorAll('[data-alt-caption]')).toHaveLength(0)
    dispose()
  })
  it('ignores native IR syntax markers when recognizing a standalone image', () => {
    const host = document.createElement('div')
    host.innerHTML = '<p><span data-type="img"><span class="vditor-ir__marker">![说明](image.png)</span><img alt="说明"></span></p>'
    const dispose = observeImageCaptions(host)
    expect(host.querySelector('p')?.dataset.altCaption).toBe('说明')
    expect(host.textContent).toBe('![说明](image.png)')
    dispose()
  })
})
