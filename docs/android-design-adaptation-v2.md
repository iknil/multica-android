# Multica Android 设计与适配整改方案 V2

**状态：可评审方案与交互原型，不代表范围、实现或发布已获批准。**

**版本：2026-11-14.2 · RD · 对齐 BA V2 `d5c55e0` 与 38 项验收矩阵**

## 1. 评审结论

本方案采用 QA 推荐组合作为**待确认默认值**：固定 main SHA `9cb59baec1ea51d0745651db47eb4d67c025b8b7`、monorepo `apps/mobile` 双平台、任务协作 P0、草稿策略 D0。它不是对 main 当前 Android 可用性的宣称；本轮没有运行 Android 构建、模拟器或真机验证。

相较上一稿，本轮完成四项整改：

1. 将浅/深色 token 按文本、容器、图标、填充用途拆分，移除一个 Amber 同时承担多个角色的做法；
2. 给出不预先四舍五入的 WCAG 对比度计算证据，并在原型中应用同一组 token；
3. 把 iOS Action Sheet 按用途映射为 Android bottom sheet、anchored menu、确认对话框或原生 picker，定义复验步骤；
4. 以 BA V2 的七字段、通知起点、D0 草稿边界及 38 项矩阵重排实施顺序和工作量。

配套 `android-core-prototype.html` 是 Inbox、Issue detail/timeline、评论操作 bottom sheet 的可交互高保真方向稿。它只验证信息架构、状态角色和交互约定，不是运行中的应用，也不替代 Compose/React Native 渲染取样或 TalkBack 真机证据。

## 2. 同一套待确认范围

### 2.1 默认任务协作 P0

| 纳入 P0 | 明确边界 |
|---|---|
| 登录与工作区 | 固定 HTTPS 环境；认证失效重新鉴权，不泄露旧空间数据 |
| 前台 Inbox | **通知起点是已登录、应用前台的 Android Inbox**；建议前台事件到可见恢复 p95 ≤ 5 秒，绝不是系统 push SLA |
| Issue 发现 | 筛选、搜索、详情及有权限对象的外部 custom-scheme 深链 |
| Issue 写入 | 创建、基础编辑、指派；权限和最终状态由服务端判定 |
| 协作 | 评论、回复、mention、成功创建后的附件上传 |
| Run | 查看及停止；未知结果先查服务端状态，不能直接重试 |
| 可靠性 | D0 同进程保存、失败可重试、unknown-result 核验、无障碍及自适应布局 |

默认 P1：Chat（AC14）、系统 push（AC22）、reaction 写入（AC23）、HTTPS App Links（LINK04）。原型底栏保留 Chat 的产品位置，但以 `P1` 标签和不可误判的说明标出，不将其计入默认工程量或验收通过。

### 2.2 七个字段合同

P0 创建/基础编辑只覆盖以下字段；未在表内的管理与批处理能力不能由“编辑已支持”推导出来。

| 字段 | Android 交互 | 边界与错误处理 |
|---|---|---|
| title | 全屏编辑页单行输入 | 必填与长度按服务端合同；错误保留输入 |
| description | 全屏多行输入 | D0 同进程保存；mention 是否造成副作用以服务端合同为准 |
| status | 单选 bottom sheet | 只列出当前身份可用项；失败恢复原值 |
| priority | 单选 bottom sheet | 清楚显示“未设置”；不依赖颜色表达 |
| assignee type/id | 可搜索单选 bottom sheet | 仅已有 member/agent/squad；每次写入复验权限 |
| project | 可搜索单选 bottom sheet | 仅已有项目；不含创建项目或项目管理 |
| date-only due date | Android date picker dialog | 按 date-only 传输与显示，不擅自附加时区时刻 |

不含：新建任务时上传、任务删除、批量编辑、成员/Agent/团队/项目管理、复杂 artifact 沙箱。新任务成功后可在详情评论中附文件。

### 2.3 Reaction 边界

默认 P0 只呈现服务端已有 reaction 汇总，不提供增删入口，不做乐观计数。若用户升级 AC23：长按评论后显示常用 reaction 快捷行和 “More reactions” sheet，并增加权限、去重、失败回滚、快速重复点击及跨客户端一致性证据，增量估算见 §9。

