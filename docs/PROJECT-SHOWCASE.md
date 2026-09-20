# 项目详解：从 Android 客户端到内容治理后台

这份文档用于面试、简历附件和项目讲解。它把仓库里的代码按照“产品问题—技术方案—验证证据—已知边界”重新组织，便于别人快速理解这个项目不是只有界面截图，而是包含客户端、服务端、数据和运营后台的完整闭环。

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

面试时可以进一步说明：客户端不能把“本地操作成功”当成“服务端已经成功”，所以同步任务需要区分操作类型、重试状态和媒体上传阶段；服务端仍然要做最终鉴权和幂等校验。

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

代码中对以下场景有明确处理：未配置 Key、服务端错误、响应为空、超时、结果格式不完整。AI 的结果必须经过用户预览确认，这也是一个适合在面试中展开的“自动化能力与用户控制权”取舍。

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

## 7. 端到端演示脚本

如果需要向面试官演示，可以按下面顺序操作：

### 普通用户链路

1. 使用 `user1 / 123456` 登录。
2. 在发现页打开一篇图文笔记，双击点赞、收藏、评论。
3. 打开搜索，切换笔记/用户/商品分类，观察本地与服务端结果合并。
4. 进入发布页，输入想法，调用 AI 助手生成草稿，确认后回填编辑器。
5. 发布图文内容，观察图片上传和服务端回写。
6. 进入钱包，使用模拟充值，查看账本；在私信中送礼，观察余额扣减。

### 运营后台链路

1. 打开创作者工作台，查看内容列表和数据趋势。
2. 导入 `server/public/templates/creator-drafts.csv`，检查草稿导入结果。
3. 打开管理员后台，查看数据概览、笔记审核、用户、举报和规则。
4. 修改一条内容的审核状态，回到客户端确认非公开内容不可被普通用户看到。

配套截图见：

- [Android 回归截图](test-screenshots/)
- [创作者工作台截图](doc-screenshots-final/60_web_creator_login.png)
- [管理员工作台截图](doc-screenshots-final/65_web_admin_dashboard.png)
- [审核使用说明](MODERATION-GUIDE.md)

## 8. 验证证据

当前仓库提供了三类可复核材料：

| 类型 | 位置 | 作用 |
| --- | --- | --- |
| 服务端自动化测试 | `server/*.test.js`、`server/smoke-test.js` | 验证统计、审核、AI、存储和接口行为 |
| Android 测试入口 | `app/src/test`、`app/src/androidTest` | 验证基础逻辑和回归场景入口 |
| 操作截图 | `docs/test-screenshots/` | 证明页面和关键交互经过实际操作 |

推荐验证命令：

```powershell
npm ci --prefix server
npm test
.\gradlew.bat test
```

编译、测试和真实设备运行是三件不同的事：自动化测试通过不代表所有设备上的动画、媒体解码和键盘适配都没有问题；发布仓库时应当如实说明验证范围。

## 9. 下一步可扩展方向

如果要继续把项目往“更像生产系统”的方向推进，优先级可以是：

1. 把 Room 主线程查询逐步迁移到 Repository + coroutine/Executor。
2. 为发布任务增加唯一幂等键，并让服务端返回可重放的同步结果。
3. 把评论回复、评论点赞和消息通知的完整数据结构下沉到服务端。
4. 为图片和视频上传增加对象存储适配、断点续传和内容类型扫描。
5. 为审核规则、AI 建议和人工结果补充更细的权限与审计策略。
6. 为关键 API 增加 OpenAPI 文档、集成测试和 CI 构建产物。

这些内容适合作为面试中“当前完成度”和“后续规划”的分界，避免把演示功能描述成已经具备线上生产能力。
