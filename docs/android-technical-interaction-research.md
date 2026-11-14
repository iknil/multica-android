# Multica Android 技术与交互阶段调研

> 阶段：评审稿（尚未进入实现）  
> 调研角色：RD（技术架构与交互方向）  
> 源码基线：`multica` 主仓库 `9cb59baec1ea51d0745651db47eb4d67c025b8b7`  
> 调研日期：2026-11-14

## 1. 结论先行

### 已确认事实

1. Multica Web 是 Next.js 薄路由 + `packages/views` 产品视图 + 私有 `@multica/core` 业务/数据层 + `@multica/ui` 组件层；Desktop 在复用这些包的同时，增加 Electron、daemon、CLI、本地文件、原生通知、更新和窗口能力。
2. 主仓库已有一套规模不小的移动客户端：`apps/mobile` 使用 Expo SDK 57、React Native 0.86、React 19 和 Expo Router；当前约有 318 个 TS/TSX 文件、3.75 万行代码，134 个文件直接引用 `@multica/core`。它已经实现登录、工作区、Inbox、My Issues、Chat、任务详情/编辑/评论/附件、运行记录、项目、搜索和设置等主要流程。
3. 这套客户端目前是 **iOS 产品**，而不是已经可交付的跨平台产品：配置、运行脚本、README、原生导航、SF Symbols、弹层和搜索等路径明显偏向 iOS；WebSocket 还固定上报 `client_os=ios`。
4. Android 空仓库不能直接复制这套应用：移动端依赖 `workspace:*` 的私有 `@multica/core`，该包版本为 `0.0.0` 且 `private: true`。脱离 monorepo 会同时失去包解析、协议模型、缓存键与实时更新的一致性。
5. 远端尚有一条未合并的移动端重构分支 `origin/agent/lambda/f8e6ee9db10d`（调研时 tip `4e3ddf98c`，MUL-9202），它会整体替换当前移动目录结构并扩大 `@multica/core` 复用，但仍明确只支持 iOS。若不先确定基线，Android 实现会产生高冲突和重复劳动。

### RD 建议

- **不新建 Kotlin/Compose 平行产品，也不以 WebView 包壳。** 优先把主仓库 `apps/mobile` 演进为 iOS/Android 双平台应用。
- 手机端定位为“通知处理、任务审核、智能体追踪与及时干预”的协作控制面，而不是缩小版 Desktop；Desktop 的 daemon、runtime、CLI、本地技能与多窗口能力不进入 Android 首版。
- 先由负责人裁定“当前 main 还是 MUL-9202 重构分支”为唯一实施基线，再开始代码改造。
- Android 应用保留共享业务层，新增明确的平台适配层；系统返回、导航、弹层、图标、通知、文件选择、系统栏与安全存储均按 Android 行为实现，避免在现有 iOS 分支上堆散落判断。

### 待评审决策

1. **实施基线：** 当前 `main` 还是待合并 MUL-9202 分支？
2. **代码归属：** 接受 Android 代码留在 Multica monorepo 的 `apps/mobile`，还是必须使用独立 `multica-android` 仓库？后者必须先解决 `@multica/core` 的发布/版本化或受控 vendoring。
3. **首版范围：** 下文 P0/P1 是 RD 技术建议，最终范围需与 BA 阶段成果合并确认。
4. **发布条件：** application id、签名/Play Console、隐私政策、推送供应商及最低 Android SDK 尚未给定。

## 2. 调研方法与边界

本报告基于代码静态审阅、路由/依赖统计和 Android 官方设计/架构资料。未进行 Android 真机或模拟器构建，因为被调研基线尚无 Android 配置或构建入口。本阶段不提交产品代码。

为避免混淆，后文使用以下标记：

- **事实：** 可从当前源码或已验证资料直接得出；
- **建议：** RD 对目标架构、范围和交互的提案；
- **待决策：** 开始实现前需产品/技术负责人确认。

