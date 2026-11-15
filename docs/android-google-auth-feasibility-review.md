# Android Google 认证实施可行性评审

评审日期：2026-11-16。结论：**有条件可行，但不是仅改 Android 即可完成**。现有服务端 `/auth/google` 只接受 Google OAuth 授权码；Credential Manager 返回 Google ID token，二者不能混用。最小安全路径必须先增加独立的原生 challenge/ID-token 校验契约，再接 Android。本文仅作只读评审，没有修改生产认证配置、部署新入口或向既有授权码接口发送 ID token。

## 1. 固定基线与证据边界

- Android：`94e31c7e88b821e82f148ffb724a7302bfd0353f`（`multica-android`）。
- Web/服务端：`9cb59baec1ea51d0745651db47eb4d67c025b8b7`（`multica` 的本地 `main` 快照）。该源码 SHA 不等于已部署 SHA。
- 输入：BA 的认证专项审计 `4306ae9` 及 33 项验收矩阵；本评审复核工程可落地性，不替代全量 Web→Android 能力矩阵。
- vivo：本轮 `adb get-state` 结果为 `adb_device_unavailable`。未读取序列号、账号或 token；因此 GMS、Credential Manager provider、系统版本、区域与 Google 网络连通性均为**待设备实测**，不能从机型或历史安装记录推断。
- 未执行真实 Google 登录、真实 HTTP 联调、Cloud Console 变更、生产部署或产品验收；当前 Android 成功流程仍是 fixture 能力。

## 2. 现有服务端契约复核

### 2.1 `/auth/google` 是授权码交换，不是原生 ID-token 校验

`server/internal/handler/auth.go:528-531,571-692` 定义并处理 `{code, redirect_uri}`：

1. 使用服务端 `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`；
2. 以 `grant_type=authorization_code` 调 Google token endpoint；
3. 要求返回 `access_token`，再调用 `/oauth2/v2/userinfo`；
4. 以规范化 email 查找或创建 Multica 用户。

路由位于 `server/cmd/server/router.go:1596`。Web 的 `apps/web/app/auth/callback/page.tsx` 与 `packages/core/api/client.ts:1120-1128` 正是把浏览器取得的 code 和 redirect URI 交给该接口。该处理虽声明过 `id_token` 字段，但当前路径不验证其签名、`aud`、`iss`、`exp`、nonce，也不使用 Google 稳定 `sub`。

因此：

- Credential Manager 取得的 ID token **不得**放入现接口 `code` 字段；这会违反契约且不构成安全验证。
- 现接口接收请求侧 `redirect_uri` 的行为应另列 Web hardening（精确 allowlist/事务绑定），但不应在本轮未经批准改变生产行为。
- 新原生入口必须复用现有 `findOrCreateUser` 的注册开放、白名单/邀请、禁用用户等策略，而不是旁路新建用户。

### 2.2 身份关联与会话缺口

`server/pkg/db/queries/user.sql` 当前按 email 查用户，未见 provider subject 身份表或 Google `sub` 唯一约束。安全原生 OIDC 至少需要：

- 外部身份表：唯一 `(provider, subject)` 映射 `user_id`；为未来 provider 留扩展空间，优先于直接向 user 表塞 `google_sub`。
- 首次关联不能因 email 相同而静默合并。推荐要求已登录旧账号证明、一次性迁移流程或服务维护者审核；避免账号接管。
- 记录足以审计的关联事件/客户端 audience，但不记录 ID token 正文。

服务端签发的 Multica JWT（`server/internal/handler/auth.go:175-196`）已有 `sub/email/name/sid/exp/iat`，默认 30 天，可继续作为 Android 最终会话。`POST /api/auth/refresh`（`server/internal/handler/session.go:14-50`）明确支持 bearer 客户端，可复用其返回与 `renewed/check_again_in_seconds` 语义。

现有 `/auth/logout` 只清 cookie（`server/internal/handler/auth.go:797-800`），不会立即撤销已签发 bearer。故 P0 可承诺“退出此 App”：取消在途认证、清 Android 加密 token 与账号缓存、清 Credential Manager credential state、尽力调用服务端退出；若产品要求旧 bearer 立即失效或全设备退出，必须增加按 `sid` 撤销/服务端 session 管理范围。

