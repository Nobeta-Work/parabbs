import { describe, expect, it, vi } from 'vitest'
import { useInfiniteArticles } from './useInfiniteArticles'
import type { BlogPublicBriefVO, PageResult } from '@/types'
const article = (id: string) => ({ id } as BlogPublicBriefVO)
const page = (ids: string[], pages = 2) => ({ records: ids.map(article), pages, total: 3 } as PageResult<BlogPublicBriefVO>)
describe('infinite articles', () => {
  it('appends pages once, removes overlaps and stops at the last page', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(page(['1','2'])).mockResolvedValueOnce(page(['2','3']))
    const feed = useInfiniteArticles(fetch)
    await feed.reset({pageNum:1,pageSize:2})
    await Promise.all([feed.loadMore(), feed.loadMore()])
    await feed.loadMore()
    expect(feed.items.value.map(item=>item.id)).toEqual(['1','2','3'])
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(feed.hasMore.value).toBe(false)
  })
  it('ignores stale results when the search changes', async () => {
    let resolveOld!: (value: PageResult<BlogPublicBriefVO>) => void
    const fetch = vi.fn().mockImplementationOnce(()=>new Promise(resolve=>{resolveOld=resolve})).mockResolvedValueOnce(page(['new'],1))
    const feed = useInfiniteArticles(fetch)
    const old = feed.reset({pageNum:1,pageSize:2,keyword:'old'})
    await feed.reset({pageNum:1,pageSize:2,keyword:'new'})
    resolveOld(page(['old']))
    await old
    expect(feed.items.value.map(item=>item.id)).toEqual(['new'])
  })
  it('retries the failed page without losing previously loaded articles', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(page(['1'])).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(page(['2']))
    const feed = useInfiniteArticles(fetch)
    await feed.reset({pageNum:1,pageSize:1})
    await feed.loadMore()
    expect(feed.error.value).toBe(true)
    expect(feed.items.value.map(item=>item.id)).toEqual(['1'])
    await feed.loadMore()
    expect(fetch.mock.calls[2]![0].pageNum).toBe(2)
    expect(feed.items.value.map(item=>item.id)).toEqual(['1','2'])
    expect(feed.error.value).toBe(false)
  })
})