## 3. 现有产品与架构盘点

### 3.1 Web 与 Desktop

| 层次 | Web | Desktop | 对 Android 的含义 |
|---|---|---|---|
| 页面/路由 | Next.js，路由覆盖 Inbox、Issues、Projects、Chat、Agents、Squads、Autopilots、Runtimes、Skills、Settings、Usage 等 | React/Vite + Electron，多标签与窗口 | 移动端应按高频任务重组，不照搬完整侧栏 |
| 共享视图 | `@multica/views` | `@multica/views` | 视图面向 DOM，不能默认在 React Native 直接复用 |
| 业务/数据 | `@multica/core` | `@multica/core` | Android 应继续共享 API、schema、query/mutation、实时语义 |
| UI | `@multica/ui` | `@multica/ui` | Web 组件不等于移动组件，但设计语义应统一 |
| 平台能力 | 浏览器、Web 通知 | daemon、CLI、更新、文件系统、原生通知、窗口 | daemon/runtime 本地管理不属于手机首版 |

**源码依据：** `apps/web/app/[workspaceSlug]/(dashboard)`、`packages/views`、`packages/core`、`packages/ui`、`apps/desktop/package.json`。

### 3.2 当前 `apps/mobile` 已有能力

**事实：** 当前主分支的移动应用已覆盖：

- 邮箱 OTP 登录、会话恢复、工作区选择/切换；
- 四个底部入口：Inbox、My Issues、Chat、More；
- Inbox 列表与详情；
- Issue 列表/筛选、详情、新建、编辑、状态/优先级/负责人/项目/截止日期；
- 评论、回复、mention、reaction、附件；
- Agent run 列表、执行文本、取消；
- Chat 会话切换、消息发送及运行中状态；
- 项目列表/详情/新建/编辑/资源；
- 搜索、Agents、Pins、个人资料与通知偏好；
- WebSocket 实时更新、React Query 缓存、Zustand 本地状态、SecureStore；
- Markdown、代码高亮、图片灯箱；
- `en`、`zh-Hans`、`zh-Hant` 文案。

主要依据：`apps/mobile/app` 路由树、`apps/mobile/package.json`、`apps/mobile/data`、`apps/mobile/i18n`。

### 3.3 Android 就绪度缺口

| 缺口 | 证据 | 影响 |
|---|---|---|
| Android 应用身份与构建缺失 | `apps/mobile/app.config.ts` 只有 `ios`；`package.json` 只有 iOS 运行脚本 | 无法生成可安装、可签名、可分发的 Android 包 |
| 平台标识错误 | `apps/mobile/data/realtime/ws-client.ts` 固定 `client_os=ios` | 监控、兼容策略和平台分析失真 |
| 系统图标偏 iOS | tab/menu 使用 `source="sf:..."` | Android 图标缺失或语义/视觉不一致 |
| 导航与搜索偏 iOS | 多处 `formSheet`、iOS native search、popover 语义 | 系统返回、预测性返回、弹层和焦点行为不合规 |
| 系统栏/insets 未完成 Android 验证 | 现配置和说明只覆盖 iOS | 刘海、手势区、edge-to-edge 可能遮挡内容 |
| 文件/图片权限只描述 iOS | Expo Image Picker 配置只有 iOS 文案说明 | Android Photo Picker、文档选择和权限路径需重验 |
| 通知交付链缺失 | 目前有“通知偏好”，未见 Android push 注册/渠道/深链闭环 | 无法达到移动端及时响应的核心价值 |
| 测试与 CI 缺失 | README 和脚本只说明 iOS；无 Android 构建 job | 平台回归无法成为合并门禁 |
| 无 Android 专项可访问性验证 | 现有 UI 未按 TalkBack/字体缩放/48dp 做设备验收 | 存在不可操作和内容截断风险 |

这不是“打开 Expo Android 开关”即可交付的问题；数据层可复用，但壳层、导航、交互和交付链需系统补齐。

