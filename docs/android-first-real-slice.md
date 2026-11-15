# Android 首批真实闭环：实现与验收边界

## 当前结论

首批代码建立了 Credential Manager → 服务端一次性 challenge → 独立 native token exchange → Multica session 的客户端路径，并沿用现有真实 HTTP 工作区、任务列表、详情和时间线读取。它**不是已通过的真实闭环**：当前仓库不含服务端实现，也未获得可用的非生产服务、OAuth client、测试账号或 workspace；因此真实 Google、双端同账号/同数据验收均未执行。

现有 Web `POST /auth/google` 仍保持 authorization-code 合同。Android 的 Google ID token 不会发送到该接口。服务维护者需在隔离开发分支/本地测试环境提供：

- `POST /auth/google/native/challenge` → `{attempt_id, nonce}`；短时、单次使用并绑定尝试；
- `POST /auth/google/native` `{attempt_id,id_token}` → `{token,user}`；服务端校验签名、`iss`、`aud`、`exp`、nonce，并以 provider + `sub` 识别身份；
- 保持 Web authorization-code 登录兼容，不修改或部署生产配置。

## P0 防误交付门禁

| 条件 | 构建/运行行为 | 证据 |
|---|---|---|
| 默认 demo | `fixtureMode=true`，仅构造 `LocalFixtureGateway`，不会显示 Google 登录 | `MainActivity.kt`、`AppConfigTest.fixtureBuildKeepsSafeInvalidDefaultWithoutOAuthConfiguration` |
| connected | 必须显式 `fixtureMode=false`、绝对 HTTP(S) API、非 `.invalid` host、非空公开 server client ID | `app/build.gradle.kts`、`AppConfig.kt`、`scripts/test-build-mode-gates.sh` |
| release | 必须 connected、HTTPS、非 loopback | `app/build.gradle.kts` 的 release task gate 与负测 |
| 运行时 | connected 只构造 `HttpMulticaGateway`；不捕获网络错误后降级 fixture | `MainActivity.kt` |
| fixture Google | fixture gateway 明确拒绝 native Google 方法，控制器也拒绝调用 provider | `LocalFixtureGateway.kt`、`AppControllerTest.fixtureBuildCannotUseGoogleLogin` |

`googleServerClientId` 是可公开 client identifier，不是 secret；client secret、私钥、token、OTP、测试账号不得进入仓库或评论。

## 台账映射

| 台账 | 首批实现 | 自动测试/验收 |
|---|---|---|
| G03 Google 登录 | Credential Manager 请求绑定服务端 nonce 的 ID token；独立 native exchange；仅持久化返回的 Multica token | `AppControllerTest.googleLoginUsesServerNoncePersistsOnlyMulticaTokenAndLoadsWorkspaces`；真机 GMS/provider 和真实签名校验待执行 |
| G23 会话安全/切换 | AndroidKeyStore session；logout/401 清理；generation 阻止 logout 后迟到登录写入；logout 清 Credential Manager 状态 | `logoutFencesLateGoogleCredentialAndClearsProviderState` 及既有两种 session lock 顺序回归；设备 KeyStore 已有独立入口 |
| W02 工作区 | bearer `GET /api/workspaces`，选择后以 workspace header 隔离读取 | 既有 controller/contract tests；同账号双端 workspace 待真实服务 |
| W07 任务列表 | `GET /api/issues`，当前只读取响应 `{issues,total}` 的 `issues` | loading/empty/retry/401 fixture 测试；**无服务端分页、过滤或排序参数** |
| W12 任务详情 | `GET /api/issues/:id`；字段仅 `id, identifier, title, description?, status, priority, created_at, updated_at` | detail fixture/contract tests；assignee、labels、附件、关系等未实现 |
| W18 评论/时间线只读 | `GET /api/issues/:id/timeline`，完整 oldest-first 数组；UI 每次本地 reveal 6 条 | empty/retry/迟到响应隔离测试；这不是服务端分页 |

A/B 账号切换边界：logout 先推进 generation，再清本地 session 与 Credential Manager 状态；旧 credential/exchange/workspace 响应不能提交新状态。服务端 bearer token 的即时撤销仍是后端能力，当前客户端 logout 只承诺清理本机状态。

## 明确未实现

首批未实现服务端 native endpoints、真实 Google 配置与设备验证、服务端分页/筛选/排序、任务创建/编辑、评论写入/reaction、附件、成员/权限管理、执行与实时协作、推送、App Links 以及台账其余能力。fixture 与本地合成测试只证明合同、状态机和失败路径，不替代线上或产品验收。

## 获批后端到端步骤

1. 服务维护者给出非生产 base URL、部署 SHA、native endpoint 合同和公开 Web/server client ID；凭据通过安全配置传递。
2. 在具备 GMS/provider 的目标设备安装 connected debug APK，验证 challenge 单次/过期/nonce/audience/issuer 负例。
3. 同一获批账号分别登录 Web 与 Android，比对 user id、workspace id/slug、任务 id 及上述详情字段。
4. A 登录发起慢请求后 logout，再登录 B；确认 A 的迟到响应、缓存和 session 不可见，并验证 401 清理。
5. 回归旧 Web authorization-code 登录；确认原端点请求/响应及账号映射未改变。
