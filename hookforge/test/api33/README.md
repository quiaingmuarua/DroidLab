# API 33 detect-env Frida 验证

Frida CI 在 rootable AOSP API 33 模拟器中运行四个用例，使用固定测试 Release 中的 `com.detect.app.sample`。
DroidLab 公共 CI 使用 x86_64；本次整合不迁入 macOS runner，也不声明 ARM64 CI 覆盖。
行为断言和清理均成功才返回 `0`，失败返回非零并保存证据。

`prepare.py`、`suite.py`、`run.py`、`report.py` 和 RootBeer runner 接受
`--abi x86_64|arm64-v8a`；省略时保持原来的 x86_64 默认。schema-2
`toolchain.lock.json` 分别锁定两种 server 的二进制/压缩包 SHA256，并映射
Android ABI、Frida server asset 架构和实际进程架构。ARM64 不能复用 x86_64
server 或 APK；工具在注入前检查选择的 ABI，证据记录实际设备与进程架构。

这四项属于**运行机制回归**。这里直接替换测试 App Java/JNI 方法，只用于验证
拦截、RPC 和恢复，不能计入公共 API 对原检测的影响证明。
统一验收含义见 [测试策略](../../docs/TESTING_STRATEGY.md#two-proof-scopes)，
干预边界见 [Detection Intervention Contract](../../AGENTS.md#detection-intervention-contract)。

| Host | 模式 | 验证内容 |
| --- | --- | --- |
| Python 探针 | attach、spawn | libc Native、JNI、Java Hook 的前后对照、RPC、异常传播、恢复和清理 |
| 正式 Node CLI | attach、spawn | 主机任务产出的同一份生产 bundle/CLI、loader-ready、指定模块激活、清理；spawn 开启 verbose |

CI 在这四项通过后，使用同一模拟器运行 RootBeer＋detect-env 真实界面对照。
公共 API bundle 来自 Host job 的生产 artifact；案例不会重新构建公共 hook。
RootBeer 样本来自固定测试 Release，下载步骤仅使用只读 token，并按案例声明校验 SHA256。
这部分单独标为**公共 API 行为验证**，API 命中、配置值变化、原检测结果和卸载后恢复分别保留证据。

| 探针 | 调用链 | 必须观测到的结果 |
| --- | --- | --- |
| Native Hook | RPC → `NativeFunction` → `libc.so!access` → `Interceptor` | `0 → -1 → 0`，一次 Hook 回调 |
| RPC | Python → Frida JS exports | JSON/中文/null 原样往返；异步求和 `42`；JS 和 Java 回调异常传回主机 |
| JNI Native Hook | RPC → Java → `FileHelper.check_file_exist_c` → `libdetect_sample.so` | `1 → 0 → 1`，一次 native 回调 |
| Java Hook | RPC → `FileHelper.fileExistResult(String)` | `true → false → true`，一次 Java 回调 |

探针只修改测试线程自身对 `/proc/self/status` 的调用结果，不修改文件。完成后撤销 Hook、卸载脚本、detach 并移除本轮 ADB 转发；本地保留模拟器、server 和应用，方便继续实验。

每个用例只由当前 host 建立 Frida 连接。Python 用例在 detach 后显式移除 remote device；Node 用例由正式 CLI 独占连接，准备阶段通过 ADB 校验 server 版本和哈希。

Java agent 显式导入仓库的 Java Bridge。Frida 17 的 `create_script()` 不保证存在全局 `Java`。Java agent 与生产 loader 共用 `initializeJavaClassLoader`，先通过 `Java.performNow` 和框架类获取应用 ClassLoader，再执行 `Java.perform`。调用 `FileHelper` 的现有静态方法会完成原生库初始化；共享 facade 将回调同步异常转为 Promise rejection。

## 环境准备

以下命令从 **DroidLab 根目录** 执行。使用 `hookforge/.node-version` 指定的
Node 22.19.0、Python 3.10+ 和 Android SDK；Actions 固定 Python 3.11。
`ANDROID_HOME`、`JAVA_HOME` 和 PATH 应指向相应工具。宿主检查与生产构建由根 Gradle 编排：

```bash
./gradlew -Pandroid=false :hookforge:check :hookforge:verifyProductionArtifact
python3 hookforge/test/api33/prepare.py \
  --tools-root .runtime/toolchain/python \
  --output .runtime/frida-api33/toolchain.json
```

Windows PowerShell 使用 `./gradlew.bat` 和 `python`。prepare 报告中的 `python`
字段提供所创建的匹配 Frida client 解释器；`server` 字段指向已校验的 Android server。
模块内默认输出仍在 `hookforge/.runtime`，上述显式参数把本轮工具与证据放在根 `.runtime`。

`prepare.py` 从模块 npm 锁文件读取 Frida 精确版本，创建 Python venv，安装匹配
client，并下载/校验 server。server 与压缩包 SHA256 来自 `toolchain.lock.json`。
`--check-only` 只校验已有环境；损坏缓存不能作为回退。可用 `--python`、`--node`、
`--server` 指定绝对路径；解释器保留 venv 入口，不解析成系统 Python。

`:hookforge:npmCi` 保留 postinstall，对 `frida-java-bridge@7.0.13` 应用已审阅补丁。
metadata 固定官方 npm tarball、原始及修改后字节哈希；补丁不引入固定等待或重试。
`npm --prefix hookforge run check:bridge-patch` 可单独校验。
production manifest schema 2 的 `commit` 与 API33 schema 3 的 `checkoutCommit`
均指向 **DroidLab 提交**。模块文件仍相对 `hookforge/`，不要求 `hookforge/.git`。
补丁、锁文件和实际安装字节必须一致；更换后需重装、重建、重新记录证据。

### 固定测试 APK

以下下载保留原公开 HookForge Release 的固定坐标及历史构建来源，不上传或重发资产。
完整身份和哈希以 [fixture.json](fixture.json) 为准。下载固定 APK 无须构建 Android labs：

```bash
mkdir -p .runtime/fixtures
gh release download fixtures-detect-env-ee69bb3 --repo quiaingmuarua/HookForge \
  --pattern detect-env-api33-x86_64.apk --output .runtime/fixtures/detect-env-api33-x86_64.apk
node hookforge/scripts/fixture.mjs .runtime/fixtures/detect-env-api33-x86_64.apk
```

当前 `labs/` 源码构建出的 APK 是另一个验证输入，保留自己的构建与安装哈希，不能改写
固定 fixture 的历史元数据。使用同一份生产 bundle 比较机制/行为，不能把单项集成
结果写成完整四组合 suite 通过。

## 复跑

先准备已连接的 rootable API33 x86_64 模拟器。Linux 可安装相应 SDK image；
Windows 使用原生 SDK 和现有 AVD，不使用 WSL。四项完整机制回归在 Bash 中执行：

```bash
.runtime/toolchain/python/venv/bin/python hookforge/test/api33/suite.py \
  --toolchain .runtime/frida-api33/toolchain.json \
  --production-manifest .runtime/hookforge/production-manifest.json \
  --apk .runtime/fixtures/detect-env-api33-x86_64.apk \
  --serial emulator-5554 --output .runtime/frida-api33/results/my-new-run
python3 hookforge/test/api33/report.py \
  --output .runtime/frida-api33/results/my-new-run
```

PowerShell 可从 JSON 取得解释器：

```powershell
$fridaTools = Get-Content .runtime/frida-api33/toolchain.json -Raw | ConvertFrom-Json
& $fridaTools.python hookforge/test/api33/suite.py `
  --toolchain .runtime/frida-api33/toolchain.json `
  --production-manifest .runtime/hookforge/production-manifest.json `
  --apk .runtime/fixtures/detect-env-api33-x86_64.apk `
  --serial emulator-5554 --output .runtime/frida-api33/results/my-new-run
python hookforge/test/api33/report.py --output .runtime/frida-api33/results/my-new-run
```

每次使用全新结果目录。上面的 base-only 报告仅声明运行机制；完整公共行为验收还需
在同一轮运行 RootBeer＋detect-env case，并向报告传 `--case-result`。RootBeer的
npm案例对相对路径采用进程当前目录，根目录调用时应给 APK、agent、manifest、output
传绝对路径。参见 [RootBeer](../../cases/rootbeer/README.md)。

单项正式 Node 用例可使用准备报告里的 Python 解释器运行：

```bash
.runtime/toolchain/python/venv/bin/python hookforge/test/api33/run.py \
  --serial emulator-5554 --host node --mode spawn \
  --apk /absolute/path/detect-env-debug.apk \
  --server /absolute/path/frida-server \
  --production-manifest .runtime/hookforge/production-manifest.json \
  --output .runtime/frida-api33/results/my-new-single-case
```

API33 安装器使用 `adb install -r -t`，支持固定 testOnly APK。其它 case 可能只用 `-r`，
应遵守各 case 的 APK要求。正式Node用例校验 `hookforge/dist` 生产字节、DroidLab提交、
Node版本和依赖锁哈希，开发hosts仍在 `hookforge/hosts/node-cli`。

模块保留 `emulator.py start|stop` 和 `--abi arm64-v8a`，便于明确准备过的手动环境。
模拟器工具按归属记录清理自己的进程/AVD，要求硬件加速，不以 `-accel off` 回退。
本轮没有公共macOS/ARM64 lane；历史外部runner范围见 [macOS说明](../../docs/MACOS_RUNNER.md)。

## 失败条件和证据

`run.py` 拒绝真实设备、非 API 33、与所选 ABI 不符、未完成启动的设备，以及 client/server/锁文件不匹配的环境。版本和 ABI 在注入前校验。只有 server 初始化连接进行有界重试；Hook/RPC 断言不会重试。

Node smoke 必须收到 `loader-ready`，并满足重复传入的 `--require-module ID` / `--require-recipe ID`。超时、模块失败、结构化运行时错误、loader/script error、意外 detach 和卸载/detach 失败均返回非零；verbose 仍处理全部消息。`--result FILE` 保存结果。

受控负向实验可向 Python 用例传入 `--native-agent /absolute/path/probe.js`，例如仅将探针替换返回值从 `-1` 改为 `-2`，确认断言失败且清理仍完成。失败结果会保存实际的 before/hooked/restored 值。CI 默认使用仓库原始探针。

每个用例保存：

- `result.json`：失败阶段、环境、APK/server/脚本哈希、行为检查和清理结果。
- `messages.jsonl`、`commands.jsonl`、`logcat.txt`：消息、主机命令和设备日志。
- Python 用例保存探针、Java bundle 和共享 facade 快照；Node 用例保存 `node-result.json`，包含 loader/module 状态、消息和清理。
- `installed-detect-env.apk`：从设备取回并核对 SHA256 的实际 APK。

suite 不覆盖旧结果；一项失败仍保留其他独立用例的结果，最终任一失败都返回非零。
`summary.json` 保存四项机制回归，报告生成的 `summary.md` 同时显示两类证明。
`job-result.json` 保留原有 suite、rootbeer 和步骤失败，并包含镜像 revision、emulator/adb 版本。

完整 CI 的案例结果位于本次结果根目录的 `rootbeer/result.json`。
汇总命令追加 `--case-result <该文件>`，要求生产产物模式、detect-env 复用和案例 Host 判定均通过。
结果缺失、损坏或验证失败时，`job-result.json.rootbeer` 记录原因，报告返回非零。
新增的 `proofs.runtimeMechanisms` 记录机制状态和 `summary.json` 路径；
`proofs.sharedApiBehavior` 记录行为状态、案例路径及公共脚本哈希，均为已有结果的汇总投影。
Actions Summary 分开展示两类证明和案例对照。只验证基础运行环境时可省略该参数，
公共行为状态明确为 `not-requested`，不能把该次成功解释为检测干预验证通过。

手动使用自管模拟器时，在汇总前停止本轮模拟器，并传
`--cleanup-result <emulator/cleanup.json>`；缺失、损坏或失败的清理证据会使报告失败。
每轮结果目录必须全新；只有证据上传与模拟器清理都成功，workflow 才删除带有匹配
run ID/attempt 归属标记的临时目录。初始化、上传或资源清理失败时保留可用证据。

源码和文档保存在 Git；生成证据位于 `.runtime`。Actions 使用 `if: always()` 上传已有证据，名称包含 run ID/attempt，保留 7 天。上传包保留一个构建 APK，各用例安装副本用哈希对应。CI 覆盖基础 Frida 行为以及固定 RootBeer 和 detect-env 的公共 API 对照；其它第三方 App、真实 ARM 设备及 XA Mass Worker 业务仍使用各自验收流程。
