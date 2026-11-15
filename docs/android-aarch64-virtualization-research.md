# aarch64 Android 本地运行方案调研与 redroid 故障处置

日期：2026-11-15  
范围：仅验证 Android 运行环境；未修改产品代码，也不把容器启动等同于产品、KeyStore 或真机验收。

## 结论

当前 `redroid/redroid:12.0.0-latest` 在本机可稳定复现“容器仍运行，但 Android 未完成启动”。镜像虽标记为 `linux/arm64`，其 Android 用户空间并非纯 64 位：`ro.zygote=zygote64_32`，ABI 列表包含 `armeabi-v7a/armeabi`，且 system/vendor 同时包含 32/64 位运行库。QA 的独立诊断进一步取得了直接证据：`app_process32`、audio/media/OMX 等 ARM32 ELF 在宿主报 `Exec format error`，随后 AudioService 阻塞触发 Watchdog 杀死 `system_server`。本机 CPU 仅报告 64-bit op-mode；kernel 虽有 `CONFIG_COMPAT=y`，但它不能为不支持 AArch32 EL0 的 CPU 补出 ARM32 指令执行能力。因此首选不是继续补库，而是换成全链路 ARM64 userspace。

**推荐顺序：**

1. 在本轮 aarch64 宿主上使用已固定 digest 的官方 Android 12 ARM64-only 镜像；它已通过两次真实冷启动门槛，可作为**该宿主上的 redroid 软件运行基线**，但不能外推为其他宿主、产品或真机验收结论。
2. 若镜像供应链或长期可复现性要求更高，从固定源码版本构建 `redroid_arm64_only-userdebug`。
3. 仅在该 Android 12 64-only 基线于目标宿主无法启动时，评估较新 redroid 的对应 `*_64only` 镜像；升级必须另做 API、系统行为和 APK 回归。
4. 修补 ARM32 compat 只作为定位手段，不作为默认交付路线。
5. 本地容器路线仍不通时，产品/KeyStore 验证优先转 ARM64 真机；Waydroid 或 Cuttlefish/QEMU-KVM 仅作为有额外运维成本的本地备选。

## 已验证事实

### 宿主条件

| 项目 | 实测值 | 判断 |
|---|---|---|
| CPU / kernel | `aarch64`, Linux `6.11.0-1016-nvidia` | ARM64 宿主 |
| 虚拟化 | 裸机，`/dev/kvm` 可用 | 可承接 KVM 方案 |
| binder | binderfs、`binder_linux` 已加载；`/dev/binder`、`/dev/hwbinder`、`/dev/vndbinder` 存在 | 满足 redroid 基本入口 |
| 32 位兼容 | kernel `CONFIG_COMPAT=y`，但 `lscpu` 仅列 `64-bit` op-mode，32 位 ELF 实测 `Exec format error` | 内核配置不等于 CPU 支持 AArch32 EL0；当前宿主不能直接执行镜像内 ARM32 程序 |
| 页大小 | ARM64 4 KiB page | 无已知 16/64 KiB 页兼容阻碍 |
| 工具 | Docker 29.6.2 (`linux/arm64`)、`adb`、`qemu-system-aarch64` | 可执行容器和 QEMU 诊断 |

### 失败镜像与复现结果

本机已有镜像：

- tag：`redroid/redroid:12.0.0-latest`
- index digest：`sha256:52332b2d74f337982d5ac281a8020ec297fb1ea05cbdcdaaa9c19a2065ae1adc`
- image ID：`sha256:8f35359fe35719c3b82036752f6ff6220e9549166b708b05d68bf0f14d441df5`
- OCI architecture：`arm64`
- 镜像创建时间：2024-05-27

静态检查结果：

```text
/system/build.prop: ro.product.cpu.abi=arm64-v8a
/vendor/build.prop: ro.zygote=zygote64_32
rootfs: 同时存在 lib/ 与 lib64/，含 32 位 ART/runtime 库
```

最小复现使用临时 privileged 容器、Docker 随机分配的 loopback ADB 端口且不挂载持久卷；本轮容器已按实际 cid 删除。该历史复现命令不再作为用户操作入口，避免弱容器名、无界等待和按名称误删；后续统一使用下文 `scripts/verify-redroid-arm64.sh`。

5–35 秒内观察值：

```text
sys.boot_completed = <empty>
ro.zygote = zygote64_32
ro.product.cpu.abilist = arm64-v8a,armeabi-v7a,armeabi
```

之后 ADB 变为 `offline`；30 秒时再次查询得到 `adb: device offline`。容器仍为 running，`docker logs` 为空。该复现已清理，没有改动宿主持久数据。

QA 在同一宿主的独立复验中，在 transport 尚可用时取得了更具体的失败链：

