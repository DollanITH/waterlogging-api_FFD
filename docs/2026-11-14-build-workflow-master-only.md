# 构建工作流限制为 master

日期：2026-11-14

## 改动

- `.github/workflows/build.yml` 的自动 `push` 触发现在仅匹配 `master` 分支。
- `workflow_dispatch` 仍然保留，其他分支可以按需手动运行。

## 验证

- 已检查工作流 YAML 结构和变更差异。
