<script setup lang="ts">
import { NAvatar, NIcon } from 'naive-ui'
import { HeartOutline, ChatbubbleOutline, ArrowForwardOutline } from '@vicons/ionicons5'
import type { BlogPublicBriefVO } from '@/types'
import { resolveAvatarUrl } from '@/utils/avatar'
import { DateUtils } from '@/types/date'
import ContentImage from './ContentImage.vue'
defineProps<{ blog: BlogPublicBriefVO; layout?: 'archive' }>()
</script>
<template>
  <article class="article-card" :class="{ 'archive-card': layout === 'archive' }">
    <router-link v-if="blog.coverUrl" :to="'/blog/' + blog.id" class="cover-link" tabindex="-1" aria-hidden="true">
      <ContentImage :src="blog.coverUrl" alt="" class="article-cover" />
    </router-link>
    <div class="article-body">
    <div class="article-heading">
      <h2><router-link :to="'/blog/' + blog.id">{{ blog.title }}</router-link></h2>
    </div>
    <p v-if="blog.summary" class="summary">{{ blog.summary }}</p>
    <footer>
      <div class="meta">
        <router-link class="author" :to="'/' + blog.author.id"><n-avatar round :size="24" :src="resolveAvatarUrl(blog.author.avatarUrl)" /><span>{{ blog.author.nickname }}</span></router-link>
        <time>{{ DateUtils.isoToDateOnly(blog.createTime) }}</time>
      </div>
      <div v-if="blog.tags?.length" class="tags"><span v-for="tag in blog.tags.slice(0,3)" :key="tag.id" class="tag">{{ tag.name }}</span></div>
      <div class="metrics"><span aria-label="评论数"><n-icon :component="ChatbubbleOutline" />{{ blog.commentsCount || 0 }}</span><span aria-label="点赞数"><n-icon :component="HeartOutline" />{{ blog.likeCount || 0 }}</span></div>
    </footer>
    </div>
    <router-link v-if="layout === 'archive'" :to="'/blog/' + blog.id" class="archive-open" :aria-label="'阅读：' + blog.title">
      <n-icon :component="ArrowForwardOutline" />
    </router-link>
  </article>
</template>
<style scoped>
.article-card { padding: 22px 24px; min-width: 0; background: var(--bg-secondary); border: 1px solid transparent; border-radius: 16px; transition: border-color 180ms, background-color 180ms; }
.article-card:hover { border-color: var(--line-color); }
.article-body { min-width: 0; }
.archive-card { position: relative; isolation: isolate; min-height: 144px; display: grid; align-items: center; }
.archive-card .article-body { grid-area: 1 / 1; padding-right: 64px; }
.archive-card:has(.article-cover) { grid-template-columns: minmax(0, 1fr) 36%; }
.archive-card:has(.article-cover) .article-body { padding-right: 16px; }
.archive-card .cover-link { grid-column: 2; grid-row: 1; align-self: stretch; position: relative; min-height: 144px; border-radius: 0; mask-image: linear-gradient(to right, transparent, #000 45%); }
.archive-card .article-cover { position: absolute; inset: 0; height: 100%; aspect-ratio: auto; border-radius: 0; margin: 0; }
.archive-card footer { justify-content: flex-start; gap: 10px 14px; }
.archive-card .tags { flex-shrink: 1; }
.archive-card .tag { padding: 2px 7px; font-size: 10px; }
.archive-card .metrics { gap: 10px; }
.archive-card .metrics .n-icon { font-size: 16px; }
.archive-open { position: absolute; right: 12px; top: 50%; width: 42px; height: 42px; display: grid; place-items: center; border: 1px solid var(--line-color); border-radius: 50%; background: var(--bg-primary); color: var(--text-primary); box-shadow: 0 8px 24px rgb(0 0 0 / .08); transform: translateY(-50%); transition: transform 250ms, border-color 250ms, color 250ms; }
.archive-card:hover .archive-open, .archive-open:focus-visible { transform: translate(4px, -50%); color: var(--accent-color); border-color: var(--accent-color); }
.archive-open .n-icon { font-size: 20px; }
@media(max-width:640px) {
  .archive-card:has(.article-cover) { grid-template-columns: minmax(0, 1fr) 28%; }
  .archive-card .cover-link { min-height: 156px; }
  .archive-open { right: 4px; width: 34px; height: 34px; }
  .archive-card .article-body { padding-right: 46px; }
  .archive-card:has(.article-cover) .article-body { padding-right: 0; }
}
@media(prefers-reduced-motion:reduce) { .archive-open { transition: none; } .archive-card:hover .archive-open { transform: translateY(-50%); } }
.cover-link { display: block; overflow: hidden; border-radius: 12px; }
.cover-link:not(:has(.article-cover)) { display: none; }
.article-cover { margin-bottom: 20px; border-radius: 12px; transition: filter 250ms; }
.article-card:hover .article-cover { filter: saturate(1.08); }
@media(prefers-reduced-motion:reduce) { .article-cover { transition: none; } }
a { color: inherit; text-decoration: none; }
a:hover { color: var(--accent-color); }
.article-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 20px; }
h2 { font-size: 19px; line-height: 1.5; margin: 0 0 8px; font-weight: 600; overflow-wrap: anywhere; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; flex-shrink: 0; }
.tag { font-size: 11px; padding: 3px 10px; border-radius: 14px; color: var(--text-secondary); background: color-mix(in srgb, var(--text-primary) 5%, transparent); }
.summary { color: var(--text-secondary); font-size: 14px; line-height: 1.65; margin: 0 0 12px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
footer, .meta, .metrics, .metrics span, .author { display: flex; align-items: center; gap: 10px; }
footer { justify-content: space-between; flex-wrap: wrap; gap: 14px; }
.meta { flex-wrap: wrap; font-size: 12px; color: var(--text-tertiary); }
.author { font-size: 12px; gap: 7px; }
.metrics { gap: 18px; color: var(--text-secondary); font-size: 12px; }
.metrics span { gap: 6px; }
.metrics .n-icon { font-size: 18px; }
@media(max-width:640px) { .article-card { padding: 18px; } .article-heading { flex-direction: column; gap: 0; } h2 { font-size: 18px; } .tags { margin-bottom: 10px; } }
</style>