```text
/vendor/bin/hw/android.hardware.audio.service: Exec format error
/system/bin/mediaserver: Exec format error
Watchdog: *** WATCHDOG KILLING SYSTEM PROCESS: Blocked in handler on main thread
AudioProductStrategy.native_list_audio_product_strategies
AudioService.<init>
```

QA 还将 zygote/ABI 临时收窄到 ARM64 做诊断：主 zygote 与 `system_server` 能启动，但镜像里的 ARM32 audio/media/OMX HAL 仍失败，因此仅改 `ro.zygote` 或 ABI 属性不是修复。诊断镜像已删除。

## 已验证方向：固定官方 ARM64-only 镜像

redroid 官方文档当前列出并在 quick start 使用 `redroid/redroid:12.0.0_64only-latest`，部署说明也明确指出部分 ARM 平台只支持 `aarch64`，此时需要 64-only 镜像。2026-11-15 查询到的 Docker Hub 元数据：

| 项目 | 固定值 |
|---|---|
| tag | `12.0.0_64only-latest` |
| multi-platform index digest | `sha256:a6c464bbedcf1dcb67dbf91f329fbb19bee5b50631f0ca6bda6ed7c41b0e64e2` |
| `linux/arm64` manifest digest | `sha256:3c7f9450188226bf8042c0159b9b38abe2106f1ceb0dfa45fae1cbc6409cd9bb` |
| arm64 压缩大小 | 605,791,428 bytes |
| Docker Hub 更新时间 | 2024-05-28T05:11:11Z |

### 用户可执行验证脚本

不要直接运行浮动 `latest`。仓库提供 `scripts/verify-redroid-arm64.sh`，默认固定上表 `linux/arm64` manifest digest：

```bash
scripts/verify-redroid-arm64.sh --help
BOOT_TIMEOUT_SECONDS=240 COLD_BOOT_RUNS=2 \
  scripts/verify-redroid-arm64.sh
```

脚本的安全与可复现约束：

- `set -euo pipefail`，只接受 `redroid/redroid@sha256:<完整散列>`；拉取后核对 image architecture 为 `arm64`；
- 每次使用 UUID 派生的高熵 run id、容器名和 ownership label，ADB 由 Docker 随机映射到 `127.0.0.1`；
- 每轮 boot 等待默认最多 240 秒（允许 30–900 秒）；Docker `info`/`inspect`/`logs`/`run`/`rm`/`port`/`exec` 等操作统一受 `DOCKER_TIMEOUT_SECONDS` 限制（默认 30 秒，允许 1–300 秒），pull 单独受 `DOCKER_PULL_TIMEOUT_SECONDS` 限制（默认 900 秒，允许 1–3600 秒），ADB 与宿主诊断同样有界；
- 默认新建并删除两个容器完成两次冷启动；`docker run` 使用本轮私有 cidfile，若客户端超时则先从 cidfile、再从高熵容器名与 ownership label 恢复 daemon 实际创建的 cid；`trap` 只删除实际 cid 且 label 与本次 run id 一致的容器，无法在 Docker timeout 内核验归属、label 不匹配或删除超时时均显式失败而不误删；
- 失败时即使 ADB 为 offline，也会在各自 timeout 内尽力从 Docker 侧保存 `inspect`、logs、容器内 `getprop`、`logcat`、`dmesg`、`ps`，并保留 ADB 与宿主 dmesg 的可用结果；某项 Docker 取证阻塞只产生缺项，不会让脚本无界等待；
- 证据目录权限为 `0700` 且被 Git 忽略；内容可能含设备、package 或网络信息，分享前必须脱敏。

脚本使用 `--privileged`，只应在隔离的本地验证宿主执行；它不挂持久卷，也不公开 ADB。若平台 manifest digest 不能被客户端直接拉取，应暂停并核对客户端/registry 行为，不得静默改用浮动 tag。

### 2026-11-15 固定镜像双冷启动实测

QA 在上表同一宿主（`aarch64`、Linux `6.11.0-1016-nvidia`、Docker 29.6.2、4 KiB page、binder 设备可用）执行：

```bash
BOOT_TIMEOUT_SECONDS=240 COLD_BOOT_RUNS=2 \
DOCKER_TIMEOUT_SECONDS=30 DOCKER_PULL_TIMEOUT_SECONDS=900 \
  scripts/verify-redroid-arm64.sh
```

本次固定输入与结果：