## 3. Android debug/release 配置复核

| 项目 | 当前源码事实 | 最小整改/验收要求 |
|---|---|---|
| 包名 | `app/build.gradle.kts:13-18,28-39`：release `ai.multica.android`；debug 加 `.debug` | 用户确认最终 release application ID；测试 OAuth 项目登记 debug 包名与实际 debug 签名 SHA-1；release 登记实际发布/Play App Signing 证书，而非仅 upload 证书 |
| API/fixture | `:7-10,23-24`：默认 `https://api.example.invalid` 且 fixture=true | 以环境显式配置批准的测试 HTTPS；release 构建在 fixture=true 或 `.invalid` API 时 fail-fast，避免误发 fixture |
| Google 依赖 | `gradle/libs.versions.toml` 与 `app/build.gradle.kts:57-76` 无 Credential Manager / Google ID provider | 增加版本锁定依赖和单元/设备测试；不得引入客户端 secret |
| Google client ID | BuildConfig/resources 均无 | 按环境提供非秘密 server/Web client ID；它必须与后端精确 `aud` allowlist 一致，不能把 Android client ID 当 serverClientId |
| 回调 | Manifest 只有 launcher，无 App Link | Credential Manager 主路径无需回调 intent filter；仅当用户批准浏览器 broker fallback 时再设计独立 callback、域名与 `assetlinks.json` |
| 会话存储 | 已有 `AndroidKeystoreSessionStore` | 只持久化服务端返回的 Multica JWT；Google ID/access token 仅存内存、立即交换，不写日志/磁盘 |
| 环境隔离 | 无 flavor，release minify=false，仍为 `0.1.0-feasibility` | 至少把测试/发布 endpoint、audience、fixture 门禁显式分离；固定 Git SHA、APK hash、包名、签名指纹和后端 deploy SHA |

`MainActivity.kt:27-37` 当前只在 `LocalFixtureGateway` 与 `HttpMulticaGateway` 间选择；`ApiRoutes` 无 Google 路由。refresh 常量虽存在，尚不能据此声称完整续期链路已接通。

## 4. vivo / GMS / provider 条件

Credential Manager + Google ID provider 的可行性依赖设备实际能力，不以“Android 原生”或单一 vivo 型号自动成立。设备重连后先只读核验：

```bash
adb get-state
adb shell getprop ro.build.version.sdk
adb shell pm path com.google.android.gms
adb shell pm path com.google.android.gsf
adb shell pm path com.android.vending
```

随后在批准测试账号下验证：

1. Play Services 是否存在、启用且可更新；Google 账号能否添加；
2. Credential Manager 实际 provider 列表/系统 UI 是否可用；仅有 GMS 包不算通过；
3. provider 是否能返回与指定 server client ID、nonce 对应的 Google credential；
4. 目标地区/网络能否访问 Google 服务；覆盖无 provider、无 credential、取消、网络失败；
5. 不在 adb、截图或应用日志中输出账号、token、nonce 原值。

若 provider 不可用，不能静默降级 fixture。待用户在取得设备证据后选择：

- 投资浏览器 broker + 已验证 App Link fallback（新增后端事务、域名/HTTPS、OAuth redirect、`assetlinks.json`、签名与恢复流程，成本和攻击面更高）；或
- 明确该设备/地区暂不支持 Google 登录，并保留其他获批准的真实认证方式。

## 5. 推荐的最小安全实现路径

### G0：先补批准与证据，不改生产

锁定批准的非生产后端 URL 与 deploy SHA、Google Cloud/OAuth owner、测试 server/Web client ID、批准测试账号；确认 debug/release 包名与证书指纹；完成 vivo provider/网络实测。确认账号关联与退出范围。私钥、client secret、测试账号凭据只能走安全配置，不进入仓库或 issue 评论。

### G1：后端先行，保持 Web 契约不变

新增而非复用 `/auth/google`：