## 4. 技术路线比较

评分：5 为最好；“长期成本”分数越高代表成本越低。

| 方案 | 到首版速度 | 业务语义复用 | Android 原生体验 | 长期成本 | 关键问题 | 结论 |
|---|---:|---:|---:|---:|---|---|
| A. 在现有 Expo/RN 应用启用 Android | 4 | 5 | 4 | 4 | 需重做平台壳层并完成设备 QA | **推荐** |
| B. Kotlin + Jetpack Compose 绿地实现 | 2 | 1 | 5 | 2 | API、schema、auth、realtime、缓存与所有页面形成第二套实现 | 暂不采用 |
| C. PWA/WebView 包壳 | 5 | 3 | 1 | 2 | 返回栈、离线、通知、附件、键盘、深链与性能难达交付标准 | 仅可做临时演示 |

### 为什么推荐 A

1. 当前移动产品已经承载约 3.75 万行交互和领域逻辑；重写不能带来同等规模的用户价值。
2. `@multica/core` 已经提供 API/schema/query/mutation/realtime 语义，保留它可避免 Web、Desktop、iOS、Android 四端漂移。
3. Expo/React Native 并不妨碍 Android 原生行为；关键是把平台壳层做成显式适配，而不是强行复制 iOS 控件。
4. 一套移动产品可以共享领域测试、文案、缓存更新与发布节奏，同时允许导航、图标、系统栏和弹层在两端分化。

### 独立仓库为何是高风险选项

当前 `@multica/mobile` 对 `@multica/core` 使用 `workspace:*`，而 core 是私有未发布包。独立仓库若要成立，必须先选择一种治理方案：

1. 将 core 拆出可发布的、带语义版本的移动 SDK；
2. vendoring 一份受控子集，并建立自动同步与兼容测试；
3. 以 subtree/submodule 方式引入，但仍需解决 workspace 和构建工具链。

三者都会引入额外发布/同步责任。**建议 Android 空仓库只保留说明或停止使用，实际代码进入主 monorepo。**

## 5. 推荐目标架构

```mermaid title="Android 目标架构"
flowchart TB
  UI[React Native screens\nAndroid-native hierarchy] --> VM[Screen/View models]
  VM --> CORE[@multica/core\nAPI · schema · query/mutation · realtime]
  CORE --> API[Multica API + WebSocket]
  UI --> ADAPTER[Platform adapters]
  ADAPTER --> NAV[Navigation / predictive back]
  ADAPTER --> SYS[System bars / insets / window size]
  ADAPTER --> IO[Notifications / deep links / file & photo]
  ADAPTER --> SEC[Secure token storage]
  UI --> STATE[React Query + Zustand]
  STATE --> CORE
```

### 5.1 保持共享的部分

- API client、认证协议、schema 和领域类型；
- React Query 的 query keys、mutation 和 WebSocket cache updater；
- 工作区、Issue、Inbox、Chat、Agent run 的业务语义；
- 本地化 key、Markdown/代码内容模型；
- 可纯函数测试的筛选、排序、未读、pending/retry 规则。

**禁止：** 为 Android 复制一套 API client、另起 query key、手工改写 WebSocket 事件或把服务端模型再映射成不兼容模型。

### 5.2 平台适配边界

建议把以下能力放入集中 adapter/component，而不是在页面散落 `Platform.OS`：

- application id、scheme、adaptive icon、版本和签名变量；
- Navigation Bar / Rail / Drawer 与窗口尺寸适配；
- modal bottom sheet、dialog、full-screen editor、search；
- 系统 Back 与 predictive back 动画/状态保存；
- edge-to-edge、状态栏/导航栏、safe insets、键盘；
- Android notification channel、push token、通知深链；
- Photo Picker、document picker、分享与下载；
- SecureStore/Keystore 错误恢复；
- Android 图标映射与 TalkBack label；
- haptics、reduced motion、可访问性焦点。

