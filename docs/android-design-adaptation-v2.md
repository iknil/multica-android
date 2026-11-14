# Multica Android 设计与适配整改方案 V3

**状态：原生工程路线已确认；产品范围组合仍待确认；38 项产品实测尚未开始。**
**版本：2026-11-14.4 · RD · 对齐 BA V2 `d5c55e0` 与 38 项验收矩阵**

## 1. 路线更正

工程路线现固定为独立 `multica-android` 仓库中的 **Kotlin + Jetpack Compose 原生应用**。此前 Expo/React Native、monorepo 和 34–59 RD 人日建议全部撤回，不再作为 G0/G1 前提或排期依据。Web/iOS/RN 源码只复用业务语义和服务端协议；原生实现不共享 TypeScript runtime。

本轮骨架可编译不代表产品已实现，也不代表 38 项矩阵通过。原生架构、合同、配置缺口和 48–70 RD 人日新估算见 `android-native-architecture.md`。

## 2. 同一套待确认范围

默认范围继续采用 BA V2：任务协作 P0、七字段、前台 Inbox 通知起点、D0 草稿；Chat、系统 push、reaction 写入、D1 草稿和 HTTPS App Links 均为单独增项。

- **七字段：** title、description、status、priority、assignee type/id、project、date-only due date；不从“基础编辑”外推删除、批量、成员或项目管理。
- **Reaction：** 默认只读服务端汇总；写入获批后才增加权限、幂等、回滚和一致性测试。
- **通知起点：** 已登录且应用前台的 Inbox 实时恢复；不是 Android 系统 push SLA。
- **D0：** 同进程内跨 picker、后台、导航和配置变化保存新建/编辑/评论输入；显式失败保留，dirty 退出告警；进程死亡后不承诺恢复。
- **D1：** 若“杀进程后恢复”成为硬需求，另行批准加密、TTL、备份排除、账号/工作区隔离、登出清理和迁移，增量 5–8 RD 人日。

原承诺与 V2 收窄的差异：上一稿笼统承诺进程死亡后恢复，却没有定义持久化对象、TTL、密钥与清理规则；D0 删除该隐私风险并降低首发实现面。只有需要跨进程恢复且能批准上述安全合同的场景适用 D1。

## 3. 分享入口边界

已核实 Copy link 的来源是 HTTPS Web URL。工程是否原生与分享 A/B 无关：

- **A（App Links，+3–5 RD 人日）：** 现有 HTTPS 在已安装时进入原生详情，未安装时 Web 降级；依赖最终 applicationId、签名 SHA-256、域名 `assetlinks.json` 和路径白名单。
- **B（低风险默认）：** 现有分享继续 Web，App 内功能仍全部原生；不加 intent filter。

A/B 未确认前，骨架不加入 App Links 或声称 custom scheme 承接现有分享。

## 4. 浅/深色用途 token

颜色按文本、容器、操作和信号用途分开；状态还必须带文字/glyph，颜色不是唯一信息源。

| 用途 | Light | Dark | 约束 |
|---|---|---|---|
| canvas | `#F7F8FA` | `#111318` | 页面背景 |
| surface | `#FFFFFF` | `#1B1E25` | app bar、sheet、内容层 |
| primary text/icon | `#181A20` | `#F1F3F7` | 正文与高强调图标 |
| secondary text | `#555B66` | `#B9C0CC` | 辅助信息，不用于 disabled 猜测 |
| primary action | `#2459C4` | `#A9C7FF` | 操作、选中、焦点；深色 action 上用 `#062B66` |
| attention | `#8A4B00` | `#FFC176` | 警示/待处理，配文字与 glyph |

计算按 WCAG 2.x：通道 `c = byte / 255`；`c <= 0.04045` 时取 `c / 12.92`，否则取 `((c + 0.055) / 1.055)^2.4`；`L = 0.2126R + 0.7152G + 0.0722B`；对比度为 `(Lmax + 0.05) / (Lmin + 0.05)`。中间值使用 double 且不四舍五入，只在最终结果显示 6 位小数：Light primary/canvas `16.366867:1`、secondary/canvas `6.427668:1`、action/canvas `5.986611:1`、attention/canvas `6.401578:1`；Dark primary/canvas `16.726180:1`、secondary/canvas `10.152996:1`、action/canvas `10.883623:1`、attention/canvas `11.627946:1`；深色 action text/action fill `7.960601:1`。最终仍须从 Compose 渲染截图复算；这些值是 token 数学证据，不替代真机证据。

## 5. Android 菜单与键盘规则

- 短单选/操作集合：Material 3 modal bottom sheet；
- 与具体图标/字段锚定的少量非破坏动作：anchored dropdown menu；
- 不可逆操作：confirmation dialog；
- 日期：Material date picker，传 date-only；
- 多字段/长文本：全屏页面；
- 关键入口不得藏进 kebab；Android Back 先关 IME/菜单/sheet，再回退页面；dirty 页面显示退出确认。

复验步骤：Compact/Medium/Expanded 各打开字段与评论菜单；键盘显示时检查主动作、insets 和焦点；Back 依次关闭 IME→弹层→页面；旋转/分屏后确认 D0 状态；浅/深色、200% 字体和 TalkBack 核对焦点顺序/名称/48dp 目标；Pixel 与第二 OEM 均记录截图/视频。当前 HTML 原型仅是方向稿，旧 RN chrome 不再作为原生验收证据。

## 6. G0 / G1 与计数

- **G0 已关闭工程路线：** Kotlin/Compose + 独立 Android 仓库 + 服务端接口复用。
- **G0 待产品：** 分享 A/B、Chat、push、reaction、D1；不阻塞基础骨架。
- **G1 骨架：** JDK 17、SDK 35、wrapper 可完成 clean debug build/test/lint。
- **G1 联调：** 必须补最终 applicationId、environment URL、测试账号/workspace、加密存储选择和 WS 夹具。
- **G3 发布：** release keystore、Play track、隐私与升级证据，不与前两项混写。

38 个唯一 ID 的主表仍是 BA V2：`AC01–AC23` 23 项、`AC19A–AC19D` 4 项、`BUILD01–BUILD04` 4 项、`LINK01–LINK04` 4 项、`REG01–REG03` 3 项，合计 **38**。原型、JVM 测试和一次本机构建不会自动通过任何产品实测项；需将 BUILD/LINK 的 RN 描述按原生路线重映射后由 QA 独立执行。

## 7. 实施顺序与估算

1. 骨架/CI/variant（4–6）；
2. HTTP/WS + OTP/session/workspace/安全存储（7–10）；
3. 导航 + Inbox/search/Issue read（10–14）；
4. 七字段 + 评论/附件/Run（14–20）；
5. D0/错误/无障碍/自适应（6–9）；
6. 自动化/跨端回归/缺陷缓冲（7–11）。

默认原生合计 **48–70 RD 人日**；App Links `+3–5`、D1 `+5–8`、reaction `+2–4`、Chat `+6–10`、push `+8–13`。该数字是范围/合同冻结前的工程初估，不含 QA 独立执行、后端改造、等待或 G3 发布。
