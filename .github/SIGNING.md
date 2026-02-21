# Release 签名配置

## 设计原则

| 目标 | 方案 |
|------|------|
| 密钥不进 Git | `.gitignore` 屏蔽 `*.jks`, `*.b64` 及 `local.properties` 签名字段 |
| 跨 Mac 自动同步 | Keystore 存 **iCloud Drive**，密码存 **iCloud Keychain** |
| CI 签名 | GitHub Secrets → 工作流解码 → 临时 keystore |
| 无密钥时降级 | 检测不到签名配置时跳过，输出 unsigned APK |

---

## 文件存放位置

```
iCloud Drive/Dev/Keystores/
└── medlog-release.jks          # 跨 Mac 自动同步（永远不进 Git）

macOS Keychain（通过 iCloud Keychain 同步）
└── 服务: MedLogAndroid-Keystore  账号: release  密码: <your-password>

本地项目（gitignored）
└── local.properties            # 由 setup-signing.sh 自动生成
```

---

## 首次生成 Keystore（只需做一次）

```bash
# 1. 生成强密码
PASS=$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 24)

# 2. 生成 PKCS12 Keystore（RSA 4096，有效期 ~27 年）
mkdir -p ~/Library/Mobile\ Documents/com~apple~CloudDocs/Dev/Keystores
keytool -genkey -v \
  -keystore ~/Library/Mobile\ Documents/com~apple~CloudDocs/Dev/Keystores/medlog-release.jks \
  -storetype pkcs12 -alias release \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=MedLog, OU=Android, O=<YourOrg>, C=CN"

# 3. 存入 Keychain（iCloud Keychain 自动同步到其他 Mac）
security add-generic-password -a "release" -s "MedLogAndroid-Keystore" -w "$PASS" -T ""

# 4. 初始化 local.properties
./scripts/setup-signing.sh
```

---

## 换了新 Mac / 重装系统后

iCloud 同步完成后只需：

```bash
./scripts/setup-signing.sh
```

脚本自动检测 iCloud Drive keystore → 从 Keychain 读取密码 → 写入 `local.properties`。

---

## 配置 GitHub Actions Secrets

前往 **Settings → Secrets and variables → Actions**，添加以下 4 个 Secret：

| Secret | 获取方式 |
|--------|---------|
| `KEYSTORE_BASE64` | `base64 -i ~/Library/Mobile\ Documents/com~apple~CloudDocs/Dev/Keystores/medlog-release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD` | `security find-generic-password -a release -s MedLogAndroid-Keystore -w` |
| `KEY_ALIAS` | `release` |
| `KEY_PASSWORD` | 同 `KEYSTORE_PASSWORD` |

---

## 触发 Release

```bash
git tag v1.0.0 && git push origin v1.0.0
# 预发布（自动标记 Pre-release）
git tag v1.1.0-rc1 && git push origin v1.1.0-rc1
```

---

## versionCode 规则

`major × 10000 + minor × 100 + patch`

| Tag | versionCode |
|-----|-------------|
| v1.0.0 | 10000 |
| v1.2.3 | 10203 |
| v2.0.0 | 20000 |

---

## 无 Secrets 时的行为

`KEYSTORE_PASSWORD` 在所有来源均为空时跳过签名，输出 **unsigned APK**（文件名含 `-unsigned`）。