### 5.3 应用状态与失败恢复

- 服务端状态继续由 React Query 管理；仅 UI 草稿/选择状态进入 Zustand 或路由 state。
- WebSocket 断开时显示非阻塞状态条，并指数退避重连；恢复后按受影响领域失效 query，避免全量刷新。
- 离线时允许浏览缓存；写操作若未提供可靠幂等协议，不伪装“已成功”，保留草稿并明确重试。
- 进程被系统回收后恢复 workspace、主 tab、列表滚动锚点和可安全恢复的详情路由；敏感 composer 内容需遵循产品隐私决策。
- 登录 token 保存在系统安全存储，不写 AsyncStorage、日志、URL 或 crash breadcrumb。

## 6. 首版范围建议（待 BA 对齐）

### P0：完成 Android 核心闭环

- OTP 登录、会话恢复、退出、工作区切换；
- Inbox 未读/已读、筛选、详情和深链；
- My Issues 列表/筛选/详情；
- Issue 状态、优先级、负责人、项目、截止日期更新；
- 评论、回复、mention、reaction、附件上传/预览；
- Agent run 状态、执行记录、取消；
- Chat 会话选择、发送、pending/retry/stop；
- WebSocket 重连、离线/错误/空状态；
- 浅色/深色、英文和简体中文；
- Android 系统返回/预测性返回、edge-to-edge、状态恢复、TalkBack 基线。

### P1：增强完整性

- 新建/编辑 Issue；
- Projects、Pins、Search；
- Profile 与通知偏好；
- 原生 push、notification channel、点击通知直达目标；
- 繁体中文验收、平板/折叠屏双栏细化。

### Later：暂缓

- Agents/Squads/Autopilots 的完整管理面；
- 广泛的 workspace 管理、成员、billing/usage；
- runtime、daemon、CLI、skills 的本地管理；
- 插件/主题创作、Desktop 文件系统和多窗口能力。

**范围原则：** P0 必须形成“收到信号 → 理解上下文 → 处理任务/干预 Agent → 确认结果”的闭环；若时间不足，应减少低频页面，而不是删掉错误恢复和系统返回。

## 7. Android 交互与视觉方向

### 7.1 产品交互命题

> 在移动端快速处理通知、审核任务、追踪智能体并及时干预。

因此首屏和详情应优先呈现“发生了什么、是否需我处理、当前运行到哪一步”，而不是桌面信息密度、数据看板或装饰性卡片。

### 7.2 产品化原则

1. **状态先于装饰：** 未读、阻塞、运行中、待审核等状态用稳定 glyph + 文本表达，颜色只作强化，不能成为唯一线索。
2. **一屏一主行动：** 列表用于判断，详情用于处理；编辑/创建采用全屏，短选项采用 bottom sheet。
3. **执行轨迹可扫描：** 评论、状态变化和 Agent run 形成安静的纵向时间线，重要错误和用户可介入点突出。
4. **Android 原生预期优先：** More 是真实页面或 drawer，不使用 iOS popover；返回手势可预览目的地且不丢草稿。
5. **失败可恢复：** loading、empty、offline、permission denied、upload failed 和 reconnect 都有明确状态与下一步。
6. **高频单手可达：** 主操作靠近底部，但不遮挡键盘和手势区；所有点击目标至少 48dp。

### 7.3 紧凑设计 token

仅定义 6 个基础颜色，避免页面各自创造“彩色卡片”。深色模式使用同一语义映射，不直接机械反相。

| Token | Hex | 用途 |
|---|---|---|
| Canvas | `#F7F8FA` | 应用背景 |
| Surface | `#FFFFFF` | 顶栏、底栏、sheet 与需要分层的内容 |
| Ink | `#181A20` | 主文本与高强调图标 |
| Action Blue | `#356AE6` | 主操作、选中态、链接、焦点 |
| Signal Amber | `#D97706` | 运行中、需关注、警示 |
| Critical Red | `#C83E4D` | 失败、阻塞、破坏性操作 |

