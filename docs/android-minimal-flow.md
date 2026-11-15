# Android 最小原生流程与验证边界

**实现基线：** `multica` main `9cb59baec1ea51d0745651db47eb4d67c025b8b7`（只读协议参考）
**范围：** 登录 → 工作区选择 → 任务列表 → 任务详情 → 评论/活动时间线只读浏览；不含产品写入、真实 OTP、App Links、发布身份或完整产品验收。

## 运行模式

默认 debug 构建是 **Local fixture**，界面顶部始终显示标签，不打开网络连接：

- 邮箱：`demo@multica.local`
- 本地验证码：`123456`
- `Populated`：2 个工作区、3 个任务；首个任务含 13 条混合时间线，第二个任务含空描述和空时间线；
- `Empty`：工作区任务为空；
- `Retry`：首次任务列表读取失败，重试后成功；首次时间线读取也独立失败一次，重试后成功；
- `Expired`：任务列表读取返回 401 语义，清除 token 并回到登录。

该夹具只证明原生状态转换、UI 状态、分页呈现和安全存储接线可复现，不证明服务端可达或协议联调成功。

只有取得获授权测试环境、账号和 workspace 后，才可显式构建连接版本：

```bash
./gradlew assembleDebug \
  -Pmultica.fixtureMode=false \
  -Pmultica.apiBaseUrl=https://AUTHORIZED_TEST_HOST \
  -Pmultica.googleServerClientId=PUBLIC_WEB_CLIENT_ID
```

URL 与 OAuth client ID 不是密钥；client secret、私钥、token、OTP、邮箱和发布凭据不得写入 Gradle 属性文件或仓库。生产示例 `https://api.multica.ai` 与参考源码中的 staging 值均只作静态核对，本轮未自动连接。

## 会话、导航与旧响应隔离

- 验证成功后才写 token；登出和已认证请求的 401 会立即清除；
- 启动时先解密 token，再以 `GET /api/me` 验证；
- 网络错误、超时、5xx 和 protocol error 保留 token，并显示可重试状态；
- 工作区、任务列表、详情和时间线都有对应 loading / empty / error / retry 表达；
- 每次请求、导航、工作区切换和登出都会推进 generation；只有当前 generation 的响应可以写 UI，因而旧 workspace 或已登出会话的迟到详情/时间线响应会被丢弃；
- 返回任务列表使用进入详情前的只读快照，不额外发起网络请求；切换工作区则重新读取工作区列表。

## 已实现的 HTTP 映射

| 操作 | 合同 |
|---|---|
| 发送验证码 | `POST /auth/send-code` `{email}` |
| 验证验证码 | `POST /auth/verify-code` `{email,code}` → `{token,user}` |
| 恢复用户 | `GET /api/me` |
| 工作区 | `GET /api/workspaces` |
| 任务列表 | `GET /api/issues` + `X-Workspace-Slug`；读取 `{issues,total}` 的 `issues` |
| 任务详情 | `GET /api/issues/:id` + `X-Workspace-Slug`；读取单个 `Issue` |
| 评论/活动 | `GET /api/issues/:id/timeline` + `X-Workspace-Slug`；读取完整、最旧优先的扁平数组 |

共享 header：`Authorization: Bearer`、`X-Client-Platform: mobile`、`X-Client-OS: android`、`X-Client-Version`、`X-Request-ID`。Issue id 作为 URL path segment 编码。

参考服务端已移除 timeline 服务端分页，所以客户端**不发送旧 cursor/page 参数**。适配器一次读取完整数组，界面每次本地展示 6 条（6 → 12 → 全部）；这是渐进呈现，不是服务端分页。评论按 `content`，活动按 `action` 展示；删除评论、actor fallback 与 `parent_id` 只读映射已覆盖。本轮没有真实 HTTP 调用记录。

## 安全存储选择

未采用已弃用的 `EncryptedSharedPreferences`。`AndroidKeystoreSessionStore` 为每次安装生成随机 AndroidKeyStore alias 及不可导出的 AES-256 key，以 AES/GCM/NoPadding 加密 token；每次写入由加密 provider 独立生成 12-byte IV（加密侧不传 caller-provided IV，解密侧才传已保存 IV），仅把 alias、IV 与密文写入应用私有且禁止备份的 SharedPreferences。登出/401 会同时删除 envelope 与对应 KeyStore entry；损坏密文、丢失/失效 key 也做同样清理，读取旧 envelope 时绝不会为缺失 key 静默新建替代 key。既有固定 alias `multica_session_aes_v1` 仅用于一次性解密迁移，成功提交随机 alias envelope 后即删除。所有操作通过 `Mutex` 串行。

JVM 用普通 AES key 验证 round-trip、篡改失败及同一明文两次写入的 IV/密文不同。平台入口 `./scripts/verify-keystore-device.sh` 只运行 `AndroidKeystoreSessionStoreTest`，覆盖 AndroidKeyStore token 写入、store 重建恢复、重复写入、清理与 key entry 删除；无 ready ADB 设备时明确失败而非计为通过。硬件支持、进程终止、系统升级和 key invalidation 仍需在目标真机矩阵复验。

## 验证方法与产品验收区别

本地工程门禁：

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./scripts/verify-build.sh
```

该入口固定要求 JDK 17，默认使用仓库内、已忽略的 `.gradle-verification` 作为隔离 Gradle 用户目录，并执行 `clean testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`，因此不会依赖 `~/.gradle/gradle.properties`。Linux x86-64 使用 AGP/Maven 的 AAPT2；Linux aarch64 必须在 SDK build-tools 中存在实际可执行的 aarch64 AAPT2，或通过 `MULTICA_AAPT2_PATH` 指定，脚本会先对候选文件计算 SHA-256 并与仓库 `config/aapt2-linux-aarch64.sha256` 的批准值比较；不匹配时不会执行候选文件，匹配后才运行 `aapt2 version` 并传入 `android.aapt2FromMavenOverride`。可复取来源固定为 SDK Manager 包 `build-tools;37.0.0`（`sdkmanager --sdk_root="$ANDROID_HOME" "build-tools;37.0.0"`），取回后仍必须通过批准散列；该 SDK 工具不随仓库分发。本轮不声明 macOS、Windows 或其他架构已验证。

骨架/切片验收只覆盖编译、JVM 状态机与合同测试、lint 和 debug APK 签名完整性。产品验收还需由 QA 在可用 AVD/设备上完成安装启动、浅深色、软键盘、TalkBack/字体缩放、触控目标、AndroidKeyStore 行为、进程恢复和 38 项矩阵，并在获授权账号上验证真实 OTP、HTTP/401/弱网。两者不可互相替代。
