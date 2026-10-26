# 发布与 CI 说明（GlucoNote）

本文说明 GlucoNote 的仓库结构与自动化打包/发布流程。源码在 `LuckyLearning/GlucoNote`，最终发布产物落在 `LuckyLearning/Release`，打包由 GitHub Actions 完成，所有敏感密钥存放在 GitHub Secrets（不在仓库内）。

## 1. 仓库与分支约定
- 源码仓库：`https://github.com/LuckyLearning/GlucoNote`
  - 默认分支：`main`
  - 日常只提交（commit）不推送；发版时推送 `main` 并打 `v*` tag 触发 CI。
- 发布仓库：`https://github.com/LuckyLearning/Release`
  - CI 在该仓库下以 `gluconote-<版本>` 为 tag 创建 Release，并上传 APK / AAB。

## 2. CI 流水线
文件路径：`.github/workflows/build-release.yml`

### 触发条件
- 推送 tag（形如 `v1.0.0`）→ 自动构建并发布。
- 在 Actions 页面手动 `workflow_dispatch`（填写 `version` 输入）触发。

### 流程
1. `actions/checkout@v4` 拉取源码。
2. 安装 JDK 17（Temurin）+ Android SDK。
3. 赋予 `gradlew` 执行权限。
4. 从 Secret `KEYSTORE_BASE64` 解码出签名 keystore 到工作区。
5. `./gradlew clean assembleRelease bundleRelease` 产出 APK 与 AAB（签名信息从环境变量读取：见第 3 节）。
6. `softprops/action-gh-release@v2` 在 `LuckyLearning/Release` 仓库创建 Release，上传 `app/build/outputs/apk/release/*.apk` 与 `app/build/outputs/bundle/release/*.aab`。

> 注意：工程需提交 Gradle Wrapper（`gradlew`、`gradle/wrapper/`）以保证 CI 可构建；App 模块 `app/build.gradle.kts` 需读取 `KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD` 环境变量完成签名配置。

## 3. 需要在 GitHub 设置的 Secrets
在 **GlucoNote 仓库 → Settings → Secrets and variables → Actions** 中添加：

| Secret 名称 | 说明 |
|---|---|
| `KEYSTORE_BASE64` | 签名 keystore 文件的 Base64 编码（`base64 -w0 your.keystore` 生成） |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 签名别名（key alias） |
| `KEY_PASSWORD` | 别名对应私钥密码 |
| `RELEASE_TOKEN` | 具备 `LuckyLearning/Release` 仓库写权限的 Personal Access Token（repo 范围），用于跨仓库发版 |

> 以上密钥均由用户在 GitHub 后台配置，本地与仓库代码中**不出现任何明文密钥**。

## 4. 本地推送约定（与 WindTone 一致）
日常本地提交使用工程内的 `.github-token`（已在 `.gitignore` 中忽略，绝不入库）。推送示例：

```bash
git remote add origin https://github.com/LuckyLearning/GlucoNote.git
git add -A
git commit -m "feat: ..."
git -c credential.helper= -c url."https://$(cat .github-token)@github.com".insteadOf="https://github.com" push origin main
```

发版时打 tag 触发 CI：

```bash
# 版本号随版本提升，例如 v1.0.0
git tag v1.0.0
git push origin v1.0.0   # 触发 build-release.yml
```

## 5. 验证链（发版后）
1. GitHub Actions 该次运行成功（build job green）。
2. `LuckyLearning/Release` 下出现 `gluconote-vX.Y.Z` Release，且 APK / AAB 资产齐全。
3. 下载 APK 安装验证可正常打开、记录与统计可用。

## 6. 待办
- [ ] 在 GitHub 创建 `LuckyLearning/GlucoNote` 与 `LuckyLearning/Release` 仓库。
- [ ] 配置上述 5 个 Secrets。
- [ ] App 工程代码实现后提交并打首个 tag 验证 CI。
- [ ] （可选）为 Release 产物计算并登记签名指纹，便于校验。
