# Android 最小原生流程与验证边界

**实现基线：** `multica` main `9cb59baec1ea51d0745651db47eb4d67c025b8b7`（只读协议参考）
**范围：** 登录 → 工作区选择 → 任务列表；不含产品写入、真实 OTP、App Links、发布身份或完整产品验收。

## 运行模式

默认 debug 构建是 **Local fixture**，界面顶部始终显示标签，不打开网络连接：

- 邮箱：`demo@multica.local`
- 本地验证码：`123456`
- `Populated`：2 个工作区，3 个任务；
- `Empty`：工作区任务为空；
- `Retry`：首次任务读取失败，点击 Retry 后成功；
- `Expired`：任务读取返回 401 语义，清除 token 并回到登录。

该夹具只证明原生状态转换、UI 状态和安全存储接线可复现，不证明服务端可达或协议联调成功。

只有取得获授权测试环境和账号后，才可显式构建连接版本：

```bash
./gradlew assembleDebug \
  -Pmultica.fixtureMode=false \
  -Pmultica.apiBaseUrl=https://AUTHORIZED_TEST_HOST
```

URL 不是密钥；token、OTP、邮箱和发布凭据不得写入 Gradle 属性文件或仓库。生产示例 `https://api.multica.ai` 与源码中的 staging 值均只作静态核对，本轮未自动连接。

## 会话与失败边界

- 验证成功后才写 token；登出和已认证请求的 401 会立即清除；
- 启动时先解密 token，再以 `GET /api/me` 验证；
- 网络错误、超时、5xx 和 protocol error 保留 token，显示可重试恢复页；
- 工作区读取和任务读取均有独立 loading / empty / error / retry 状态；
- 当前最小 transport 固化 30 秒超时、Bearer、workspace slug 和 Android client headers；没有业务写入；
- refresh token、WS、前后台重连、分页和完整 issue 字段不在本切片。

## 安全存储选择

未采用已弃用的 `EncryptedSharedPreferences`。`AndroidKeystoreSessionStore` 生成 AndroidKeyStore 内不可导出的 AES-256 key，以 AES/GCM/NoPadding 加密 token，仅把随机 IV 与密文写入应用私有 SharedPreferences；manifest 同时禁用备份。所有读取、写入与清除通过 `Mutex` 串行，损坏密文、丢失/失效 key 会清除本地 envelope，而不会返回或记录 token。

JVM 用普通 AES key 验证 round-trip 与篡改失败；AndroidKeyStore 是否硬件支持、进程重启、系统升级和 key invalidation 仍必须在模拟器/真机复验。

## 已实现的 HTTP 映射

| 操作 | 合同 |
|---|---|
| 发送验证码 | `POST /auth/send-code` `{email}` |
| 验证验证码 | `POST /auth/verify-code` `{email,code}` → `{token,user}` |
| 恢复用户 | `GET /api/me` |
| 工作区 | `GET /api/workspaces` |
| 任务 | `GET /api/issues` + `X-Workspace-Slug`；读取 `{issues,total}` 的 `issues` |

共享 header：`Authorization: Bearer`、`X-Client-Platform: mobile`、`X-Client-OS: android`、`X-Client-Version`、`X-Request-ID`。本轮没有真实环境调用记录。

## 验证方法与产品验收区别

本地工程门禁：

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug
```

骨架/切片验收只覆盖编译、JVM 状态机与合同测试、lint 和 debug APK 签名完整性。产品验收还需由 QA 在可用 AVD/设备上完成安装启动、浅深色、软键盘、TalkBack/字体缩放、触控目标、进程恢复和 38 项矩阵，并在获授权账号上验证真实 OTP/401/弱网。二者不可互相替代。
