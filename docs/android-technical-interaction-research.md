# Multica Android 技术与交互阶段调研（原生路线修订）

> 状态：历史调研已按 2026-11-14 用户决策修订。
> 协议参考：`multica` main `9cb59baec1ea51d0745651db47eb4d67c025b8b7`。
> 实现仓库：独立 `multica-android`。

## 结论

已确认采用 **Kotlin + Jetpack Compose** 原生 Android。上一版“不新建 Kotlin/Compose、优先 RN monorepo”的建议已撤回，Expo/RN 与私有 `@multica/core` 的依赖分析只保留为历史背景，不再约束实施。Web/iOS/mobile 源码用于核实业务和 HTTP/WebSocket 合同；Android 通过服务端接口复用能力。

原生详细架构、已核实接口、配置表、分享 A/B 与 48–70 RD 人日估算见 `android-native-architecture.md`；范围/视觉/菜单/验收映射见 `android-design-adaptation-v2.md`。

## 产品与交互方向（仍有效）

P0 目标是“收到前台信号 → 理解上下文 → 处理任务/干预 Agent → 确认结果”。Compact 使用 bottom navigation，Medium/Expanded 使用 navigation rail 和 list-detail；创建/长编辑采用全屏，短单选采用 bottom sheet，锚定少量动作使用 menu，不可逆操作用确认 dialog。Android Back、IME、窗口尺寸、TalkBack 和 200% 字体是实现合同，不以 iOS action sheet/popover 行为替代。

默认范围对齐 BA V2 的任务协作 P0、七字段、D0 和前台 Inbox。Chat、push、reaction 写入、D1、App Links 仍待产品逐项确认。38 项验收仍未执行。

## 已验证与未验证边界

本轮原生骨架包含 Compose 入口、浅深主题、HTTP/WS 合同和 JVM 单测，并运行 clean build/test/lint。尚未完成真实 OTP、HTTP/WS、Keystore、数据库、真机、键盘、菜单、外部深链、弱网或产品回归，不得据此宣称可发布。
