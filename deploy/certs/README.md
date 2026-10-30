# HTTPS 证书

此目录用于挂载 Nginx 证书，**不要提交真实私钥**。

在项目根目录执行以下任一命令生成自签名证书（仅用于本地/演示）：

```bash
# Linux / macOS / Git Bash（需要 openssl）
bash scripts/gen-certs.sh
```

```powershell
# Windows PowerShell 7+
pwsh -File scripts/gen-certs.ps1
```

生成的文件：

- `deploy/certs/fullchain.pem`
- `deploy/certs/privkey.pem`

浏览器首次访问会提示证书不受信任，这是自签名证书的正常现象；生产环境请替换为 CA 签发的证书。
