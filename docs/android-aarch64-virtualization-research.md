# aarch64 Android 本地运行方案调研与 redroid 故障处置

日期：2026-11-15  
范围：仅验证 Android 运行环境；未修改产品代码，也不把容器启动等同于产品、KeyStore 或真机验收。

## 结论

当前 `redroid/redroid:12.0.0-latest` 在本机可稳定复现“容器仍运行，但 Android 未完成启动”。镜像虽标记为 `linux/arm64`，其 Android 用户空间并非纯 64 位：`ro.zygote=zygote64_32`，ABI 列表包含 `armeabi-v7a/armeabi`，且 system/vendor 同时包含 32/64 位运行库。QA 的独立诊断进一步取得了直接证据：`app_process32`、audio/media/OMX 等 ARM32 ELF 在宿主报 `Exec format error`，随后 AudioService 阻塞触发 Watchdog 杀死 `system_server`。本机 CPU 仅报告 64-bit op-mode；kernel 虽有 `CONFIG_COMPAT=y`，但它不能为不支持 AArch32 EL0 的 CPU 补出 ARM32 指令执行能力。因此首选不是继续补库，而是换成全链路 ARM64 userspace。

**推荐顺序：**

1. 使用官方 Android 12 ARM64-only 镜像，按 `linux/arm64` manifest digest 固定并验证；这是改动最小、最直接移除 ARM32 路径的方案。
2. 若镜像供应链或长期可复现性要求更高，从固定源码版本构建 `redroid_arm64_only-userdebug`。
3. 仅在 Android 12 64-only 仍无法启动时，评估较新 redroid 的对应 `*_64only` 镜像；升级必须另做 API、系统行为和 APK 回归。
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

最小复现使用临时 privileged 容器、随机 loopback ADB 端口，不挂载持久卷；退出后删除容器：

```bash
name="redroid-rd-$(date +%s)"
cid=""
cleanup() {
  adb disconnect '127.0.0.1:'"${port:-0}" >/dev/null 2>&1 || true
  test -z "$cid" || docker rm -f "$cid" >/dev/null 2>&1 || true
}
trap cleanup EXIT

cid=$(docker run -d --name "$name" --privileged \
  -p 127.0.0.1::5555 \
  redroid/redroid@sha256:52332b2d74f337982d5ac281a8020ec297fb1ea05cbdcdaaa9c19a2065ae1adc \
  androidboot.use_memfd=true)
port=$(docker port "$name" 5555/tcp | sed 's/.*://')
adb connect "127.0.0.1:$port"
```

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

## 首选方案：固定官方 ARM64-only 镜像

redroid 官方文档当前列出并在 quick start 使用 `redroid/redroid:12.0.0_64only-latest`，部署说明也明确指出部分 ARM 平台只支持 `aarch64`，此时需要 64-only 镜像。2026-11-15 查询到的 Docker Hub 元数据：

| 项目 | 固定值 |
|---|---|
| tag | `12.0.0_64only-latest` |
| multi-platform index digest | `sha256:a6c464bbedcf1dcb67dbf91f329fbb19bee5b50631f0ca6bda6ed7c41b0e64e2` |
| `linux/arm64` manifest digest | `sha256:3c7f9450188226bf8042c0159b9b38abe2106f1ceb0dfa45fae1cbc6409cd9bb` |
| arm64 压缩大小 | 605,791,428 bytes |
| Docker Hub 更新时间 | 2024-05-28T05:11:11Z |

### 可复制验证命令

不要直接运行浮动 `latest`；先验证 manifest 与架构，再按平台 manifest digest 拉取：

```bash
IMAGE='redroid/redroid@sha256:3c7f9450188226bf8042c0159b9b38abe2106f1ceb0dfa45fae1cbc6409cd9bb'
docker buildx imagetools inspect \
  redroid/redroid@sha256:a6c464bbedcf1dcb67dbf91f329fbb19bee5b50631f0ca6bda6ed7c41b0e64e2
docker pull --platform linux/arm64 "$IMAGE"
docker image inspect "$IMAGE" --format '{{.Architecture}} {{json .RepoDigests}}'

name="redroid-arm64only-$(date +%s)"
docker run -d --name "$name" --privileged \
  -p 127.0.0.1::5555 \
  "$IMAGE" androidboot.use_memfd=true
port=$(docker port "$name" 5555/tcp | sed 's/.*://')
adb connect "127.0.0.1:$port"
adb -s "127.0.0.1:$port" wait-for-device
```