## 3. 草稿恢复：原承诺、D0 与 D1

上一版将“进程被系统回收后恢复未发送内容”写进核心恢复行为，范围过宽且未先解决加密、TTL、备份及账号隔离。BA V2 将默认收窄为 D0；本方案和原型均以 D0 为准。

| 方案 | 恢复承诺 | 适用边界 | 代价 / 依赖 |
|---|---|---|---|
| **上一稿原承诺（已撤回为默认）** | 编辑、评论在进程死亡后仍恢复 | 没有明确持久化对象、TTL、加密和清理规则，不可作为可验收合同 | 隐私、跨账号泄露、备份外溢和陈旧草稿风险不可控 |
| **D0 推荐默认** | 同一进程内 picker、后台、导航及配置变化后保存新建/编辑/评论文本、mention/reply 目标、仍有效的附件 URI | 进程死亡/重启后不承诺恢复未发送正文；dirty 退出告警；显式失败保留输入；未知写结果先查服务端；退出登录、401、撤权清空 | 最低实现与隐私负担；不能满足“杀进程后恢复”用户需求 |
| **D1 可选升级** | 加密本地文本草稿，建议 TTL 24 小时；按账户/工作区/对象隔离 | 包含新建字段/文本、未保存编辑文本、评论文本及 mention/reply 目标；不含 token/OTP、文件二进制、下载 URL、Chat 草稿（除非另批） | +4–7 RD 人日；需隐私、备份、TTL、密钥与清理策略批准，并执行 AC19B/C/D |

若无法保证加密和全路径清理，不得用明文持久化冒充 D1。D0 的“同进程保存”和 D1 的“进程死亡恢复”必须在产品文案及 QA 证据中分开表述。

## 4. 用途化浅/深色 token

### 4.1 Token 表

颜色名称描述**语义用途**，不是品牌色别名。同一 warning/danger 家族拆成容器、文本、图标/填充，以避免低对比度组合。

| Role | Light | Dark | 允许用途 |
|---|---:|---:|---|
| `canvas` | `#F6F8FC` | `#0E1117` | 应用背景 |
| `surface` | `#FFFFFF` | `#171C24` | 列表、导航、sheet 表面 |
| `text-primary` | `#161A22` | `#F4F7FB` | 标题、正文、主数字 |
| `text-secondary` | `#526070` | `#B2BDCC` | 元数据、辅助说明 |
| `action-focus` | `#285CC7` | `#9AB9FF` | 链接、选中态、焦点环；不用作大段正文 |
| `on-action` | `#FFFFFF` | `#10213F` | 实心 action 上的文字/图标 |
| `warning-container` | `#FFF1D6` | `#3B2A13` | warning 背景 |
| `warning-text` | `#6F410D` | `#FFD58E` | warning 正文 |
| `warning-icon` | `#8F4D00` | `#F2B85A` | warning 图标/边线 |
| `danger-container` | `#FCE8ED` | `#432029` | danger 背景 |
| `danger-text` | `#981B35` | `#FFB0BC` | danger 正文 |
| `danger-icon-fill` | `#B4233C` | `#FF8FA2` | danger 图标、实心破坏性按钮 |
| `on-danger` | `#FFFFFF` | `#35131B` | danger fill 上的文字/图标 |

约束：disabled 状态除颜色外还必须改变可操作性并向辅助技术报告 disabled；优先级/状态同时使用文字与图标；焦点环至少 2dp 且不被裁切；不透明 foreground/background 组合按本表测试，任何动态 alpha 叠色必须从最终合成像素重新计算。

### 4.2 完整精度对比度证据

计算使用 WCAG 标准 sRGB 通道线性化，并按 `(Llighter + 0.05) / (Ldarker + 0.05)` 得到比值。是否通过使用未四舍五入值判定；下表显示小数点后 12 位。正文/小字目标 ≥ 4.5:1，关键非文本 UI/图标/焦点目标 ≥ 3:1。所有列出的组合均满足对应阈值。

