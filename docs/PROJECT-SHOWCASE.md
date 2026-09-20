# 项目详解：从 Android 客户端到内容治理后台

这份文档记录项目的技术设计和关键实现，帮助读者理解这个项目如何连接 Android 客户端、服务端、数据层和运营后台。

## 1. 项目目标

项目以生活方式内容社区为原型，目标不是复刻某个线上产品的全部细节，而是练习一条完整的产品工程链路：

```text
浏览内容 → 搜索内容 → 关注/点赞/收藏/评论 → 发布草稿
    → 图片上传与同步 → 内容审核 → 创作者查看数据 → 管理员治理
```

其中最值得展示的部分是“移动端体验”和“后台工程”同时存在：Android 端解决交互、缓存和弱网问题；Node 服务端解决权限、审核、统计和数据一致性问题；两个 Web 工作台则让业务闭环可以被直接操作和演示。

## 2. 技术栈和职责边界

| 层次 | 技术 | 主要职责 |
| --- | --- | --- |
| Android UI | Java、Kotlin、ViewBinding、Material、RecyclerView、ViewPager2 | 页面、列表、手势、编辑器、媒体与状态反馈 |
| Android data | Room、DAO、Entity、Migration | 本地用户、笔记、互动、订单、钱包账本 |
| Android network | Retrofit、OkHttp、Gson | REST、JWT、Multipart 上传、错误处理 |
| Sync | PendingSyncStore、RemoteApiClient | 发布/草稿失败重试、媒体上传恢复、服务端同步 |
| Backend | Node.js 18+、Express | API、JWT、bcrypt、文件上传、静态 Web |
| Persistence | MySQL 8 / isolated JSON | 正式式持久化与无依赖本地演示 |
| Governance | 关键词规则、AI 建议、人工审核、举报、审计日志 | 内容安全与可追踪处理 |
| Web consoles | 原生 HTML/CSS/JS | 创作者内容管理和管理员审核工作台 |

## 3. 关键实现一：Local-first 与同步队列

Android 端不是把每一次展示都绑定到网络请求。信息流、用户、笔记、点赞、收藏和草稿首先由 Room 提供本地数据；网络可用时再读取服务端并更新本地状态。这样做的价值是：

1. 首屏可以展示已有内容，网络慢时不至于完全空白。
2. 断网时用户仍然可以查看已缓存内容和保存草稿。
3. 发布时如果媒体上传失败，不会静默发布成“无图笔记”，而是登记待同步任务。
4. 详情页或同步流程可以重新尝试上传，成功后再回写服务端地址。

对应代码入口：

- `app/src/main/java/com/xiaohongshu/network/PendingSyncStore.java`
- `app/src/main/java/com/xiaohongshu/network/RemoteApiClient.java`
- `app/src/main/java/com/xiaohongshu/ui/publish/repository/NoteRepository.java`
- `app/src/main/java/com/xiaohongshu/database/`

实现上需要注意：客户端不能把“本地操作成功”当成“服务端已经成功”，所以同步任务需要区分操作类型、重试状态和媒体上传阶段；服务端仍然要做最终鉴权和幂等校验。

## 4. 关键实现二：内容审核状态机

笔记的审核状态与上下架状态分开设计：

```text
approved  ───────┐
pending           ├─→ 人工审核 ─→ approved / rejected / hidden
rejected  ───────┘

isDraft = true / false 只表示是否仍是草稿，不能代替内容审核状态。
```

审核链路包括：

1. 关键词规则快速初筛。
2. 可选的 AI 复审建议，AI 只提供建议，不直接替代人工裁定。
3. 管理员查看案件、备注、举报和内容详情。
4. 审核、举报、用户封禁和媒体管理写入可查询的审计信息。
5. Android 客户端同步审核结论，并向作者展示必要的未通过原因。

对应代码入口：

- `server/moderation.js`
- `server/moderation-routes.js`
- `server/moderation-worker.js`
- `server/public/admin.html`
- `server/public/admin.js`
- `docs/MODERATION-GUIDE.md`

这里的工程取舍是把 AI 放在“建议层”，把权限判断和最终状态写入放在服务端。这样即使 AI 超时或不可用，人工审核链路仍然可以工作。

## 5. 关键实现三：AI 笔记助手的安全边界

发布编辑页可以输入“想法”和“风格”，服务端调用可选的 AI 服务返回标题、正文和话题。客户端只负责发起请求、展示结果和让用户确认；它不会自动发布，也不接触 API Key。

```text
Android editor
    ↓ idea + style
Express /api/ai/note-assistant
    ↓ server-side key
AI provider
    ↓ title + body + topics
Android preview → user confirms → editor filled
```

代码中对以下场景有明确处理：未配置 Key、服务端错误、响应为空、超时、结果格式不完整。AI 的结果必须经过用户预览确认，体现自动化能力与用户控制权之间的取舍。

## 6. 关键实现四：钱包与账本演示

钱包功能虽然是演示闭环，但仍然按照服务端最终校验的思路实现：

- 客户端先预检余额，给出及时提示。
- 服务端再次校验余额并执行扣费，避免只信任客户端。
- 收礼方收益使用幂等入账，避免重复打开会话造成重复收益。
- Room 保存本地交易记录，钱包页面展示余额和明细。
- 充值是模拟支付，不会调用真实支付平台，也不会产生真实扣款。

相关代码入口：

- `app/src/main/java/com/xiaohongshu/ui/wallet/WalletRepository.java`
- `app/src/main/java/com/xiaohongshu/database/entity/CoinTransactionEntity.java`
- `app/src/main/java/com/xiaohongshu/database/dao/CoinDao.java`
- 服务端 `server/transaction-store.js`
