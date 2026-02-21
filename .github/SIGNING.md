# Release 签名配置指南

本项目使用 GitHub Actions 自动发布，签名通过 Secrets 注入，
Keystore 文件**不提交到版本库**。

---

## 第一步：生成 Keystore

```bash
keytool -genkey -v \
  -keystore medlog.jks \
  -alias medlog \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass <keystore密码> \
  -keypass  <key密码> \
  -dname "CN=MedLog, OU=Mobile, O=YourOrg, L=Shanghai, ST=Shanghai, C=CN"
```

> 生成后请将 `medlog.jks` 妥善保管，**不要提交到 Git**（`.gitignore` 已排除 `*.jks`）。

---

## 第二步：将 Keystore 编码为 Base64

```bash
# macOS
base64 -i medlog.jks | pbcopy       # 直接复制到剪贴板

# Linux
base64 medlog.jks                   # 输出到终端，手动复制
```

---

## 第三步：在 GitHub 仓库配置四个 Secrets

前往 **仓库 → Settings → Secrets and variables → Actions → New repository secret**：

| Secret 名称        | 值                                         |
|--------------------|--------------------------------------------|
| `KEYSTORE_BASE64`  | Keystore 文件的 Base64 编码                |
| `KEYSTORE_PASSWORD`| Keystore 密码（`-storepass` 值）           |
| `KEY_ALIAS`        | 签名密钥别名（如 `medlog`）                |
| `KEY_PASSWORD`     | 签名密钥密码（`-keypass` 值）              |

---

## 第四步：推送 Tag 触发发布

```bash
# 正式 Release
git tag v1.0.0
git push origin v1.0.0

# 预发布（GitHub Release 自动标记为 Pre-release）
git tag v1.1.0-rc1
git push origin v1.1.0-rc1
```

### versionCode 自动计算规则

`versionCode = major × 10000 + minor × 100 + patch`

| Tag      | versionName | versionCode |
|----------|-------------|-------------|
| v1.0.0   | 1.0.0       | 10000       |
| v1.2.3   | 1.2.3       | 10203       |
| v2.0.0   | 2.0.0       | 20000       |

---

## 无 Secrets 时的行为（默认）

如果 Secrets 未配置，CI 仍会完整运行，构建产出**未签名 APK**
（文件名带 `-unsigned` 后缀），适合开发测试，不可直接上传应用市场。