| 项目 | 实测证据 |
|---|---|
| 日期 | 2026-11-15 |
| 固定镜像 | `redroid/redroid@sha256:3c7f9450188226bf8042c0159b9b38abe2106f1ceb0dfa45fae1cbc6409cd9bb` |
| 本地镜像 | QA 摘要记录 image ID 为 `sha256:c0966e…dacac`，OCI architecture=`arm64` |
| 冷启动 1 | 新容器通过 `boot_completed=1`、`bootanim=stopped`、`zygote64`、`arm64-v8a`、`uname=aarch64`、Package Manager 与 `system_server` 全部门槛 |
| 冷启动 2 | 删除首轮容器后以另一新 cid 再次通过同一组全部门槛 |
| 最终输出 | `PASS: 2 fresh-container cold boots passed.` |
| 补充检查 | 宽泛 fatal/restart 关键字扫描无命中；结束后本轮 ownership label 容器数为 0 |

因此，该固定 digest 已升格为**此宿主上的 redroid 软件运行基线**。它仍不证明用户目标宿主可复现，也不覆盖 APK、KeyStore、硬件 TEE/StrongBox、UI/可访问性或真实 HTTP；这些继续单列验收。

## 可复取源码构建

适合对供应链、调试符号或镜像生命周期有更强控制要求的场景。已核验上游引用：

- `remote-android/redroid-doc` `master`: `eba9a489990f3f558b7115f25fdc782d610ee8ad`
- `remote-android/local_manifests` `12.0.0`: `a8b841983cd28ee1b1b9c4a0d0b1b95a5a7b60f8`
- `remote-android/redroid-patches` `master`: `a5c70c0dd89c12b5027c902ab4182f31d38f3bb7`

上游流程如下；正式执行前需从 redroid 12 manifest/分支确认并固定准确的 AOSP Android 12 release tag，不应臆造 tag。clone 后还应 checkout 上述 commit 并记录 `.repo/manifest.xml`、patch tree 和最终镜像 digest：

```bash
mkdir redroid-12-arm64only && cd redroid-12-arm64only
repo init -u https://android.googlesource.com/platform/manifest \
  --git-lfs --depth=1 -b '<PINNED_ANDROID_12_RELEASE_TAG>'

git clone --branch 12.0.0 \
  https://github.com/remote-android/local_manifests.git .repo/local_manifests
(
  cd .repo/local_manifests
  git checkout a8b841983cd28ee1b1b9c4a0d0b1b95a5a7b60f8
)
repo sync -c

git clone https://github.com/remote-android/redroid-patches.git
(
  cd redroid-patches
  git checkout a5c70c0dd89c12b5027c902ab4182f31d38f3bb7
)
./redroid-patches/apply-patch.sh "$PWD"

. build/envsetup.sh
lunch redroid_arm64_only-userdebug
m
```

构建成本显著高于直接拉镜像：需要 AOSP 级磁盘、下载和编译时间；`userdebug` 还扩大调试接口，不能直接视作生产安全基线。构建产物必须按官方 image import 流程导入后计算 OCI digest，再执行同一组启动判据。

## 方案对比与风险

| 顺序 | 方案 | 兼容性 | 安全/运维风险 | 采用条件 |
|---:|---|---|---|---|
| 1 | redroid 12 `64only` 固定 digest | 与现有 Android 12 最接近；去除 32 位 ABI | privileged 容器权限高；ADB 必须仅监听 loopback；旧镜像需关注上游补丁状态 | registry 可取且全部成功判据通过 |
| 2 | 源码构建 `redroid_arm64_only-userdebug` | 可固定源码并调试；需严格对齐 manifest/patch | AOSP 供应链大、构建成本高；userdebug 不宜暴露 | 需要长期可复现、镜像不可取或需堆栈定位 |
| 3 | 较新 redroid `*_64only` | 可能改善新 kernel/驱动适配，也可能改变 API/系统行为 | 引入版本升级回归；仍有 privileged/ADB 风险 | 12 64-only 失败且完成版本回归 |
| 4 | 补齐 ARM32 compat | 当前 CPU 仅报告 64-bit op-mode，ARM32 ELF 已实测 `Exec format error`；只改 kernel config/linker/属性不可行。若硬件本身支持 AArch32 EL0，才继续核对 linker、ART、system/vendor 库和 ABI 配置 | 攻击面、镜像体积和 ABI 漂移增大；更换宿主硬件/内核的维护成本最高 | 只有明确业务必须运行 32 位 APK，且换到确认支持 AArch32 EL0 的宿主 |
| 5a | ARM64 真机 | 平台行为、KeyStore 最可信 | 设备管理、测试账号与数据隔离成本 | 容器路线失败，或进入 KeyStore/产品验收 |
| 5b | Waydroid | ARM64 Linux 可行性较高、复用 binder | 依赖图形 compositor/session，宿主集成强；隔离弱于 VM | 有受支持桌面会话且仅需本地交互验证 |
| 5c | Cuttlefish / QEMU-KVM ARM64 | VM 隔离强，宿主已有 KVM/QEMU | 镜像与 host package 支持需另核；网络、GPU、磁盘配置重 | 能取得明确支持 aarch64 host 的版本化 host/image 套件 |

