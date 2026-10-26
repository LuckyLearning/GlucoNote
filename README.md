# 血糖随手记 (GlucoNote)

Android 原生 App（Kotlin + Jetpack Compose + Room），本地记录每日血糖数据并做统计分析、趋势与达标率，支持 CSV 导出/导入备份。

- **源码仓库**：https://github.com/LuckyLearning/GlucoNote
- **发布仓库**：https://github.com/LuckyLearning/Release/tree/main/GlucoNote
- **构建与发布**：GitHub Actions，见 [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml)

## 功能

- **记录**：5 个快捷标签（空腹 / 早餐后 / 午餐后 / 晚餐后 / 睡前），测量时间可改，数值 1.0–50.0 之外二次确认
- **统计**：趋势图（日/周/月/年）、按标签达标率环图、年/月/日区间分桶分析、近 7/30 天区间汇总
- **数据**：CSV 导出 / 导入备份
- **更新**：应用内检查更新（「我的 → 版本更新」）

## 文档（详见 [`docs/`](docs/)）

| 文档 | 内容 |
|---|---|
| [产品需求文档 (PRD)](docs/血糖随手记-PRD.md) | 需求、数据模型、验收标准 |
| [技术方案设计](docs/血糖随手记-设计方案.md) | 架构、包结构、数据层、图表实现 |
| [版本与更新](docs/版本与更新.md) | 版本号规则、发版流程、分发仓库、应用内更新机制 |
| [发布与 CI 说明](docs/发布与CI.md) | CI 流水线、Secrets、本地构建环境、验证链 |
| [UI 原型（可交互 HTML）](docs/ui-prototype.html) | 4 屏原型演示 |

## 开发

```bash
# 本地必须指定 JDK（系统默认的 graalvm 会在 jlink 阶段失败）
export JAVA_HOME=/c/Users/stu_l/.jdks/openjdk-21.0.2

# 提交前必须本地全绿
./gradlew assembleDebug testDebugUnitTest
```

`local.properties` 里的 `sdk.dir` 指向本机 Android SDK（gitignored，不入库）。

## 发版

```bash
bash scripts/release.sh patch    # 也可 minor / major / set X.Y.Z
```

脚本会先跑单测，通过后才 bump 版本、打 tag 并推送触发 CI。详见 [`docs/版本与更新.md`](docs/版本与更新.md)。
