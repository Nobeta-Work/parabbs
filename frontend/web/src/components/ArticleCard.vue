<script setup lang="ts">
import { NAvatar, NIcon } from 'naive-ui'
import { HeartOutline, ChatbubbleOutline } from '@vicons/ionicons5'
import type { BlogPublicBriefVO } from '@/types'
import { resolveAvatarUrl } from '@/utils/avatar'
import { DateUtils } from '@/types/date'
defineProps<{ blog: BlogPublicBriefVO }>()
</script>
<template>
  <article class="article-card">
    <div class="article-heading">
      <h2><router-link :to="'/blog/' + blog.id">{{ blog.title }}</router-link></h2>
      <div v-if="blog.tags?.length" class="tags"><span v-for="tag in blog.tags.slice(0,3)" :key="tag.id" class="tag">{{ tag.name }}</span></div>
    </div>
    <p v-if="blog.summary" class="summary">{{ blog.summary }}</p>
    <footer>
      <div class="meta">
        <router-link class="author" :to="'/' + blog.author.id"><n-avatar round :size="24" :src="resolveAvatarUrl(blog.author.avatar)" /><span>{{ blog.author.nickname }}</span></router-link>
        <time>{{ DateUtils.isoToDateOnly(blog.createTime) }}</time>
      </div>
      <div class="metrics"><span aria-label="评论数"><n-icon :component="ChatbubbleOutline" />{{ blog.commentsCount || 0 }}</span><span aria-label="点赞数"><n-icon :component="HeartOutline" />{{ blog.likeCount || 0 }}</span></div>
    </footer>
  </article>
</template>
<style scoped>
.article-card { padding: 22px 24px; min-width: 0; background: var(--bg-secondary); border: 1px solid transparent; border-radius: 16px; transition: border-color 180ms, background-color 180ms; }
.article-card:hover { border-color: var(--line-color); }
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