状态还必须带文字或 glyph；成功态优先复用 Ink + check glyph，避免继续扩张调色板。分隔线与次级文字可由 Canvas/Ink 按透明度派生并通过对比度验证。

### 7.4 字体角色

- **UI / 正文：** Android 系统字体（Roboto）+ 系统 CJK fallback；遵循用户字体缩放。
- **标题：** 同一字体族的 Medium/Semibold，通过字重和间距建立层级，不引入品牌装饰字体。
- **代码/执行记录：** JetBrains Mono，仅用于代码、命令和结构化 transcript。
- **数字/状态：** 使用 UI 字体的 tabular number（可用时），保持计数和时间稳定。

不以极小字号换取信息密度；正文目标 16sp，辅助文字通常不低于 12sp，并在 200% 字体缩放下验证关键流程。

### 7.5 自适应布局

| 窗口 | 导航 | 内容 |
|---|---|---|
| Compact | 4 项 bottom navigation | 列表和详情全屏推进 |
| Medium | navigation rail | Inbox/Issue 等可采用 list-detail |
| Expanded / foldable | permanent rail | rail + list + detail；按 hinge/insets 分区 |

主目的地固定为 **Inbox / My Work / Chat / More**。屏幕宽度变化时切换布局，不按“手机/平板”设备名硬编码。

### 7.6 弹层与编辑规则

- 简短、可立即提交的单选/操作：modal bottom sheet；
- 创建任务、编辑多字段、长文本：全屏页面；
- 不可逆操作：confirmation dialog，标题直接说明结果；
- 搜索：Android 搜索页面/顶栏，支持返回关闭和 query 恢复；
- 菜单：少量上下文操作使用 menu；核心页面入口不得隐藏在 kebab 中。

## 8. 关键线框

### 8.1 Compact · Inbox

```text
┌──────────────────────────────┐
│ Inbox                   🔍  ●│  Top app bar
│ [All] [Needs me] [Runs]      │  Filter chips
├──────────────────────────────┤
│ ●  Agent run needs approval  │
│    GANY-4 · 2m               │
│    “Android scope ready…”  › │
├──────────────────────────────┤
│ ↻  Build recovered           │
│    MUL-9202 · 14m          › │
├──────────────────────────────┤
│    No more unread            │
├──────────────────────────────┤
│ Inbox   My Work   Chat  More │
└──────────────────────────────┘
```

行本身是点击目标，不套重复圆角卡片；状态 glyph、标题、对象和时间形成扫描轴。

### 8.2 Compact · Issue detail

```text
┌──────────────────────────────┐
│ ‹ GANY-4               ⋮     │
│ Create Multica Android app   │
│ [In progress] [High] [RD]    │  concise properties
├──────────────────────────────┤
│ ● Agent started        10:32 │
│ │ Environment prepared       │
│ ● Scope submitted      11:08 │  activity / run trace
│ │ View transcript ›          │
│ ○ Awaiting review            │
├──────────────────────────────┤
│ Reply…              📎  Send │  sticky composer
└──────────────────────────────┘
```

属性提供横向摘要，活动使用一条连续时间线；运行中/失败状态可以展开，不把每条事件做成独立彩卡。

### 8.3 Compact · Chat

```text
┌──────────────────────────────┐
│ ‹ 小刚 · Android research  ▾ │
├──────────────────────────────┤
│ Agent is running       Stop  │  persistent status banner
├──────────────────────────────┤
│ You  Review Android options  │
│                              │
│ RD   I recommend enabling…   │
│      [View cited source]     │
│                              │
│      Reconnecting… Retry     │
├──────────────────────────────┤
│ Message…            ＋  Send │
└──────────────────────────────┘
```

### 8.4 Expanded / foldable

