# VPS Scripts

## init-letsencrypt.sh

Run on the VPS from the repo root (`/opt/smartparking`):

```bash
chmod +x scripts/vps/init-letsencrypt.sh
export LETSENCRYPT_EMAIL=admin@smartparking.id.vn
./scripts/vps/init-letsencrypt.sh
```

Notes:
- DNS must already point `smartparking.id.vn` and `www.smartparking.id.vn` to the VPS.
- Ports 80 and 443 must be reachable from the internet.
- The script will obtain the cert first (standalone on port 80), then start the full stack (`proxy` + dependencies) and the renew loop.
