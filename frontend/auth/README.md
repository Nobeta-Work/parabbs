# Para Auth 前端

独立 Vue 3 + TypeScript + Vite 应用，采用 GitHub 登录页式的紧凑居中布局、浅灰表单面板、绿色主按钮与深浅色主题，保留 Para 品牌。提供登录、注册、当前账号、修改密码及退出确认页。

## 本地开发

```powershell
cd frontend/auth
npm install
npm run dev
```

打开 `http://localhost:5174/auth/login`。开发服务器代理认证 API 与协议请求到 `http://localhost:9000`；可通过 `.env.example` 中的配置更改端口与代理目标。

Auth 后端仍需正确配置数据库、Redis、密钥库和凭据；数据库使用 `infra/mysql/auth_schema.sql` 手动建表。本地 HTTP 联调设置：

```text
AUTH_ISSUER=http://localhost:5174/auth
AUTH_ALLOW_LOCAL_HTTP=true
AUTH_SECURE_COOKIES=false
```

通过前端开发服务器访问授权入口 `/auth/oauth2/authorize`，并让 RP 使用同一 issuer 发现协议端点；已登记客户端的回调地址仍须符合后端约束。

## 登录与部署

登录及退出使用浏览器原生 POST 表单，分别提交到 `/auth/api/login`、`/auth/api/logout`，携带真实 CSRF 参数，交给 Spring Security 处理。这两个接口不是 JSON API。成功登录后由服务端优先恢复保存的授权请求，没有保存的请求时跳转 `/auth/account`；失败跳转 `/auth/login?error`，退出成功跳转 `/auth/login?logout`。前端不会通过 AJAX 消费授权回调，也不会读取或存储 Session Cookie、客户端密钥或令牌。注册及改密使用同源 JSON API，并在每次写入前获取 CSRF。改密成功后要求重新登录。

```powershell
npm run build
```

将 `dist` 内容部署到 Web 根目录下的 `auth/` 子目录。`nginx.conf` 提供独立站点示例，部署时将 `auth_backend` 修改为实际后端地址，并合并到现有站点配置；不直接替换 BBS 的站点配置。

代理路由必须区分：

- `GET /auth/login`、`GET /auth/logout` 和前端页面、资源返回 SPA。
- `POST /auth/api/login`、`POST /auth/api/logout` 交给后端安全过滤器，不再按同一路径的 GET/POST 分流。
- `/auth/api/**`、`/auth/oauth2/**`、`/auth/.well-known/**`、`/auth/userinfo`、`/auth/connect/**` 转发后端。

Nginx 示例使用更具体的前缀及精确匹配选择后端，其他 `/auth/` 请求返回前端。`proxy_pass` 不附加 URI，保留后端需要的 `/auth` 前缀。宿主机 Nginx 连接同机后端时，把 upstream 地址改为 `127.0.0.1:9000`；示例中的 `auth:9000` 需要 Nginx 与 Auth 容器位于同一 Docker 网络。现有 `infra/docker/frontend/Dockerfile` 只部署 BBS，不会自动部署这个前端。

生产将这些路由合并到直接提供 HTTPS 的 Nginx 站点，保持 Secure Cookie，并设置 `AUTH_FORWARD_HEADERS_STRATEGY=FRAMEWORK`；后端只允许可信代理访问，代理覆盖转发头。如果 HTTPS 在更外层终止，必须按可信代理链配置外部协议和端口，不能直接把本示例内层 HTTP 的 `$scheme` 当成浏览器协议。`AUTH_ISSUER` 必须与公开地址完全一致。`vite preview` 仅用于预览静态构建，不提供完整认证代理。默认“返回社区”链接为 `/bbs/`。管理端与自定义授权同意页不在这个简版前端中；协议继续使用后端默认能力。
