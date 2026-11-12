# 构建与发布

## GitHub Actions 密钥

Nightly 可通过 Debug APK 自动构建，但要让应用内更新能够覆盖安装，正式版和
Nightly 必须使用同一个发布证书。在 `Pitchee-Android` 仓库的
`Settings > Secrets and variables > Actions` 中配置：

- `PITCHEE_KEYSTORE_BASE64`：发布 keystore 的 Base64 文本
- `PITCHEE_STORE_PASSWORD`：keystore 密码
- `PITCHEE_KEY_ALIAS`：key alias
- `PITCHEE_KEY_PASSWORD`：key password
- `SENTRY_AUTH_TOKEN`：上传 R8 mapping、Native symbols 和源码上下文所需的 Sentry
  Organization Auth Token

生成 Base64：

```bash
base64 -i pitchee-release.jks | tr -d '\n'
```

Sentry Auth Token 可在
<https://nexaorion.sentry.io/settings/auth-tokens/> 创建。未配置该 Secret 时，
应用仍会上报错误和性能事件，但 Release 构建不会上传符号文件。

## Sentry

- Organization：`nexaorion`
- Project：`pitchee-android`
- Debug 环境：`development`
- Release 环境：`production`
- Nightly 环境：`nightly`

## Nightly

`.github/workflows/nightly.yml` 仅通过手动触发。
工作流生成 `nightly-YYYYMMDD-HHMM` 版本并创建预发布 Release。

配置发布证书后，Nightly 会上传已签名的 Release APK；没有证书时回退到
Debug APK。

## 正式版

`.github/workflows/release.yml` 支持两种方式：

1. 在 Actions 中手动运行 `Release`，填写不带 `v` 的版本号，例如 `1.2.1`。
2. 推送 `v1.2.1` 形式的 Git tag。

工作流会生成版本号 `1.2.1`、版本代码 `1002001` 的签名 APK，并创建正式
GitHub Release。以后需要发布正式版时，只需指定版本号并推送对应 tag。
