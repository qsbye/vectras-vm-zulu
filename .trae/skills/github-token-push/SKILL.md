---
name: github-token-push
description: Push commits to the qsbye GitHub remote using the local plaintext token file .github/token read at runtime. Use when the user asks to push/推送 after committing and the default osxkeychain credentials lack write access (403). Do not use for creating commits, and never embed the token in command text or config.
---

# 使用 .github/token 推送到 GitHub

本机 osxkeychain 中的 GitHub 凭据是只读账号（ByeIO），向 `qsbye/audio-suit-zulu` 推送会 403。具备写权限的令牌由用户以明文存放在项目根 `.github/token`（单行 ghp_ 令牌）。本技能描述如何安全地用它推送。

## 1. 安全前置检查（每次推送前必做）

```bash
# a) 令牌文件已被 git 忽略（应输出 .gitignore 中的匹配行）
git check-ignore .github/token
# b) 令牌文件未被版本库跟踪（应无输出）
git ls-files --error-unmatch .github/token
```

任一检查失败就停止：先让用户把 `.github/token` 加入 `.gitignore`（已存在，见第 41 行），切勿在未忽略的情况下提交。

## 2. 推送（令牌运行时读取，不得出现在命令文本中）

使用 git 内联凭据助手，密码在 helper 被调用时才从文件读取——**不要**把令牌拼进 URL、不要 `git remote set-url`、不要写入任何配置：

```bash
git -c credential.helper= -c credential.helper='!f() { echo "username=qsbye"; echo "password=$(cat .github/token)"; }; f' push origin <分支名>
```

- 第一个 `-c credential.helper=`（等号后留空）**不可省略**：系统级配置（Xcode 自带 gitconfig）已有 osxkeychain 助手，会先返回只读账号 ByeIO 的凭据导致 403；空值用于清空继承的助手链，使本次内联助手成为唯一凭据来源。

- `<分支名>` 通常为 `android`。
- 该命令只影响这一次推送，令牌不会进入 `.git/config`、reflog 或 shell 历史中的远端 URL。
- 沙箱拦截网络/钥匙串时按正常授权在沙箱外执行；不要因此改成把令牌写入命令字符串。

## 3. 推送后校验

```bash
git fetch origin
git rev-parse HEAD origin/<分支名>   # 两个哈希必须一致
```

## 4. 红线

- 禁止在工具调用参数、提交信息、文件、日志里书写令牌明文；令牌只能经 `$(cat .github/token)` 在运行时进入 git 子进程。
- 禁止 `git add .github/token` 或任何包含该文件的提交；令牌一旦进入提交历史，GitHub Push Protection 会拒绝推送且需要重写历史。
- 若推送返回 403/401，停止并告知用户令牌可能失效或权限不足，由用户更换令牌文件，不要尝试其他账号或改写凭据。
- 用户若在对话中直接粘贴了令牌，提醒其用完后到 GitHub Settings → Developer settings → Tokens 吊销重置。
