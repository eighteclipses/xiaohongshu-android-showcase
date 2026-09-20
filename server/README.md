# Express backend

这是 Android 客户端配套的 Express 服务端，负责账号、笔记、互动、创作者平台、内容审核和演示种子数据。默认支持两种存储方式：

- `STORAGE=json`：零依赖演示模式，适合本地快速体验。
- `STORAGE=mysql`：MySQL 8.0 模式，适合验证迁移、事务和持久化配置。

## 启动

```powershell
npm ci
copy .env.example .env
npm run start
```

健康检查：`http://localhost:3001/api/health`

创作者工作台：`http://localhost:3001/`

管理员后台：`http://localhost:3001/admin`

默认演示账号由服务端启动时创建：`user1 / 123456`。管理员密码必须通过 `.env` 配置；如果留空，服务端会生成一次性随机密码并打印到启动日志。

## 环境变量

| 变量 | 用途 |
| --- | --- |
| `PORT` | HTTP 端口，默认 `3001` |
| `STORAGE` | `json` 或 `mysql` |
| `DATA_FILE` | JSON 存储文件路径 |
| `JWT_SECRET` | 必填，签发用户 JWT 的密钥 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 管理员账号 |
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` / `DB_NAME` | MySQL 连接参数 |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_MODEL` | 可选，AI 笔记助手 |

不要把 `.env`、真实密码、JWT 密钥或 AI Key 提交到公开仓库。

## 主要接口

- 用户：注册、登录、刷新令牌、个人资料、关注/粉丝。
- 内容：笔记列表、详情、草稿、创建、编辑、删除、点赞、收藏、评论。
- 搜索：笔记、用户和商品分类，支持关键词和排序。
- 创作者：统计、草稿 CSV 导入、内容管理、图文发布。
- 管理员：审核、关键词规则、举报、用户状态、评论、媒体、审计日志。
- 钱包：余额、模拟充值、薯币流水和送礼扣费演示。

需要登录的接口使用 `Authorization: Bearer <access_token>`。管理员接口使用管理员 JWT；旧的 `X-Admin-Key` 兼容模式默认关闭。

## 测试

```powershell
npm test
```

测试会使用隔离 JSON 数据并清理临时结果，不会覆盖你的正式数据文件。MySQL 模式迁移：

```powershell
npm run migrate:mysql
```