不推荐 Anbox：项目已不再是优先维护路线。也不把“官方 Android Emulator”列为当前 aarch64 Linux 宿主首选，除非先取得 Google 对该 host architecture 的当前、明确支持证据；不能把 ARM64 guest system image 与 ARM64 host emulator 支持混为一谈。

## 成功判据与失败取证

单次看到 ADB `device` 不算成功。脚本在有界等待内要求：

- `sys.boot_completed=1`；
- `ro.zygote=zygote64`、`ro.product.cpu.abilist=arm64-v8a`、`uname -m=aarch64`；
- Package Manager 可列出 packages，且 `system_server` 存活；两项均为显式硬门槛，任一命令失败或超时都返回失败，绝不进入 PASS；
- `init.svc.bootanim` 为空（禁用动画时允许）或 `stopped`；其他值失败，不再强制要求该属性必须存在；
- 删除首个任务容器后，新建第二个容器并再次通过全部门槛。

自动门槛通过后仍需人工检查证据中无 zygote、linker 或 `system_server` fatal restart loop，并另行安装/启动固定 debug APK。KeyStore token 写入、恢复、清理和登录闭环仍需在可用设备执行；redroid boot 通过不能替代硬件 TEE/StrongBox 或产品验收。

ADB offline 时不能只依赖 `adb logcat`。脚本会先基于实际 cid 执行 Docker 侧 `inspect`/logs，并尝试容器内 `getprop`、`logcat -b all -d`、`dmesg` 和 `ps -A`；随后再保留有界 ADB 结果和宿主 dmesg（缺权限的错误也保留）。


## 清理与方案撤回（不是功能回退）

优先并存验证，不覆盖旧 tag，不删除宿主已有镜像。脚本的 EXIT/INT/TERM trap 依据实际 cid 和本次随机 ownership label 双重校验，只删除本次创建且归属匹配的容器；不创建或删除持久卷。归属不匹配、cid 畸形、Docker 核验或删除超时时，脚本安全失败并拒绝误删并发任务资源。

自动清理是**尽力清理**而非绝对无残留承诺：若 `docker run` 客户端超时，脚本先读取本轮 cidfile，再按本轮高熵精确容器名查询并核对 ownership label；但 daemon 若在这两次探测均结束后才完成创建，仍可能留下本轮容器。失败后应使用证据目录中的 run id 检查同名及同 label 资源，再经人工核对实际 cid 与 label 后清理，不能按模糊名称批量删除。

这里的“撤回”仅表示停止并删除候选容器。旧 mixed-ABI 镜像已知不能 boot complete，只能保留作故障对照，**不是可用功能回退**；registry 不可用且 64-only 镜像未缓存时，也不存在“按该 digest 立即重建”的回退能力。若后续升级到较新 redroid 出现回归，只有本机已缓存且此前完成两次冷启动门禁的固定 digest 才能作为运行基线恢复；否则转 ARM64 真机或其他已验证环境。

## 本轮测试边界

| 项目 | 状态 |
|---|---|
| 宿主 arch/KVM/binder/COMPAT/页大小检查 | 已实测 |
| 现有 Android 12 混合 ABI 镜像静态检查 | 已实测 |
| 混合 ABI 镜像启动失败、ADB offline 复现与清理 | 已实测 |
| 官方 12 64-only tag、index/arm64 manifest digest 元数据 | 已核验 |
| 官方 12 64-only 固定 digest 拉取与双冷启动 | 2026-11-15 已在本宿主实测通过；两次均通过全部硬门槛，结束后本轮 label 容器数为 0 |
| ARM32 compat 修补 | 未执行；已有 32 位 ELF `Exec format error` 与 Watchdog 日志，且 CPU 仅报告 64-bit op-mode，当前宿主不适合该路线 |
| Waydroid/Cuttlefish/QEMU/真机启动 | 未执行；需要额外镜像/host 套件或设备 |
| 产品 APK、KeyStore、UI/可访问性与真实接口验收 | redroid 软件基线通过不替代这些验收，保持待验证；硬件 TEE/StrongBox 需真机 |

## 上游依据

- redroid 文档与 quick start：https://github.com/remote-android/redroid-doc/blob/eba9a489990f3f558b7115f25fdc782d610ee8ad/README.md
- redroid 构建文档：https://github.com/remote-android/redroid-doc/tree/eba9a489990f3f558b7115f25fdc782d610ee8ad/android-builder-docker
- redroid local manifests：https://github.com/remote-android/local_manifests/tree/a8b841983cd28ee1b1b9c4a0d0b1b95a5a7b60f8
- redroid patches：https://github.com/remote-android/redroid-patches/tree/a5c70c0dd89c12b5027c902ab4182f31d38f3bb7
- Docker Hub tags/API：https://hub.docker.com/r/redroid/redroid/tags
