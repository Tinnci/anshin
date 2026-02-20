#!/bin/sh
# 配置 git 使用项目内的 .githooks 目录
# 首次 clone 后执行一次即可

set -e
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
chmod +x .githooks/pre-push
echo "Git hooks 已配置完成。pre-commit 和 pre-push 将自动运行。"
