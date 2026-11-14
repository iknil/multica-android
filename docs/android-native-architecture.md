# Android 原生架构、协议合同与可行性验证

**状态：Kotlin + Jetpack Compose 工程路线已确认；连接环境、产品增项和发布配置仍待关闭。**
**基线：`multica` main `9cb59baec1ea51d0745651db47eb4d67c025b8b7`（协议参考）+ 独立 `multica-android`（实现）**
**日期：2026-11-14**

## 1. 本轮结论

本仓库已建立最小原生 Android 工程。应用层、主题、HTTP/实时合同和 JVM 合同测试均为 Kotlin；UI 使用 Jetpack Compose / Material 3。Web、iOS 和既有 React Native 源码只作为业务行为与线上协议参考，不复制 TypeScript runtime，也不依赖私有 `@multica/core`。

骨架刻意不发送线上请求：`API_BASE_URL` 是不可路由的 `https://api.example.invalid`，尚未选择 HTTP/WS 客户端、数据库或依赖注入框架。这样可以验证编译工具链和协议边界，同时避免把“可编译”误报为“已经联调”。

## 2. 工程与模块边界

```text
app
├── MainActivity / MulticaApp       Compose 单 Activity 入口与可行性状态页
├── ui/theme                        按用途区分的浅/深色 Material token
└── core
    ├── config/AppConfig            环境 URL 与版本的启动约束
    └── network
        ├── ApiContract             HTTP 路由、header、30 s 超时合同
        └── RealtimeContract        /ws URL、auth 首帧、心跳/退避常量
```

后续按 feature + data/domain/ui 垂直切片扩展，而不是建立巨型 `screens`/`utils`：

- `core/network`：选定的 HTTP/WebSocket transport、统一错误和 request id；
- `core/auth`：Android Keystore 支持的加密 token 存储、会话更新和 401 清理；
- `core/database`：若 D1 获批才引入加密持久化与迁移；
- `feature/auth`、`workspace`、`inbox`、`issue`、`run`：各自状态与 Compose UI；
- `navigation`：Navigation Compose、自适应 list/detail 与 Android Back；
- `testing`：fake contract、截图/无障碍、网络故障和回归夹具。

首选单 Activity + unidirectional data flow。Repository 只暴露业务结果，ViewModel 不直接拼 URL 或 header。前后台切换由 lifecycle-aware collector 驱动：实时连接只在已登录且前台时存在，重连后刷新缓存，因为 v1 没有事件 replay。

## 3. 已核实服务合同

核实来源均是固定 main SHA 的 `apps/mobile/data/*` 与服务端实现；“已核实”表示静态合同存在，不等于 Android 对真实环境联调通过。

### 3.1 HTTP 与认证

| 合同 | 原生边界 |
|---|---|
| API base | 环境注入的绝对 HTTP(S) URL；仓库不得提交真实密钥或 token |
| 通用 header | `Content-Type: application/json`、`X-Client-Platform: mobile`、`X-Client-OS: android`、`X-Client-Version`、`X-Request-ID` |
| 鉴权 | `Authorization: Bearer <token>`；token 使用 Keystore 支持的加密存储，禁止明文 SharedPreferences |
| 工作区 | `X-Workspace-Slug: <slug>`；服务端负责 slug→ID 与成员资格校验 |
| 登录 | `POST /auth/send-code` `{email}`；`POST /auth/verify-code` `{email,code}` → token/user |
| 会话更新 | `POST /api/auth/refresh`；响应含 `renewed`、`token`、`expires_at`、`check_again_in_seconds` |
| 首批读取 | `GET /api/me`、`GET /api/workspaces`、`GET /api/inbox` |
| 超时与失效 | 参考移动端 30 s；401 才清理会话，网络错误/5xx 不误删 token |

`ApiRoutes`、`ClientMetadata` 与 `ApiContract.headers` 已将这些不随 transport 变化的部分固化，并用 JVM 测试确保 Android 标识、Bearer 和 workspace header 不回退为 iOS。

### 3.2 WebSocket

- `https`→`wss`、`http`→`ws`，路径固定 `/ws`；
- query：`workspace_slug`、`client_platform=mobile`、`client_os=android`，可选 `client_version`；
- socket 打开后首帧：`{"type":"auth","payload":{"token":"..."}}`；收到 `{"type":"auth_ack"}` 后才视为业务连接可用；
- 应用心跳：`{"type":"ping"}` / `pong`；参考策略为 25 s heartbeat、10 s timeout；
- 前台连接、后台暂停；指数退避 + full jitter，1 s base、30 s cap；
- 每次重连读取最新 token，重连成功后主动刷新查询缓存。

`RealtimeContract` 已覆盖 URL、query、JSON 转义和 transport frame 常量。骨架尚未宣称完成 socket 互通、弱网或前后台实测。

## 4. 分享链接：与工程路线解耦

当前 Web/iOS 的 Copy link 实际产物是 **HTTPS Web URL**，不是 Android custom scheme；因此 P0 只能在以下二选一中承接现有入口：

