# Veyr Android 开发状态与路线图

最近核对：2026-11-19。本文记录 GitHub 仓库、构建产物和可复核的测试证据。

## 1. 项目基本信息

Veyr 是基于 NekoBox for Android、SagerNet 和 sing-box 的 Android 代理客户端。功能覆盖 Sub-Store 订阅、路由规则、DNS 分流和连接日志。[仓库 README](README.md) · [许可证](LICENSE)

| 路径 | 用途 |
| --- | --- |
| `app/` | Kotlin Android 客户端、UI、语言资源和 APK 打包 |
| `libcore/`、`hev-socks5-tunnel/` | Go 原生核心、sing-box 集成和 TUN 实现 |
| `buildSrc/`、`buildScript/`、`gradle/` | 构建约定、脚本与 Gradle Wrapper |
| `nb4a.properties` | 基础版本和包名；CI 对候选构建覆盖这些值 |
| `.github/workflows/` | 构建、产物校验和发布流程 |

GitHub 默认分支为 `lauritz/starifly-core-1.14`（`62731eb`），应用诊断开发分支为 `feature/app-diagnostics`（Beta.3 源码 `6720338`，包含已验证的 `396b98e`）。`main` 也是现存分支。核对时仓库没有可见的 Pull Requests 或 Issues；下文的问题来自实机反馈与代码核对。[分支](https://github.com/LauritzWu/Veyr/branches) · [Pull Requests](https://github.com/LauritzWu/Veyr/pulls) · [Issues](https://github.com/LauritzWu/Veyr/issues)

## 2. 当前版本与构建状态

| 渠道 | 显示版本 | Android versionCode | 构建与源码 |
| --- | --- | ---: | --- |
| GitHub 最新稳定版 | 1.0.4-STABLE | 25840 | ARM64，`c7b112b`，S24 Ultra 验收及发布校验通过 |
| GitHub 历史稳定版 | 1.0.3-STABLE | 25810 | ARM64，`5d4537f`，Release 构建，CI 与实机验证已通过 |
| Actions 前一稳定候选 | 1.0.3-STABLE | 25800 | ARM64，`539f754`，实测覆盖安装及配置保留通过，「关于」页显示 DEBUG |
| Actions 前一稳定候选 | 1.0.3 DEBUG | 25780 | ARM64，`83880d5`，繁中频道文案已通过实机验收 |
| Actions 前一稳定候选 | 1.0.3 DEBUG | 25770 | ARM64，`68de6ad`，CI 签名校验和实机覆盖安装通过 |
| GitHub 历史稳定版 | 1.0.2 | 25685 | ARM64，`dc73a2c` |
| GitHub 预发布版 | 1.0.3-beta.3 DEBUG | 25745 | ARM64，`6720338`，应用内升级已通过实机验证 |
| GitHub 历史预发布版 | 1.0.3-beta.2 DEBUG | 25715 | ARM64，`712445f`，applicationId 为 `top.lauritz.jc.uipreview.debug` |
| Actions 签名测试包 | 1.0.3-beta.2 DEBUG | 25725 | ARM64，`396b98e`，已通过实机回归 |

开发分支的 `nb4a.properties` 基础值为 `VERSION_NAME=1.0.1-beta.1`、`VERSION_CODE=46`。CI #43 覆盖版本与包名，再执行 `app:assembleOssDebug`。ARM64 APK 的版本号、签名与对齐均由 CI 校验。该流程使用 JDK 17、Android SDK Platform 35、Build Tools 35.0.1 和 NDK 25.0.8775105。实机“关于”页显示 sing-box `1.14.4-neko-1`。

## 3. 已实现功能清单

| 功能 | 当前证据 |
| --- | --- |
| 按应用查看近期连接、繁中诊断界面 | 代码已实现，25715 的 YouTube 和 Chrome Beta 用例已通过 |
| 按域名查看近期 DNS、规则命中与出站 | 稳定版发布说明已记录；25715 的独立域名诊断入口待专门回归 |
| 日志搜索和高亮 | 提交 `7bd0d1a` 已实现；25715 的专项测试待补 |
| 订阅、节点、DNS、路由规则 | README 与源码已有对应功能；覆盖安装已核对已有资料，订阅刷新及完整 DNS／规则矩阵待验证 |
| GitHub 稳定／Beta 更新检查 | 代码已实现；25715 复现识别问题，25725 已通过渠道识别与无降级提示回归 |

## 4. 已验证的测试结果

| 用例 | 版本 | 结果与证据 |
| --- | --- | --- |
| 繁中诊断与版本显示 | 25715 | 通过。截图显示繁中“关于”与“记录档”，版本为 1.0.3-beta.2 DEBUG |
| YouTube 应用归属与代理出站 | 25715 | 通过。应用包名 `com.google.android.youtube`，`i.ytimg.com`、`googlevideo.com` 相关流量显示经洛杉矶 VLESS 出站 |
| Chrome Beta 应用归属 | 25715 | 通过。日志归属 `com.android.chrome`，含 `example.com`；`example.org` 记录显示洛杉矶 VLESS 出站 |
| Wi-Fi ↔ 行动网络双向切换 | 25715 | 通过。维护者确认两次切换后均能打开网页，且有新连接日志 |
| 覆盖安装与资料保留 | 25685 → 25715 | 通过。维护者确认直接覆盖安装后，原有订阅、节点、路由规则均保留 |
| 已装新版本时检查稳定渠道 | 25715 | 通过该用例。界面显示已安装 25715、可用 25685、当前渠道没有更新 |
| Beta 渠道发现预发布版 | 25715 | 失败。维护者确认 Beta 也显示可用 25685；截图记录对应提示，源码核对确认 25715 会被筛掉 |
| 检查更新与无降级提示 | 25725 | 通过。维护者确认 Beta 显示可用 25715，稳定渠道显示可用 25685，两个渠道均显示没有更新 |
| Beta 渠道端到端升级 | 25725 → 25745 | 通过。维护者确认从应用内 Beta 渠道下载并覆盖安装，原有订阅、节点和路由规则均保留 |
| 稳定候选覆盖安装与更新检查 | 25745 → 25770 | 通过。订阅、节点和路由规则保留；稳定渠道可用 25685、Beta 渠道可用 25745，均提示没有更新 |
| 检查更新渠道识别 | 25745 | 通过。Beta 渠道识别 25745；稳定渠道显示已安装 25745、可用 25685 |
| 覆盖安装与应用诊断回归 | 25715 → 25725 | 通过。维护者确认订阅、节点及路由规则保留，YouTube 和 Chrome 的连接归属正常 |

## 5. 已知 Bug 与待修复问题

**P1 · Beta 更新渠道识别不到已发布的 25715（已修复并验收）。** `AboutFragment.kt` 仅接受以 `-preview.<build>` 或 `-stable.<build>` 结尾的标签，当前预发布标签为 `v1.0.3-beta.2.25715`。预发布 APK 的文件名也不在更新器的 ARM64 资产白名单中。两项筛选都会导致该发布被跳过。稳定渠道排除预发布版符合本次预期。修复已提交至诊断分支 [396b98e](https://github.com/LauritzWu/Veyr/commit/396b98ef2462ff1ce949dcc636486158857b79ed)，保留旧标签和 APK 名称，并按 Beta 标签识别版本对应的 ARM64 安装包。根据 GitHub Release 数据核对筛选结果，Beta 选中 25715，稳定渠道选中 25685。25725 已通过 Android 实机回归。25725 → 25745 的 Beta 渠道发现、下载和覆盖安装已通过实机验证。

- [x] 核对标签和资产命名约定，并提交兼容历史版本的修复。
- [x] 实机验证 25725 的 Beta／稳定渠道识别和无降级提示。
- [x] 实机验证 25725 能发现更高 versionCode 的 Beta（25745）。
- [x] 通过应用内 Beta 下载入口取得并安装 25745 ARM64 APK。

## 6. 待开发功能与优先级

| 优先级 | 尚未交付项 | 验收标准 |
| --- | --- | --- |
| P2 | 更新候选筛选的自动化回归 | 覆盖渠道、标签、资产名称和 versionCode 比较 |

## 7. 下一阶段开发路线图

- [x] 复现并修复更新候选筛选。
- [x] 用 25725 回归更新检查、应用诊断及覆盖安装。
- [x] 核对 25745 的 Beta 和稳定渠道识别结果。
- [x] 从 25725 的 Beta 更新提示下载并覆盖安装 25745，确认订阅、节点和路由规则保留。
- [ ] 补测域名诊断、日志搜索／高亮、清除日志后采集新连接，以及订阅刷新。
- [x] 构建 1.0.3 稳定候选 25770。CI 核对与 25745 相同的 applicationId 和签名证书，且 versionCode 高于 25745。
- [x] 实机验证 25745 → 25770 覆盖安装、配置保留与更新渠道。
- [x] 实机验收 25780 的繁中更新频道文案（「穩定版」和「Beta 版」）。
- [x] 构建显示 `1.0.3-STABLE` 的签名候选 25800。
- [x] 实机验证 25780 → 25800 覆盖安装、配置保留和 Android 系统版本显示。
- [x] 构建 25810 Release 候选，验证包名、签名、版本号及 APK 未开启调试。
- [x] 验证 25800 → 25810 覆盖安装。「关于」显示 `1.0.3-STABLE`、构建号 25810，无 `DEBUG`，配置保留，VPN 正常连接。
- [x] 将同一份已验收 APK 发布为 GitHub 最新稳定版。
- [x] 发布后实测：25810 的稳定版和 Beta 版更新频道均显示已安装 25810、可用 25810，无需更新。

## 8. GitHub Actions 与 Release 状态

| 工作流程 | 结果 | 发布 |
| --- | --- | --- |
| [1.0.3 正式发布](https://github.com/LauritzWu/Veyr/actions/runs/44576152224) | 发布包与 CI APK 的 SHA-256 一致；已验证为最新稳定版 | [v1.0.3-stable.25810](https://github.com/LauritzWu/Veyr/releases/tag/v1.0.3-stable.25810) |
| [CI #62](https://github.com/LauritzWu/Veyr/actions/runs/44573687065) | 成功。1.0.3-STABLE（25810），签名、包名、版本及非调试属性校验通过 | [Release 候选产物](https://github.com/LauritzWu/Veyr/actions/runs/44573687065/artifacts/14555717962) |
| [CI #60](https://github.com/LauritzWu/Veyr/actions/runs/44570837995) | 成功。1.0.3-STABLE（25800），ARM64 签名、包名和构建号校验通过 | [稳定候选产物](https://github.com/LauritzWu/Veyr/actions/runs/44570837995/artifacts/14555320309) |
| [CI #56](https://github.com/LauritzWu/Veyr/actions/runs/44566873866) | 成功。1.0.3（25780），ARM64 签名与对齐校验通过 | [文案更新候选产物](https://github.com/LauritzWu/Veyr/actions/runs/44566873866/artifacts/14552262272) |
| [CI #54](https://github.com/LauritzWu/Veyr/actions/runs/44563328107) | 成功。1.0.3（25770），包名、构建号、ARM64 签名与对齐校验通过 | [稳定候选产物](https://github.com/LauritzWu/Veyr/actions/runs/44563328107/artifacts/14551053416)，已通过实机覆盖安装 |
| [CI #49](https://github.com/LauritzWu/Veyr/actions/runs/44559517827) | 成功。1.0.3-beta.3（25745），通过 ARM64 签名与对齐校验 | [候选产物](https://github.com/LauritzWu/Veyr/actions/runs/44559517827/artifacts/14548422195) |
| [Beta.3 发布](https://github.com/LauritzWu/Veyr/actions/runs/44560975120) | 成功。发布 APK 的 SHA-256 与候选 APK 一致 | [v1.0.3-beta.3.25745](https://github.com/LauritzWu/Veyr/releases/tag/v1.0.3-beta.3.25745) |
| [CI #45](https://github.com/LauritzWu/Veyr/actions/runs/44556469063) | 成功。1.0.3-beta.2 DEBUG（25725），ARM64 签名与对齐验证通过 | [修复候选产物](https://github.com/LauritzWu/Veyr/actions/runs/44556469063/artifacts/14544419251)，已通过实机回归 |
| [CI #43](https://github.com/LauritzWu/Veyr/actions/runs/44546508392) | 成功。LibCore、OSS APK、ARM64 签名和对齐检查通过 | [原始候选产物](https://github.com/LauritzWu/Veyr/actions/runs/44546508392/artifacts/14540883341) |
| [Beta 发布](https://github.com/LauritzWu/Veyr/actions/runs/44554631626) | 成功。重新下载的 APK 与候选 APK 的 SHA-256 一致 | [v1.0.3-beta.2.25715](https://github.com/LauritzWu/Veyr/releases/tag/v1.0.3-beta.2.25715) |
| [稳定版发布](https://github.com/LauritzWu/Veyr/actions/runs/44541336712) | 成功 | [v1.0.2-stable.25685](https://github.com/LauritzWu/Veyr/releases/tag/v1.0.2-stable.25685) |

## 9. 最近开发记录及相关 Commit

| Commit | 内容 |
| --- | --- |
| [`83880d5`](https://github.com/LauritzWu/Veyr/commit/83880d519423cf9e2d3e439f741f3fa2124fcdb8) | 繁中更新频道缩短为「Beta 版」 |
| [`68de6ad`](https://github.com/LauritzWu/Veyr/commit/68de6adf9550314658e05335883a3a9a2ce81a8e) | 在 `release/1.0.3-candidate` 构建稳定候选，沿用已验收的 Beta.3 应用代码 |
| [`6720338`](https://github.com/LauritzWu/Veyr/commit/6720338fbf15473168d4d6255c0f4c6243df353f) | 配置 Beta.3 签名构建，通过同一版本变量生成校验值和产物名称 |
| [`396b98e`](https://github.com/LauritzWu/Veyr/commit/396b98ef2462ff1ce949dcc636486158857b79ed) | 修复 Beta 标签和对应 ARM64 APK 的筛选 |
| [`4158559`](https://github.com/LauritzWu/Veyr/commit/4158559d3a7a1a1e32d75bd2c5468649083a462e) | 在 release/beta-25715 增加预发布工作流程 |
| [`712445f`](https://github.com/LauritzWu/Veyr/commit/712445fa7f4ad8aae5ace0e2def9a605b644f0f7) | 配置 1.0.3-beta.2 签章构建 |
| [`bd480e1`](https://github.com/LauritzWu/Veyr/commit/bd480e1) | 更新路由与连接诊断的繁中资源 |
| [`4787083`](https://github.com/LauritzWu/Veyr/commit/4787083) | 添加按应用的连接诊断 |
| [`62731eb`](https://github.com/LauritzWu/Veyr/commit/62731eb177122d52a809ff2e72e8801aeef15921) | 默认分支恢复 README 致谢 |

## 10. 新 Work 对话交接与长期维护规则

1. 先读仓库默认分支根目录的 `DEVELOPMENT_STATUS.md`，再核对目标分支 HEAD、最近的 Actions，以及 GitHub 最新稳定版和预发布版。
2. 确认要修改的分支与 APK 的源码提交。已通过的应用连接诊断和覆盖安装用例作为回归基线。
3. 每次完成开发、修复 Bug、通过测试或发布版本时，同步更新本文。记录分支、Commit、Actions run、Release、versionCode、测试环境与结果。
4. 分开记录源码已实现、CI 验证、实机通过、失败及待验证。每项通过结果标明所测构建和证据，更新路线图与已知问题，保留相关历史链接。
5. 新 Work 对话可从这里开始：“请读取 LauritzWu/Veyr 默认分支的 DEVELOPMENT_STATUS.md，核对当前分支和发布状态，从最高优先级未完成项继续。”

## 发版自动化演练

- 独立分支 `ci/release-automation` 增加 Android 发布草稿工作流。它使用同一组输入生成版本显示名、发布标签和下载文件名。
- 用已发布的 25810 进行演练，校验 APK 包名、签名、ARM64、构建号、非调试属性及 SHA-256，并校验重新下载的草稿附件。最新稳定版仍为 25810。[演练工作流](https://github.com/LauritzWu/Veyr/actions/runs/44658007821)
- 依据应用更新规则和 GitHub Release 数据进行模拟：现有稳定和 Beta 渠道均选中 25810；加入模拟 Beta 25815 后，只有 Beta 渠道选中它。Android 更新器的 Kotlin 规则已接入 CI：[回归通过](https://github.com/LauritzWu/Veyr/actions/runs/44660823023)。覆盖草稿过滤、稳定／Beta 分流、ARM64 文件选择、历史预览格式及防降级。
- [x] 在独立分支让构建端与发版草稿复用版本、渠道和 Beta 轮次配置。
- [x] 将签名构建及草稿工作流改为手动触发。修改后普通提交仅运行更新回归，Beta 25820 继续保留为草稿。
- [x] 从默认分支单独移植更新规则、回归测试和手动发版工作流。[PR #1](https://github.com/LauritzWu/Veyr/pull/1) · [PR 回归通过](https://github.com/LauritzWu/Veyr/actions/runs/44661161037)
- [x] PR #1 的回归与完整 APK 构建通过，已合并至默认分支。GitHub Actions 页面已验证显示 Run workflow 入口。[手动工作流](https://github.com/LauritzWu/Veyr/actions/workflows/android-build-and-draft.yml)
- [x] 从默认分支手动构建 `1.0.4-STABLE`（25840）：ARM64 APK 签名、版本、非调试属性及草稿附件 SHA-256 校验通过。最新公开稳定版仍是 25810。[构建记录](https://github.com/LauritzWu/Veyr/actions/runs/44672030353) · [Release 草稿](https://github.com/LauritzWu/Veyr/releases/tag/untagged-661a8ff32ecba6875b92)
- [x] S24 Ultra 实机通过 25835 → 25840 覆盖安装。「關於」显示 `1.0.4-STABLE`、組建編號 25840；繁中界面正常，订阅、节点和路由规则保留，VPN 可连通。
- [x] 发布已验收的 25840 为 GitHub 最新稳定版，保留 `veyr-preview-arm64-v8a.apk`，附件 SHA-256 与草稿一致。[发布页](https://github.com/LauritzWu/Veyr/releases/tag/v1.0.4-stable.25840) · [发布校验](https://github.com/LauritzWu/Veyr/actions/runs/44673641147)
- [x] 一加 7T 实测升级到 `1.0.4-STABLE`（25840）。订阅、节点和路由规则均保留，VPN 可连通。
- [x] 一加 7T 实测从 25810 的「檢查更新 → 穩定版」发现 25840，并从更新提示下载 APK，完成覆盖安装。
- [x] 用真实签名 Beta（`1.0.4-BETA`，25820）验证从构建到草稿。APK 与草稿附件的 SHA-256 一致，最新公开稳定版仍为 25810。[工作流](https://github.com/LauritzWu/Veyr/actions/runs/44658581003)

## 下载入口与安装体验

- [x] README 提供最新稳定版 ARM64 APK 直链、当前公开 Beta APK 直链以及 Beta 发布列表。发布新 Beta 时同步更新 README 的固定版本直链。
- [x] 手动发版模板正文首行包含 ARM64 APK 和 SHA-256 下载链接。链接在 Release 正式发布后可用。
- [x] 应用内下载、校验包名／构建号／签名并打开 Android 安装确认页已在 [PR #2](https://github.com/LauritzWu/Veyr/pull/2) 实现。[完整 APK 构建](https://github.com/LauritzWu/Veyr/actions/runs/44663472610)与[频道回归](https://github.com/LauritzWu/Veyr/actions/runs/44663472469)通过。
- [x] 已从 PR #2 分支手动构建 `1.0.4-BETA`（25825，Beta.2）。签名验证和草稿附件哈希核对通过，草稿正文首行已生成 APK 与 SHA-256 链接。[构建记录](https://github.com/LauritzWu/Veyr/actions/runs/44664088544)
- [x] S24 Ultra 实机覆盖安装 25810 → 25825。显示 `1.0.4-BETA` 与构建号 25825，原有订阅、节点和路由规则均保留。
- [x] 已发布签名 Beta.3（25830）作为实测目标，与工作流草稿 APK 的 SHA-256 一致；稳定版仍为 25810。[预发布版](https://github.com/LauritzWu/Veyr/releases/tag/v1.0.4-beta.3.25830)
- [x] S24 Ultra 实机通过 25825 → 25830 应用内下载与覆盖安装。订阅、节点和路由规则均保留。
- [x] S24 Ultra 实测通过 25830 → 25835；确认「關於」、更新頻道及連線診斷恢复繁体中文。[Beta.4](https://github.com/LauritzWu/Veyr/releases/tag/v1.0.4-beta.4.25835)
- [x] PR #2 在签名构建、完整 APK 构建和更新频道回归通过后合并至默认分支。[合并提交](https://github.com/LauritzWu/Veyr/commit/ae2ddc435cfdb483a9019c2e992c8373caa58fbf)
- [ ] 增加清晰 APK 文件名兼容后，再迁移公开下载文件名。

## 下一轮 Beta 更新体验

- [x] [草稿 PR #3](https://github.com/LauritzWu/Veyr/pull/3) 增加可取消的下载百分比和验证阶段提示，区分下载、校验和打开系统安装器失败，提供重试和发布页入口。
- [x] 关键繁中更新、诊断和规则集资源加入 CI，检查缺失、重复及占位符。[回归构建](https://github.com/LauritzWu/Veyr/actions/runs/44675481432)与[完整 APK 构建](https://github.com/LauritzWu/Veyr/actions/runs/44675481337)通过。
- [x] 通过独立发布分支从 PR #3 构建 `1.0.5-BETA`（25845、Beta.1），签名和草稿附件 SHA-256 校验通过。[构建](https://github.com/LauritzWu/Veyr/actions/runs/44676275188) · [草稿](https://github.com/LauritzWu/Veyr/releases/tag/untagged-cc605e778be92410d787)
- [x] S24 Ultra 覆盖安装 `1.0.5-BETA`（25845）通过。「關於」显示正确版本和組建編號，订阅、节点和路由规则均保留。
- [ ] 在 25845 的 Beta 频道用更高构建号验证下载进度、取消、重试、失败提示和系统安装确认。