| Theme | Foreground / background | Ratio | 用途 |
|---|---|---:|---|
| Light | text-primary / surface | 17.427804552680 | 文本 |
| Light | text-primary / canvas | 16.391415931559 | 文本 |
| Light | text-secondary / surface | 6.430107105739 | 文本 |
| Light | text-secondary / canvas | 6.047724470173 | 文本 |
| Light | on-action / action | 6.094400609740 | 实心按钮文字 |
| Light | action / surface | 6.094400609740 | 链接/焦点 |
| Light | action / canvas | 5.731981612821 | 链接/焦点 |
| Light | warning-text / warning-container | 7.713873550864 | warning 文本 |
| Light | warning-icon / warning-container | 5.822960227541 | warning 图标 |
| Light | warning-text / surface | 8.614211820668 | warning 文本 |
| Light | danger-text / danger-container | 7.040023004602 | danger 文本 |
| Light | danger-icon-fill / danger-container | 5.515356621338 | danger 图标 |
| Light | on-danger / danger-icon-fill | 6.468860709350 | 破坏性按钮文字 |
| Dark | text-primary / surface | 15.915147679747 | 文本 |
| Dark | text-primary / canvas | 17.587747006018 | 文本 |
| Dark | text-secondary / surface | 8.993393124578 | 文本 |
| Dark | text-secondary / canvas | 9.938552012434 | 文本 |
| Dark | on-action / action | 8.203442318533 | 实心按钮文字 |
| Dark | action / surface | 8.760193622776 | 链接/焦点 |
| Dark | action / canvas | 9.680844454694 | 链接/焦点 |
| Dark | warning-text / warning-container | 9.931082001780 | warning 文本 |
| Dark | warning-icon / warning-container | 7.716273813071 | warning 图标 |
| Dark | warning-text / surface | 12.345437622283 | warning 文本 |
| Dark | danger-text / danger-container | 8.250489933594 | danger 文本 |
| Dark | danger-icon-fill / danger-container | 6.567947187770 | danger 图标 |
| Dark | on-danger / danger-icon-fill | 7.698413133439 | 破坏性按钮文字 |
| Dark | focus / canvas | 9.680844454694 | 焦点环 |

原 Amber `#D97706` 对白色仅 `3.185823:1`，对原 Canvas `#F7F8FA` 仅 `2.998044:1`；main 浅色 Chat warning text / 15% warning 背景静态估算仅 `1.719936:1`。因此三者均不能继续作为当前用途化方案的文本证据。

以上是静态十六进制输入的计算证据，不替代 Android 最终渲染截图像素取样、系统对比度设置、disabled/pressed/overlay 组合及 QA 真机复验。

## 5. 核心界面与交互意图

### Inbox

- 以事件状态为结构：Needs you、Runs active、Recently changed，而非大面积装饰卡片。
- 每行同时呈现对象、动作、发生时间和未读标志；整行点击区域 ≥48dp。
- 顶部筛选与搜索保留；前台恢复超出建议 5 秒时显示 warning container，并提供明确重试。
- Inbox 是默认通知起点；没有系统权限请求、channel 或锁屏通知承诺。

### Issue detail / timeline

- 顶部先显示标识、标题、status/priority、assignee/project/due date；字段操作均可见文本标签。
- Timeline 把评论、Run 状态和字段变更放在同一执行脉络；失败/unknown 不靠 toast 一闪而过。
- Comment composer 在 D0 下显示 “Same-process draft” 提示；dirty 返回先确认，显式失败保留正文。
- 外部深链进入时构建合法父栈；Back 顺序依次关闭键盘、sheet、编辑页，再返回获权工作区主 tab。

### Comment actions

- 长按和 overflow 按钮到达同一 action model；TalkBack 不要求用户发现长按。
- 多操作使用 modal bottom sheet；每项有图标、动词标签与 ≥48dp 行高。
- 破坏性动作位于末尾、视觉隔离，选择后再进入两按钮确认对话框。
- 默认 P0 不显示 reaction 写入；若 AC23 升级才加入快捷 reaction 行。

## 6. Android 菜单替代决策

main 在 comment/chat/inbox/profile 及 issue/project 页面使用 `ActionSheetIOS`。Android 不得把超过三个选择传给 `Alert.alert`；Alert 只保留确认或极少量决策。