- **A — App Links：** 已安装时把获批 HTTPS host/path 路由到原生详情，未安装时继续 Web。新增域名 `assetlinks.json`、最终 applicationId、签名证书 SHA-256、路径白名单、安全授权及多浏览器/OEM 验证。原生增量 **3–5 RD 人日**，不含域名或签名等待。
- **B — 保持 Web（当前低风险默认）：** 分享链接继续打开 Web；App 内业务仍全部原生。无需 Android link filter，新增工程量近似 0，但外部链接不会直达 App。

在 A/B 未确认前，本骨架不声明 VIEW intent filter，也不把 custom scheme 当作现有分享入口。若后续另增只用于 App 内调试/内部导航的 custom scheme，它不能替代对外 HTTPS 分享合同。

## 5. G0 / G1 前置条件（已统一）

### G0：工程与范围决策

已关闭：Kotlin + Compose、独立 `multica-android`、服务端接口复用、参考 main SHA。
仍待产品确认：分享 A/B、Chat、系统 push、reaction 写入、D1 持久草稿。它们不阻塞骨架编译。

### G1：连接与实现配置

骨架构建只需要 JDK 17、Android SDK 35 与仓库 wrapper；连接构建/联调还需要下表的实际值。发布签名和 Play 凭据属于发布门禁，不能和基础联调混为一个范围决策。

| 配置 | 已核实值 | 待补项 | 负责人 | 获取/关闭方式 |
|---|---|---|---|---|
| 工具链 | Gradle 8.13、AGP 8.11.1、Kotlin 2.0.21、compile/target 35、min 26（暂定） | CI image 与 min SDK 产品确认 | Android 工程所有者 | 在 CI clean checkout 重跑同一命令；核对设备覆盖 |
| Android identity | 临时 `ai.multica.android`，debug 后缀 `.debug` | 最终 applicationId、version policy | 发布所有者 | 从 Play/组织命名登记取得并写入 variant |
| 服务环境 | 路由与 header 合同已核实 | dev/staging HTTPS API URL、证书链 | 后端/环境所有者 | 通过受控配置注入，真机执行 `/api/me` 探针 |
| 测试身份 | OTP 流程已核实 | 测试邮箱、workspace slug、角色矩阵 | QA + workspace owner | 建立最小权限测试账号，不在仓库记录凭据 |
| 安全存储 | 必须 Keystore 支持的加密存储 | 具体库、失效/迁移策略 | Android + 安全 owner | threat review 后实现登出/401/密钥失效测试 |
| 实时 | `/ws`、auth-first、android query 已核实 | staging WS 可达性与事件夹具 | 后端 owner | 前台连接、断网、重连、token 轮换联调 |
| 分享 | 现状为 HTTPS Web URL | A/B；若 A，host/path、assetlinks 与签名 SHA-256 | 产品 + 域名/发布 owner | `adb shell am start`、App Links assistant、未安装 Web 降级 |
| 发布 | debug APK 可构建 | release keystore、Play track、隐私材料 | 发布 owner | G3 受控注入；私钥不得进入 issue 或仓库 |

## 6. 原生工作量重估

原 34–59 RD 人日基于 RN/monorepo，现已撤回。以下是独立原生、默认任务协作 P0、D0 草稿下的工程初估，包含开发自测，不含 QA 独立执行、等待、后端改造、商店审核；范围/合同冻结后再校准。

| 工作包 | RD 人日 | 依赖/退出条件 |
|---|---:|---|
| 原生基础、CI、环境/variant、安全基线 | 4–6 | clean build；配置不入库；debug/release 边界 |
| HTTP/WS、OTP、会话、工作区与加密 token | 7–10 | 真实 staging 合同、401/刷新/重连验证 |
| 自适应导航、Inbox、搜索、Issue 列表/详情 | 10–14 | 前台通知起点、浅深色/Back/键盘证据 |
| 七字段编辑、评论/回复/mention、附件、Run | 14–20 | 权限、上传、unknown-result 合同稳定 |
| D0、错误恢复、无障碍与窗口适配 | 6–9 | 进程内恢复、TalkBack、200% 字体、双窗 |
| 自动化、跨客户端回归支持与缺陷缓冲 | 7–11 | 38 项矩阵重映射和 QA 修复闭环 |
| **默认合计** | **48–70** | 不含下列增项与 G3 发布操作 |

可选增量：App Links A `+3–5`；D1 加密持久草稿 `+5–8`；reaction 写入 `+2–4`；Chat `+6–10`；系统 push `+8–13`。增量不能在依赖未明确时静默并入默认 P0。

## 7. 验证边界与后续项

本轮应以 `clean testDebugUnitTest assembleDebug lintDebug` 的真实输出为构建证据；JVM 测试覆盖配置约束、HTTP identity、WS URL 与 auth frame。它不等于 38 项产品验收，也没有模拟器/真机、键盘、TalkBack、菜单、App Links、真实 OTP/HTTP/WS 证据。

下一阶段顺序：

1. CI clean checkout 复现骨架，冻结 applicationId/variants/environment；
2. 选择 transport/加密存储并用 staging 完成 OTP→workspace→`/api/me`→WS auth_ack 探针；
3. 以同一 38 项矩阵重新映射原生证据，先实现默认 P0；
4. 在 Pixel + 第二 OEM 执行浅/深色、键盘、bottom sheet/anchored menu、Back、字体缩放与弱网；
5. A 获批才加入 App Links 并执行已安装/未安装/未授权/错误路径回归；
6. G3 再处理受控签名、升级和分发。