```text
┌───────┬──────────────────┬────────────────────────────┐
│ Inbox │ Needs review (3) │ GANY-4                     │
│ Work  │ ● GANY-4         │ Create Multica Android app│
│ Chat  │   Android scope  │ [In progress] [High]       │
│ More  │                  │                            │
│       │   MUL-9202       │ Activity timeline         │
│       │   Mobile rewrite │ …                          │
└───────┴──────────────────┴────────────────────────────┘
  rail          list                  detail
```

窗口收窄或跨越 hinge 时，详情回到可预测的导航栈；折叠不应丢失当前对象和未提交输入。

## 9. 可用性、可访问性与可靠性验收

### 交互

- 所有触摸目标至少 48dp，紧邻目标之间保留足够间距；
- 系统 Back、顶栏 Back、预测性返回结果一致；未保存编辑会确认而非静默丢弃；
- TalkBack 朗读顺序、role、state 和 action 清楚；图标按钮有可本地化 label；
- 颜色不是状态的唯一表达；浅/深色均验证文本和控件对比度；
- 字体放大到 200% 时，登录、列表、详情、评论和确认对话框仍可完成；
- 遵循 reduced motion，状态动画不闪烁、不作为唯一反馈。

### 稳定性

- 冷启动、进程回收、token 过期、workspace 被移除均有确定落点；
- 网络离线/抖动/WebSocket 重连不产生重复评论或错误已读状态；
- 上传可取消/重试，失败项不从 composer 消失；
- 通知点击在冷/热启动下都进入正确 workspace/object，越权或已删除对象给出安全错误页；
- 日志、crash 和 analytics 不记录 token、评论正文或附件内容。

### Android 设备矩阵（最低建议）

- API 最低版本设备 + 当前主流 API + 最新 API；
- 小屏、标准手机、平板/横屏、至少一种折叠/窗口尺寸模拟；
- 手势导航和三键导航；
- 浅色/深色、英文/简中、字体 100%/200%；
- TalkBack；网络离线/高延迟；后台回收后恢复。

## 10. 分阶段实施与门禁

### Gate 0 · 基线决策（开始编码前）

- 裁定 main 或 MUL-9202；
- 裁定 monorepo 或独立仓库；
- BA/RD 合并 P0 范围；
- 确认 application id、scheme、min SDK、签名和 push 方案。

**通过条件：** 一份被认可的范围/基线决策，不允许两条移动架构并行实现。

### Gate 1 · Android host 可运行

- Expo Android config、adaptive icon、scheme、variant、脚本；
- CI 可构建 debug artifact；
- 登录、SecureStore、deep link、系统栏、图标、Back 基础适配；
- WebSocket 正确上报 Android。

**通过条件：** 干净环境可构建；模拟器和一台真机完成登录/重启恢复/退出。

### Gate 2 · P0 行为闭环

- Inbox、My Work、Issue、Run、Chat、评论附件；
- Android 导航/sheet/search 适配；
- loading/empty/error/offline/reconnect；
- 单元/组件测试复用 core 契约。

**通过条件：** 端到端完成“收到通知 → 打开任务 → 评论/修改 → 查看或停止运行”。

### Gate 3 · 自适应、通知与质量

- push/channel/deep link；
- medium/expanded list-detail；
- TalkBack、字体缩放、深色、进程回收；
- 性能、crash、隐私和发布配置。

**通过条件：** 设备矩阵通过，无 P0/P1 崩溃、越权、数据丢失或返回栈缺陷。

### Gate 4 · 内测发布

- 签名、版本、Play internal testing、隐私披露、回滚方案；
- crash/ANR、登录成功率、通知到达/打开、关键 mutation 失败率可观察。

**通过条件：** 内测可安装升级，回滚和告警责任明确。

## 11. 风险登记

