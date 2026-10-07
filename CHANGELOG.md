# Changelog

该文档记录 Para BBS 过往版本与未来的不定计划，格式遵循 [Keey a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 。

## [Expect]

- [Add] Langchain Agent —— 博客编辑辅助 AI Copilot (Agent)。
- 长文本博客切片存储。
- [Add] 补充 MCP Server

## [Unreleased]

## [0.5.2] - 2026-10-7

- 新增 SSO 服务

## [0.5.0] - 2026-08-30

### Added

- 新增博客评论与两级回复功能，支持评论创建、查询、删除及评论点赞。
- 新增 Elasticsearch 博客索引，仅索引公开博客，支持按标题、摘要、正文、作者昵称和标签进行中文全文检索。
- 新增 RabbitMQ 领域事件链路，用于异步更新 Elasticsearch 和持久化博客、评论点赞。
- 新增 MySQL Outbox/Inbox 与 Redis Outbox，实现消息可靠发布、幂等消费和乱序保护。
- 新增消息发布与消费重试、死信队列、Single Active Consumer 及 Publisher Confirm。
- 新增点赞数、评论数和博客搜索索引的定时对账任务，以及已发布 Outbox 事件清理任务。
- 新增 RabbitMQ、Elasticsearch 和 Kibana 的 Docker Compose 编排。

### Changed

- 博客公开列表优先使用 Elasticsearch 查询，查询异常时降级到 MySQL。
- 博客创建、更新、发布状态变更、删除及评论数量变化改为通过 Outbox 异步同步搜索索引。
- 博客与评论点赞改为 Redis Set 配合 Lua 原子切换状态，再通过 RabbitMQ 异步落库。
- 博客表新增 `comments_count` 字段，并新增 `comment`、`like_comment`、`outbox_event` 和 `inbox_event` 表。
- 编辑器工具栏改为随页面滚动固定在顶部导航下方。
- 代码块改用默认等宽字体，并统一编辑层与高亮层的字号和行高。

### Fixed

- 修复 Elasticsearch 文档字段映射不一致导致的查询转换异常。
- 修复 Elasticsearch 索引首次创建后未同步已有公开博客的问题。
- 修复编辑器代码块字体指标不一致导致的光标错位问题。