| 用途 | Android 替代 | 行为约束 | 典型位置 |
|---|---|---|---|
| 上下文多操作 | Material 3 modal bottom sheet | 行图标+标签；破坏性项末尾隔离；支持 drag handle、scrim、Back 关闭 | 评论、Chat 消息长按 |
| 2–4 个非破坏性 overflow | Anchored dropdown menu | 锚定 overflow；不遮挡关键上下文；键盘/Back 可关闭 | Inbox、详情页顶部 |
| 破坏性确认 | 两按钮 confirmation dialog | 先从 menu/sheet 选动作，再确认；默认焦点不落在破坏项 | 删除本地附件、取消未提交编辑等 |
| status/priority/assignee/project | 单选 bottom sheet | 当前值打勾；长列表可搜索；只展示有权限选项 | Issue create/edit |
| date-only due date | Android date picker dialog | 本地显示、date-only 提交；取消不更改值 | Issue create/edit |
| 长表单 | 全屏编辑页 | dirty 返回告警；D0 同进程保存 | 创建/编辑 Issue |
| Reaction（仅 AC23 升级） | 快捷行 + More reactions sheet | 去重、权限、失败回滚和重复点击保护 | 评论/Chat message |

## 7. 菜单与核心链路复验步骤

每个菜单入口至少对 Compact、Medium、Expanded 布局，浅/深色、默认/200% 字号，API 目标设备矩阵运行以下步骤，并保留构建 SHA、设备/API、角色 fixture、录屏/截图及写计数。

1. **可达性（AC20/AC21）**：分别用触摸、TalkBack、键盘/开关控制从 long-press 与可见 overflow 入口打开；确认动作顺序和可访问名称一致，触控目标 ≥48dp，焦点不丢失。
2. **权限与状态**：用 owner/member/read-only/revoked fixture 逐项核对动作可见/disabled 规则；服务端仍执行最终授权，撤权后不显示缓存内容。
3. **破坏性路径**：选择破坏项，确认先出现两按钮 dialog；取消写计数为 0，确认只写 1 次，失败后状态和反馈一致。
4. **导航与返回（LINK03）**：外部 custom-scheme 冷/热启动详情；打开键盘→sheet→dialog，系统 Back/顶栏 Back/预测性返回按最内层优先关闭，并最终回到合法的工作区父栈。
5. **幂等与恢复（AC19/AC19A）**：快速双击、断网、超时、前后台切换；不得重复执行。显式失败保留输入并允许重试；unknown-result 先读取服务端再决定重试。
6. **视觉**：浅/深色、系统高对比度、200% 字号和三类宽度下无截断/遮挡；对最终截图中 warning/danger/focus 的像素重新采样计算。
7. **回归（REG01/REG02）**：运行 mobile 现有 typecheck/lint/test 及新增 Android 单元/集成测试；在 iOS 复验对应 Action Sheet 路径。若触及 core/API，再运行受影响 Web/Desktop 契约与读写回归。

`Alert.alert` 复验需加入静态搜索/代码审查，禁止 Android 分支传入超过三个按钮。所有 menu adapter 共用 typed action model，平台层只负责呈现，避免 iOS 与 Android 权限/业务规则漂移。

## 8. 38 项验收矩阵对齐

本方案不复制或改写 BA V2 的 pass/fail 条件；`d5c55e0` 中 `android-acceptance-matrix.csv` 是范围验收主表，本节只说明设计/适配证据挂接方式。38 项均仍为 Android 实测未执行。

| 组 | 数量 | 本方案覆盖重点 |
|---|---:|---|
| `AC01–AC23` | 23 | 默认 P0 核心链、七字段、前台 Inbox、reaction/Chat/push 的 P1 边界 |
| `AC19A–AC19D` | 4 | unknown-result、D0/D1、隔离清理与跨客户端草稿边界 |
| `BUILD01–BUILD04` | 4 | clean debug build、Android 平台标识、独立路线条件门禁、签名内测 |
| `LINK01–LINK04` | 4 | 外部 custom scheme、安全授权、父栈/Back；HTTPS App Links 默认 P1 |
| `REG01–REG03` | 3 | mobile 与新增测试、iOS/受影响跨端回归、更换 SHA 时全量重映射 |
| **合计** | **38** | 原型不使任何一项自动通过 |

Gate 对齐：G0 冻结组合和边界；G1 先完成 `BUILD01/02`、`LINK01–03` 及 `REG01/02` 基础证据；G2 才跑全部适用 P0 和 38 项矩阵；G3 才处理签名内测、升级与发布证据。若基线变化，立即触发 `REG03`，旧 main 证据不得沿用。