| 风险 | 概率/影响 | 预防与缓解 |
|---|---|---|
| MUL-9202 与 main 发生整体目录冲突 | 高/高 | Gate 0 先选基线；不在旧架构并行开发 |
| 独立仓库无法消费私有 core | 高/高 | Android 留 monorepo；否则先立 SDK 发布项目 |
| iOS 控件假设散落页面 | 高/中 | 先建立 adapter/component inventory，再逐路由替换 |
| 通知服务/深链尚未定义 | 中/高 | 单独做通知契约和安全模型，P0 不伪装已支持 push |
| Android 设备/厂商差异 | 中/高 | CI + 真机矩阵；覆盖进程回收、权限、后台限制 |
| 写操作在断网/重连时重复 | 中/高 | 使用服务端幂等能力；无幂等时明确失败与人工重试 |
| 平板/折叠屏过早扩大范围 | 中/中 | P0 保证 compact 正确，复用 window-size 架构后再细化 |
| 需求范围复制 Desktop | 高/中 | 以移动任务闭环验收，Later 明确排除本地能力 |
| 可访问性在临发布才补 | 中/高 | 每个 Gate 带 TalkBack/字体/触控门禁 |

## 12. 对设计方向的自我审查

最初容易落入“通用 SaaS 手机模板”：每条信息一张圆角卡、蓝紫渐变、多个彩色 badge、把桌面侧栏机械压进 More。这个方向虽然快速，但会弱化 Multica 最重要的执行状态，也会让 Android 看起来像 iOS/Web 的移植品。

本稿据此做了四项修正：

1. **从卡片墙改为状态驱动的扁平列表与连续时间线**，让未读、运行、阻塞和待审核成为视觉主轴；
2. **删除装饰性渐变和扩张色板**，仅保留 6 个基础 token，颜色服务于行动和风险；
3. **不用 iOS 弹层/Popover 模拟 Android**，明确 bottom sheet、full-screen editor、predictive back 和 window-size navigation；
4. **不把 Desktop 功能数量当完整度**，以移动闭环、失败恢复和及时干预作为首版质量标准。

仍需在实现原型中验证：高密度执行记录是否需要折叠策略、Signal Amber 在深色主题的对比度、双栏切换时 composer 草稿的恢复，以及中文 200% 字体下属性摘要的布局。

## 13. 参考资料

### 内部源码（基线 `9cb59baec1ea51d0745651db47eb4d67c025b8b7`）

- `apps/mobile/app.config.ts`：Expo 配置与仅 iOS 平台项；
- `apps/mobile/package.json`：Expo/RN 版本、`workspace:*` core 依赖及仅 iOS 脚本；
- `apps/mobile/README.md`：产品明确标记为 iOS；
- `apps/mobile/app`：当前移动路由和功能面；
- `apps/mobile/data/realtime/ws-client.ts`：固定 `client_os=ios`；
- `apps/mobile/components/nav`、`apps/mobile/components/ui`：SF Symbols、popover/sheet 与 Android 待办；
- `packages/core/package.json`：私有 `@multica/core` 及领域 exports；
- `apps/web/app/[workspaceSlug]/(dashboard)`：Web 功能路由；
- `apps/desktop/package.json`：Electron 与共享包依赖；
- 远端候选基线 `origin/agent/lambda/f8e6ee9db10d`（tip `4e3ddf98c`）：MUL-9202 移动重构，仍配置 `platforms: ["ios"]`。

### Android / Material 官方资料（2026-11-14 已验证可访问）

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Build adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive)
- [Canonical adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts)
- [Build adaptive navigation](https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation)
- [Predictive back gesture](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)
- [Edge-to-edge and window insets](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [Material accessible design](https://m3.material.io/foundations/accessible-design/overview)
- [Android accessibility touch target guidance](https://support.google.com/accessibility/android/answer/7101858)

---

## 评审建议

本阶段建议只评审并冻结三件事：**唯一代码基线、monorepo 归属、P0 用户闭环**。三项明确后，RD 再提交 Gate 1 的实施拆分与验收清单；在此之前不应进入功能编码。