随后执行“成功判据”一节。当前环境两次访问 `registry-1.docker.io` 均返回 `EOF`，因此只完成了 registry 元数据核验，**没有宣称该 64-only 镜像已拉取或启动成功**。网络恢复后应原样重跑；若平台 digest 拉取受客户端限制，可改用上表 index digest并保留 `--platform linux/arm64`，拉取后必须核对实际 RepoDigest/architecture。

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

## 成功判据

单次看到 ADB device 不算成功。以下条件必须全部满足：

```bash
serial="127.0.0.1:$port"
adb -s "$serial" wait-for-device
test "$(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r')" = 1
test "$(adb -s "$serial" shell getprop init.svc.bootanim | tr -d '\r')" = stopped
test "$(adb -s "$serial" shell getprop ro.zygote | tr -d '\r')" = zygote64
test "$(adb -s "$serial" shell getprop ro.product.cpu.abilist | tr -d '\r')" = arm64-v8a
test "$(adb -s "$serial" shell uname -m | tr -d '\r')" = aarch64
adb -s "$serial" shell pm list packages >/dev/null
```

还需人工/日志确认：

- 无 zygote、linker 或 system_server fatal restart loop；
- debug APK 可安装并启动；
- 删除任务容器后再次冷启动仍通过；
- ADB 只绑定 `127.0.0.1`，不对公网或局域网开放；
- KeyStore token 写入、恢复、清理和登录闭环另在可用设备上执行，不因 redroid boot 成功而记为通过。

若失败，在 ADB 尚在线时立即采集：

```bash
adb -s "$serial" logcat -b all -d > redroid-logcat.txt
adb -s "$serial" shell dmesg > redroid-guest-dmesg.txt || true
docker inspect "$name" > redroid-container-inspect.json
dmesg --ctime | tail -n 500 > redroid-host-dmesg-tail.txt
```

日志交付前应检查 token、账号、网络地址等敏感内容。

## 回退与清理

优先并存验证，不覆盖旧 tag，不删除宿主已有镜像。失败时只清理本次创建的资源：

```bash
adb disconnect "127.0.0.1:$port" || true
docker rm -f "$name"
```

若使用数据卷，必须给任务卷加唯一名称并确认归属后再删除。旧失败镜像仍按 digest 保留，便于回归和对照。切换到较新 redroid 后若出现回归，直接停止新容器并用已记录的 Android 12 64-only digest 重建，不移动浮动 tag。

## 本轮测试边界

| 项目 | 状态 |
|---|---|
| 宿主 arch/KVM/binder/COMPAT/页大小检查 | 已实测 |
| 现有 Android 12 混合 ABI 镜像静态检查 | 已实测 |
| 混合 ABI 镜像启动失败、ADB offline 复现与清理 | 已实测 |
| 官方 12 64-only tag、index/arm64 manifest digest 元数据 | 已核验 |
| 官方 12 64-only 镜像拉取与 boot complete | 未执行成功；Docker registry 返回 EOF |
| ARM32 compat 修补 | 未执行；已有 32 位 ELF `Exec format error` 与 Watchdog 日志，且 CPU 仅报告 64-bit op-mode，当前宿主不适合该路线 |
| Waydroid/Cuttlefish/QEMU/真机启动 | 未执行；需要额外镜像/host 套件或设备 |
| 产品 APK、KeyStore 与真实接口验收 | 不在本轮完成范围，保持待验证 |

## 上游依据

- redroid 文档与 quick start：https://github.com/remote-android/redroid-doc/blob/eba9a489990f3f558b7115f25fdc782d610ee8ad/README.md
- redroid 构建文档：https://github.com/remote-android/redroid-doc/tree/eba9a489990f3f558b7115f25fdc782d610ee8ad/android-builder-docker
- redroid local manifests：https://github.com/remote-android/local_manifests/tree/a8b841983cd28ee1b1b9c4a0d0b1b95a5a7b60f8
- redroid patches：https://github.com/remote-android/redroid-patches/tree/a5c70c0dd89c12b5027c902ab4182f31d38f3bb7
- Docker Hub tags/API：https://hub.docker.com/r/redroid/redroid/tags
