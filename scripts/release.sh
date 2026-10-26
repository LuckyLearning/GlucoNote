#!/usr/bin/env bash
# ===================================================================
#  GlucoNote 一键发版：单测通过后 bump 版本 → 提交 → 打 tag → 推送，
#  推送触发 CI（.github/workflows/build-release.yml 的 release job）
#  自动构建签名包并发布到公共分发仓库 LuckyLearning/Release。
#
#  用法：
#    bash scripts/release.sh patch   # 1.0.0 -> 1.0.1（默认）
#    bash scripts/release.sh minor   # 1.0.0 -> 1.1.0
#    bash scripts/release.sh major   # 1.0.0 -> 2.0.0
#    bash scripts/release.sh set 1.2.3
#
#  版本号单一来源：app/build.gradle.kts 顶部的
#    val appVersionName = "1.0.0"  /  val appVersionCode = 1
# ===================================================================
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

FILE="app/build.gradle.kts"
MODE="${1:-patch}"

# ---- 读当前版本 ----------------------------------------------------
cur="$(grep -E '^val appVersionName = ' "$FILE" | head -1 | grep -o '[0-9][0-9.]*')"
code="$(grep -E '^val appVersionCode = ' "$FILE" | head -1 | grep -o '[0-9][0-9]*')"
[[ -n "$cur" && -n "$code" ]] || { echo "✗ 无法从 $FILE 解析当前版本"; exit 1; }

# ---- 计算新版本 ----------------------------------------------------
if [[ "$MODE" == "set" ]]; then
  new="${2:?用法: release.sh set X.Y.Z}"
else
  IFS='.' read -r a b c <<< "$cur"
  case "$MODE" in
    major) new="$((a+1)).0.0" ;;
    minor) new="$a.$((b+1)).0" ;;
    patch) new="$a.$b.$((c+1))" ;;
    *) echo "✗ 未知模式: $MODE（可选 patch / minor / major / set X.Y.Z）"; exit 1 ;;
  esac
fi
newcode=$((code + 1))

echo "→ 发版: v$cur(code $code) -> v$new(code $newcode)"

# ---- 工作区必须干净 ------------------------------------------------
if [[ -n "$(git status --porcelain)" ]]; then
  echo "✗ 工作区有未提交改动，请先提交或 stash"; exit 1
fi
if git rev-parse -q --verify "refs/tags/v$new" >/dev/null; then
  echo "✗ tag v$new 已存在"; exit 1
fi

# ---- 构建环境（本机默认 JDK 不满足 Gradle 要求）--------------------
if [[ -d /c/Users/stu_l/.jdks/openjdk-21.0.2 ]]; then
  export JAVA_HOME=/c/Users/stu_l/.jdks/openjdk-21.0.2
fi

# ---- 先跑单测，再改版本（失败的代码不许发版）----------------------
echo "→ 运行单元测试 ..."
./gradlew testDebugUnitTest --offline -q || { echo "✗ 单测未通过，中止发版"; exit 1; }

# ---- bump 版本 ----------------------------------------------------
sed -i "s/^val appVersionName = .*/val appVersionName = \"$new\"/" "$FILE"
sed -i "s/^val appVersionCode = .*/val appVersionCode = $newcode/" "$FILE"
grep -nE 'appVersion(Name|Code) = ' "$FILE"

# ---- 本地先出一次签名包（R8 + 签名 + 产物命名全链路验证）------------
# 只把「本地构建过的代码」推上去，避免 CI 反复红。
if [[ -f .toolchain/keystore/keystore.properties && -f .toolchain/keystore/gluconote.jks ]]; then
  echo "→ 本地构建 Release 包 ..."
  _pw="$(grep STORE_PASSWORD .toolchain/keystore/keystore.properties | cut -d= -f2)"
  KEYSTORE_PATH="$PWD/.toolchain/keystore/gluconote.jks" \
  KEYSTORE_PASSWORD="$_pw" KEY_ALIAS=gluconote KEY_PASSWORD="$_pw" \
    ./gradlew assembleRelease --offline -q || { echo "✗ Release 构建失败，中止发版"; exit 1; }
  _apk="app/build/outputs/apk/release/GlucoNote-v$new.apk"
  if [[ -f "$_apk" ]]; then
    echo "  ✓ 产物：$_apk（$(du -h "$_apk" | cut -f1)）"
  else
    echo "✗ 没有产出预期命名的 APK：$_apk"; ls app/build/outputs/apk/release/; exit 1
  fi
else
  echo "  跳过本地构建（未找到 .toolchain/keystore）—— 依赖 CI 构建"
fi

# ---- 提交 + tag + 推送 --------------------------------------------
git add "$FILE"
git commit -m "release: v$new"
git tag "v$new"
echo "→ 推送 main 与 tag v$new ..."
# origin 刻意不存凭据（防止 token 落进 .git/config）；有 .github-token 就一次性授权
if [[ -f .github-token ]]; then
  _tk="$(tr -d '\r\n' < .github-token)"
  git -c credential.helper= \
      -c http.https://github.com/.extraheader="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$_tk" | base64 -w0)" \
      push origin main "v$new"
else
  git push origin main "v$new"
fi
git update-ref refs/remotes/origin/main "$(git rev-parse main)"

# ---- 推后实查：tag 必须真的在远端 ---------------------------------
owner_repo="$(git remote get-url origin | sed -E 's#.*github.com[:/]##; s#\.git$##')"
for attempt in 1 2 3; do
  status="$(curl -fsS -o /dev/null -w '%{http_code}' "https://api.github.com/repos/$owner_repo/git/ref/tags/v$new" 2>/dev/null || echo 000)"
  if [[ "$status" == "200" ]]; then
    echo "✓ tag v$new 已推送。CI 正在构建，发布产物见 https://github.com/LuckyLearning/Release/tree/main/GlucoNote"
    echo "  验证链：1) Actions 构建成功  2) Release 仓库出现 v$new 目录与 latest.json  3) 应用内「检查更新」能收到 v$new"
    exit 0
  fi
  sleep $((attempt * 3))
done
echo "✗ tag v$new 未在远端出现，请检查推送结果" >&2
exit 1
