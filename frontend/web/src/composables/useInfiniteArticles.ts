import { ref } from 'vue'
import type { BlogPageQuery, BlogPublicBriefVO, PageResult } from '@/types'

/** A reset invalidates older requests; failed pages remain retryable. */
export function useInfiniteArticles(fetchPage: (query: BlogPageQuery) => Promise<PageResult<BlogPublicBriefVO>>) {
  const items = ref<BlogPublicBriefVO[]>([])
  const loading = ref(false)
  const error = ref(false)
  const hasMore = ref(true)
  let generation = 0
  let page = 0
  let query: BlogPageQuery = { pageNum: 1, pageSize: 12 }
  async function loadMore() {
    if (loading.value || !hasMore.value) return
    const current = generation
    loading.value = true
    error.value = false
    try {
      const result = await fetchPage({ ...query, pageNum: page + 1 })
      if (current !== generation) return
      const known = new Set(items.value.map(item => String(item.id)))
      items.value.push(...result.records.filter(item => !known.has(String(item.id))))
      page += 1
      hasMore.value = page < result.pages && result.records.length > 0
    } catch {
      if (current === generation) error.value = true
    } finally {
      if (current === generation) loading.value = false
    }
  }
  function reset(next: BlogPageQuery) {
    generation += 1
    query = { ...next, tagIds: next.tagIds ? [...next.tagIds] : undefined }
    page = 0
    items.value = []
    loading.value = false
    error.value = false
    hasMore.value = true
    return loadMore()
  }
  function dispose() { generation += 1 }
  return { items, loading, error, hasMore, loadMore, reset, dispose }
}
