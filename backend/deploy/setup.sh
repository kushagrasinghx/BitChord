#!/usr/bin/env bash
# One-shot installer for the Listen Together server on an Oracle Cloud
# (or any Ubuntu 22.04/24.04) VM. Safe to re-run: it rebuilds and restarts.
#
#   sudo DOMAIN=jam.bitchord.kushagrasingh.in bash deploy/setup.sh
#
# First run on a fresh VM also takes DEPLOY_PUBKEY (the public half of the
# GitHub Actions deploy key) to create the restricted `deploy` user.
#
# Run it from the backend/ directory of a checkout on the VM. The DNS A record
# for $DOMAIN must already point at this VM, or Caddy cannot get a certificate.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

DOMAIN="${DOMAIN:?set DOMAIN, e.g. DOMAIN=jam.bitchord.kushagrasingh.in}"
GO_VERSION="${GO_VERSION:-1.27.0}"
BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"

case "$(uname -m)" in
  aarch64) GOARCH=arm64 ;;   # Ampere A1 shape
  x86_64)  GOARCH=amd64 ;;   # E2.1.Micro shape
  *) echo "unsupported arch $(uname -m)"; exit 1 ;;
esac

echo "== packages"
if ! dpkg -s curl git gnupg iptables-persistent >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y curl git debian-keyring debian-archive-keyring apt-transport-https gnupg iptables-persistent
fi

echo "== Go $GO_VERSION"
if ! /usr/local/go/bin/go version 2>/dev/null | grep -q "go$GO_VERSION"; then
  curl -fsSL "https://go.dev/dl/go$GO_VERSION.linux-$GOARCH.tar.gz" -o /tmp/go.tgz
  rm -rf /usr/local/go && tar -C /usr/local -xzf /tmp/go.tgz && rm /tmp/go.tgz
fi

echo "== build"
id bitchord >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin bitchord
install -d -o bitchord -g bitchord /opt/bitchord-jam
(cd "$BACKEND_DIR" && /usr/local/go/bin/go build -o /opt/bitchord-jam/server .)
chown bitchord:bitchord /opt/bitchord-jam/server

echo "== service"
install -m 644 "$BACKEND_DIR/deploy/bitchord-jam.service" /etc/systemd/system/bitchord-jam.service
systemctl daemon-reload
systemctl enable bitchord-jam
systemctl restart bitchord-jam
# The CPU keepalive burner is retired; clean it off VMs that still have it.
if [ -f /etc/systemd/system/bitchord-keepalive.service ]; then
  systemctl disable --now bitchord-keepalive || true
  rm -f /etc/systemd/system/bitchord-keepalive.service
  systemctl daemon-reload
fi

echo "== caddy (HTTPS + WebSocket proxy)"
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y && apt-get install -y caddy
fi
sed "s/{\$DOMAIN}/$DOMAIN/" "$BACKEND_DIR/deploy/Caddyfile" > /tmp/Caddyfile
if ! cmp -s /tmp/Caddyfile /etc/caddy/Caddyfile; then
  mv /tmp/Caddyfile /etc/caddy/Caddyfile
  systemctl reload caddy || systemctl restart caddy
fi

echo "== deploy hook"
echo "$DOMAIN" > /etc/bitchord-domain
install -m 755 "$BACKEND_DIR/deploy/bitchord-deploy.sh" /usr/local/sbin/bitchord-deploy

if [ -n "${DEPLOY_PUBKEY:-}" ]; then
  echo "== deploy user"
  # The GitHub Actions key may only run the deploy hook (forced command).
  id deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
  install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
  printf 'command="sudo /usr/local/sbin/bitchord-deploy",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty %s\n' "$DEPLOY_PUBKEY" > /home/deploy/.ssh/authorized_keys
  chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
  echo 'deploy ALL=(root) NOPASSWD: /usr/local/sbin/bitchord-deploy' > /etc/sudoers.d/bitchord-deploy
  chmod 440 /etc/sudoers.d/bitchord-deploy && visudo -cf /etc/sudoers.d/bitchord-deploy
fi

echo "== firewall"
# Oracle's Ubuntu images ship an iptables REJECT rule that blocks everything
# but SSH, independent of the VCN security list. Open 80/443 above it.
for port in 80 443; do
  while iptables -D INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null; do :; done
  reject="$(iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" { print $1; exit }')"
  iptables -I INPUT "${reject:-1}" -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
netfilter-persistent save

echo "== check"
sleep 2
curl -fsS http://127.0.0.1:8000/healthz && echo
echo "Done. Once DNS resolves, https://$DOMAIN/healthz should answer."