- `POST /auth/google/native/challenge`：生成高熵、短 TTL、一次性 `attempt_id + nonce`，限流并绑定环境/client。
- `POST /auth/google/native`：仅接 `{attempt_id, id_token}`；验证 Google JWK 签名、精确允许的 `aud`、`iss`、`exp` 与 nonce，原子消费 attempt，拒绝重放、过期和错配。
- 以 Google `sub` 绑定外部身份，再复用既有注册/邀请/白名单/禁用策略，返回现有 `{token,user}` Multica 会话结构。
- 补 wrong audience/issuer/nonce、过期、重放、并发消费、账号冲突、禁用/注册策略等负向测试；日志不得含 token 正文。

现有 Web `/auth/google {code,redirect_uri}` 保持兼容。任何生产迁移或部署须由服务 owner 单独审批。

### G2：Android 客户端

1. 先取服务端 challenge；把 nonce 传给 Credential Manager，`serverClientId` 使用服务端允许的 Web/server client ID。
2. Google 按钮走显式账号选择；妥善区分取消、无 provider、无 credential、网络和服务拒绝，不自动转 fixture。
3. ID token 仅在内存中立即送新 native endpoint；成功后只把 Multica JWT 写入现有 Keystore store。
4. 实现 refresh 的 single-flight、401 会话清理和账号/工作区 generation 隔离；logout 取消在途登录并阻止迟到响应重新落盘，再清 credential state 与本地缓存。

### G3：真实服务门禁

以显式批准 HTTPS、fixture=false 构建测试包；记录 Android/backend SHA、APK SHA256、环境、包名、签名指纹、OS/GMS/provider。用批准账号执行 BA 的 G01–G33，并证明 Web 与 Android `/api/me` 映射到同一 Multica 用户及真实工作区/权限。真实成功闭环不能由 mock/fixture 代替。

## 6. 需用户或服务维护者确认的依赖

| 决策/输入 | 推荐 | 代价与依赖 |
|---|---|---|
| 非生产环境与 OAuth owner | 提供批准的测试服务 URL/deploy SHA、项目 owner、测试 audience 与批准账号 | 服务 owner 部署新接口；OAuth owner 配置测试 client；不在评论中提供 secret |
| release 身份 | 确认 `ai.multica.android` 是否最终 ID，并核验 Play App Signing/release SHA-1 | 应用发布 owner 提供证书指纹即可，绝不提供私钥；debug 与 release 独立登记 |
| 既有 email 关联 | provider+sub 优先；既有用户需证明账号所有权/受控迁移 | 增加首次关联步骤，但避免静默 email 合并的接管风险 |
| 退出语义 | P0 为“退出此 App” | 若要求旧 bearer 立即失效/全设备退出，新增 `sid` 撤销、session UI/API 与回归范围 |
| vivo 无 provider | 先实测；不可用时在 broker fallback 与明确不支持间选择 | fallback 依赖可验证域名、App Link、后端事务与额外安全测试，不属于零成本客户端改动 |

## 7. 本轮结论

- **可行性：有条件通过。** Credential Manager 主路径在设备 provider 可用且新增安全后端契约后可实施；当前服务端接口不能直接承接 ID token。
- **当前状态：未实现、未联调、未产品验收。** Android 仍缺 Google 依赖/路由和真实配置；vivo provider、真实环境、账号/签名均待确认。
- **顺序约束：** 全量能力矩阵齐备并确认上述依赖后，再按“后端契约与身份迁移 → Android 接入 → 真实服务门禁”排整改批次。本轮不据认证专项自行删减 Web 能力，也不改生产认证配置或部署入口。

参考官方实施约束：Android Credential Manager Sign in with Google（serverClientId、nonce、显式按钮、`clearCredentialState`）、Google 后端 ID-token 校验（签名、aud/iss/exp、稳定 sub）、Android App Links 验证（仅 browser fallback）与 Android Keystore。详见 BA 审计中记录的官方来源与访问日期。

## 官方参考

- Android Developers, [Sign in with Google using Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- Android Developers, [Implement Sign in with Google using Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- Google Identity, [Authenticate with a backend server](https://developers.google.com/identity/sign-in/android/backend-auth)
- Android Developers, [Verify Android App Links](https://developer.android.com/training/app-links/verify-android-applinks)
- Android Developers, [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
