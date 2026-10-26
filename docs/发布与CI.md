# 发布与 CI 说明（GlucoNote）

本文说明 GlucoNote 的仓库结构、自动化打包与发布流程。
**版本号规则、应用内更新机制、故障排查**见 [`版本与更新.md`](./版本与更新.md)。

源码在 `LuckyLearning/GlucoNote`，最终产物分发到 `LuckyLearning/Release`，打包由 GitHub Actions 完成，所有敏感密钥存放在 GitHub Secrets（不在仓库内）。

## 1. 仓库与分支约定

| 仓库 | 地址 | 用途 |
|---|---|---|
| 源码 | `https://github.com/LuckyLearning/GlucoNote` | 默认分支 `main`；日常只提交，发版时推送 |
| 分发 | `https://github.com/LuckyLearning/Release` | 静态文件分发：`GlucoNote/latest.json` + 每版本目录（无需鉴权） |

- 日常开发**只提交（commit）不推送**；发版时才推送 `main` 并打 `v*` tag 触发 CI。
- 分发仓库只留最近 **5 个版本**，用快照（orphan + force push）方式覆盖，体积恒定。

## 2. CI 流水线

文件：`.github/workflows/build-release.yml`

### 触发条件

| 事件 | 触发的 job | 效果 |
|---|---|---|
| push 到 `main` | `verify` | `assembleDebug` + `testDebugUnitTest`，只验证不出包 |
| push `v*` tag | `release` | 构建签名 APK → 发布到 `LuckyLearning/Release` |
| Actions 页手动 `workflow_dispatch` | `release` | 同上（需填 `version` 输入） |

### `verify` job（push main）

1. `actions/checkout@v4`
2. JDK 17（Temurin）
3. **Android SDK**：直接调运行器预装的 `sdkmanager` 安装 `platforms;android-34`、`build-tools;34.0.0` 并接受许可
   > 不用 `android-actions/setup-android@v3` —— 它会安装早已废弃的 `tools` 包而失败
4. `chmod +x gradlew`
5. `./gradlew assembleDebug testDebugUnitTest --no-daemon`

### `release` job（推 tag）

1. Checkout → JDK 17 → Android SDK（同上）
2. **tag ↔ `appVersionName` 一致性闸门**
   ```yaml
   VERSION="${GITHUB_REF_NAME#v}"
   FILE_VERSION=$(grep -E '^val appVersionName = ' app/build.gradle.kts | head -1 | grep -o '[0-9][0-9.]*')
   [[ "$VERSION" == "$FILE_VERSION" ]] || exit 1
   ```
   > 不一致直接 fail。否则发出去的包 `BuildConfig.VERSION_NAME` 仍是旧值，用户会陷入"永远提示有新版本"的循环。
3. 从 Secret `KEYSTORE_BASE64` 解码 keystore
4. `./gradlew clean assembleRelease --no-daemon`
5. 产物统一改名为 `GlucoNote-v<版本>.apk`
6. 生成 `/tmp/notes.md`（更新说明，进入 `latest.json` 的 `notes` 字段）
7. 调 `scripts/publish-release-repo.sh` 发布

## 3. 发版操作

```bash
bash scripts/release.sh patch    # 1.0.0 -> 1.0.1（也可 minor / major / set X.Y.Z）
```

脚本内部顺序（任一步失败即中止）：解析版本 → 检查工作区干净 → 检查 tag 未占用 → **先跑单测** → `sed` 回写版本 → 提交 → 打 tag → 推送 → REST 实查 tag 已到远端。

> 详细说明见 [`版本与更新.md` §3](./版本与更新.md#3-发版流程)。

## 4. 需要在 GitHub 设置的 Secrets

在 **GlucoNote 仓库 → Settings → Secrets and variables → Actions** 添加：

| Secret 名称 | 说明 |
|---|---|
| `KEYSTORE_BASE64` | 签名 keystore 的 base64 编码（`base64 -w0 your.keystore` 生成） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 签名别名（key alias） |
| `KEY_PASSWORD` | 别名对应私钥密码 |
| `RELEASE_TOKEN` | 对 `LuckyLearning/Release` 有写权限的 PAT，供发布脚本使用 |

> 以上密钥均由用户在 GitHub 后台配置，本地与仓库代码中**不出现任何明文密钥**。
> 发布脚本通过 `GIT_ASKPASS` 传递 token，不写进 remote URL、命令行或 git 配置。

## 5. 本地推送约定

工程根目录的 `.github-token`（已在 `.gitignore` 中忽略，绝不入库）存放 PAT。

**推荐做法**（token 不落盘到 `.git/config`）：

```bash
TOKEN=$(cat .github-token | tr -d '\r\n')
git -c credential.helper= \
    -c http.https://github.com/.extraheader="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$TOKEN" | base64 -w0)" \
    push origin main
git update-ref refs/remotes/origin/main $(git rev-parse main)
```

> 不要用 `git push -u https://<token>@github.com/...`：这会把 PAT 存进 `.git/config` 的 `branch.main.remote`。

## 6. 本地构建环境

| 项 | 值 |
|---|---|
| `JAVA_HOME` | `C:/Users/stu_l/.jdks/openjdk-21.0.2`（系统默认的 graalvm 会在 jlink 阶段失败） |
| `local.properties` | `sdk.dir=C:\esendev\code\temp\WindTone\.toolchain\android-sdk`（本机复用 WindTone 的 SDK，gitignored） |

**提交前必须本地全绿**：

```bash
export JAVA_HOME=/c/Users/stu_l/.jdks/openjdk-21.0.2
./gradlew assembleDebug testDebugUnitTest
```

> `--offline` 只在依赖缓存已就绪时可用；首次构建需联网拉插件与依赖。

## 7. 验证链（发版后）

1. `grep -nE 'appVersion(Name|Code) = ' app/build.gradle.kts` 确认版本已 bump
2. GitHub Actions 该次 `release` job 为 success
3. 公共仓库出现新版本目录 + `latest.json` 的 `version` 已指向新版本
   ```bash
   curl -fsSL "https://raw.githubusercontent.com/LuckyLearning/Release/main/GlucoNote/latest.json?nocache=$(date +%s)"
   ```
4. 应用内「我的 → 版本更新 → 检查更新」能收到新版本；下载后 SHA-256 校验通过、可正常安装

## 8. 待办

- [ ] 确认 `LuckyLearning/Release` 的 `main` 分支**未开启分支保护**（快照发布需要 force push）
- [ ] 配置上述 5 个 Secrets
- [ ] 打首个 tag（`bash scripts/release.sh set 1.0.0`）验证整条链路
- [ ] （可选）登记签名证书指纹（`keytool -list -v`），便于核对分发包来源
