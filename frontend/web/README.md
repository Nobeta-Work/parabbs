# Para BBS 前端

Vue 3、TypeScript、Vite、Naive UI 和 Vditor 4.0.0。

## 开发

需要 Node.js 20.19+ 或 22.12+。

```sh
npm ci
npm run dev
```

默认访问 `/bbs/`，`/api` 请求代理至 `http://localhost:8081`。可在本地 `.env.local` 配置 `VITE_PORT` 和 `VITE_API_BASE_URL`。

```sh
npm run build   # 类型检查与构建，输出 dist/
npm run preview # 预览构建结果
npm test        # 编辑器与文章加载逻辑测试
```

## 源码

- `src/views`：页面。
- `src/components`：共享组件；`editor` 保留 Vditor 原生行为并补齐站点需要的功能。
- `src/composables`：目录与文章加载逻辑。
- `src/api`、`src/stores`：接口与状态。
- `src/styles`：原生 CSS、主题变量和公共样式。

页面样式留在对应组件中。修改时优先调整已有规则，避免追加重复覆盖。构建产物和本地配置不作为源码提交。

一次性验证页面和截图统一放入 `verification/`，测试报告与覆盖率输出也已忽略。`src` 中的功能测试保留，用于检查工具栏定位、图片小标题、图表渲染和文章加载。
