# ktlint 检查与性能

## 常用入口

本地按改动选择 source set，无需安装 npm 依赖：

```sh
bun scripts/ktlint-changed.ts
bun scripts/ktlint-changed.ts --dry-run
bun scripts/ktlint-changed.ts --base origin/main
bun scripts/ktlint-changed.ts --files app/src/main/java/com/example/Example.kt
bun scripts/ktlint-changed.ts -- --offline --console=plain --max-workers=2
```

默认范围是相对 HEAD 的暂存、未暂存及未忽略的新增文件。--base 是比较端点，不自动计算 merge-base；--files 接受仓库相对路径，检查对应的整个 source set。删除和重命名也纳入范围，Git 路径使用 NUL 分隔以支持空格。

main、test、androidTest、debug 等改动映射到现有 Gradle 任务，同组只调用一次。无相关改动时跳过 Gradle 启动；Git 错误和 Gradle 失败返回非零退出码。不能识别的 Kotlin 位置退回模块或全仓库检查。

.editorconfig、Gradle 构建脚本、版本表、wrapper、buildSrc/build-logic 或 baseline 改动触发完整 ktlintCheck。CI 继续执行全量检查，原有规则、失败策略、reporters 和 baseline 保持一致。

```sh
./gradlew ktlintCheck
bun test scripts/ktlint-changed.test.ts
```

## 配置与边界

截至 2026-10-03，8 个模块使用 Gradle 插件 14.2.0，插件默认引擎为 ktlint 1.5.0；版本表里的 ktlint 数字是插件版本。规则来自根 .editorconfig，使用 android_studio 风格。app 使用 PLAIN 和 CHECKSTYLE 报告，ignoreFailures=false，排除 generated/build 目录。

app/config/ktlint/baseline.xml 是既有历史违规记录，插件自动加载。它在报告阶段过滤违规，不减少源码解析；加速不应新增或重生成 baseline。

当前插件会将上次中间结果中的文件重新带入检查。app main 的中间结果包含全部 244 个文件，因此改单文件仍可能重查整个 source set。本地入口缩小的是 source set 范围，不承诺文件级增量。没有使用插件私有 Git filter 或私有 API。

依据：[插件用法](https://github.com/JLLeitschuh/ktlint-gradle)、[引擎与 baseline 默认值](https://raw.githubusercontent.com/JLLeitschuh/ktlint-gradle/v14.2.0/plugin/src/main/kotlin/org/jlleitschuh/gradle/ktlint/KtlintExtension.kt)、[增量实现](https://raw.githubusercontent.com/JLLeitschuh/ktlint-gradle/v14.2.0/plugin/src/main/kotlin/org/jlleitschuh/gradle/ktlint/tasks/BaseKtLintCheckTask.kt)、[报告阶段](https://raw.githubusercontent.com/JLLeitschuh/ktlint-gradle/v14.2.0/plugin/src/main/kotlin/org/jlleitschuh/gradle/ktlint/TaskCreation.kt)。

## 性能建议与验证记录

保留 Gradle build cache、configuration cache 和 daemon。CI 已将检查与构建合并到一次调用；日常不要在检查前 clean 或使用 --rerun-tasks。避免多个重构建争抢资源；不要未经测量就全局增加 worker 数量或堆大小。

2026-10-03 的检查均成功：

| 场景 | 总耗时 | 任务结果 |
| --- | --- | --- |
| 全量检查两次 | 78.40s / 58.025s | 56 个任务 UP-TO-DATE；任务阶段约 1.8s / 2.9s |
| app main 强制重查 | 85.72s | 源码检查 47.666s，报告约 1.2s |
| 本地入口选择 app main | 33s | 3 个任务 UP-TO-DATE，配置缓存复用 |
| 全量及 JVM test 任务回归 | 44s | 56 个任务 UP-TO-DATE |

机器期间有其他 Gradle 构建，负载和 daemon 状态不同，不能用这些总耗时计算提速比例。短时间 worker GC 抽样未发现 Full GC，不能据此归因于堆不足。基准属于历史观测，不代表当前工作树完成了全量重新解析。

选择器的 4 个测试覆盖 Android/JVM source set、配置变化退回全量、未识别路径，以及真实临时 Git 仓库中的暂存、未暂存、新增、删除、重命名和无效 ref。临时仓库不创建 commit，不接触身份 hooks。

后续文件级快路径需要隔离中间结果，并验证 baseline 与全量检查结果一致；模块并行则需要在相同负载下测量。当前没有已验证的引擎升级、加堆或并行提速收益。