## 9. 实施顺序、工作量与依赖

以下为固定 SHA、monorepo、默认任务 P0、D0 下的 **RD 人日区间，不是承诺排期**。包含实现与开发自测，不含等待评审、后端改造、QA 独立执行、签名/上架及未知原生模块返工。多人并行只缩短日历时间，不减少总工作量。

| 顺序 | 工作包 | 预计 RD 人日 | 退出条件 / 依赖 |
|---:|---|---:|---|
| 1 | G0 冻结与设计盘点 | 2–3 | 批准唯一 SHA/repo/P0/D0、app identity、API/设备阈值 |
| 2 | G1 host/build identity 与 clean debug build | 3–5 | 固定 Node/pnpm/JDK/SDK；BUILD01 可复现 |
| 3 | Android 平台标识、API/upload/WS、secure auth/deep link | 3–5 | BUILD02、LINK01/02 基础通过；固定 HTTPS 后端与测试账号 |
| 4 | navigation/menu/icon/system bars/back adapters | 5–8 | 本稿菜单决策及 LINK03；Pixel + 第二 OEM 真机 |
| 5 | main 上默认 P0 功能适配 | 8–12 | 七字段、评论/附件、Run；服务端权限/幂等合同明确 |
| 6 | D0 恢复、错误、无障碍、自适应样式 | 5–8 | AC19/20/21；Compact/Medium/Expanded、浅/深色 |
| 7 | 自动化、clean build、iOS/跨客户端回归支持 | 4–7 | REG01/02；既有 `.github/workflows/mobile-verify.yml` 与新增检查 |
| 8 | QA 修复与不确定性缓冲 | 4–7 | 适用矩阵缺陷闭环 |
|  | **默认合计** | **34–55** | G3 签名/发布操作之前 |

### 可选范围增量

| 升级项 | 增量 RD 人日 | 额外依赖 |
|---|---:|---|
| Chat 升 P0 | +4–7 | AC14 全链、断线/pending/Stop 合同 |
| 系统 push 升 P0 | +7–12 | 服务端触发、Firebase/凭据、channel、通知 SLA、真机 |
| D1 加密草稿 | +4–7 | 隐私/备份/TTL/密钥/清理批准，AC19B/C/D |
| reaction 写入 | +2–4 | AC23 权限/计数/幂等/回滚合同 |
| HTTPS App Links | +2–4 | 域名所有权、association 文件、未安装 Web 降级 |
| 独立仓库/SDK 路线 | 初始 +10–20，另计长期同步 | 私有制品、许可、版本治理、BUILD03；需整体重估 |

如果 MUL-9202 先进入 main 或 SHA 改变，触发 `REG03`、七字段/评论/回复/附件/Chat Stop/38 项完整重映射与全量重估，不能在上表中静默替换基线。

### 必须先满足的依赖

- 获批唯一 SHA、repo、P0 组合及 D0/D1；Android application id、variants、schemes、min/target API；
- 固定 HTTPS backend、测试账户/角色、附件限制与 fixture；
- 服务端触发、幂等、冲突和 unknown-result 查询合同；
- Pixel 与至少一台其他 OEM 设备；
- G3 才需要 signing/Play 凭据；push 升级才需要 Firebase/后端/通知 SLA；App Links 升级才需要域名所有权。

## 10. 待用户确认的最小选择

1. **工程组合**：推荐 `main@9cb59...` + monorepo `apps/mobile`；若坚持独立仓库，接受初始 +10–20 人日、BUILD03 和长期同步治理。若等待 MUL-9202，则接受 `REG03` 重映射和重新估算。
2. **草稿边界**：推荐 D0（同进程、最低隐私负担）；若杀进程恢复是硬需求，升级 D1 并批准 24h TTL、加密、备份/清理政策与 +4–7 人日。
3. **P1 是否升级**：默认不升级 Chat/push/reaction/App Links；每项可独立叠加，但必须接受 §9 的代价和对应 AC/LINK 依赖。

未得到这些批准前，可以继续做 G1 风险验证和原型评审，但不得把推荐项写成用户已确认范围，也不得进入签名或发布。
