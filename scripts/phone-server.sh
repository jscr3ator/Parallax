#!/usr/bin/env bash
# phone-server.sh — run opencode so the Parallax phone app can reach it, and show a pairing QR.
#
#   scripts/phone-server.sh            start the server in this terminal and print the QR
#   scripts/phone-server.sh --qr       only print the QR (server already running)
#   scripts/phone-server.sh --install  run it at login as a systemd user service, then print the QR
#   scripts/phone-server.sh --uninstall
#
# Env: PARALLAX_PORT (default 4096), PARALLAX_DIR (working dir for the server, default $HOME).
set -euo pipefail

PORT="${PARALLAX_PORT:-4096}"
WORKDIR="${PARALLAX_DIR:-$HOME}"
CONF_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/parallax"
PASS_FILE="$CONF_DIR/server-password"
UNIT="parallax-opencode.service"

mkdir -p "$CONF_DIR"
if [ ! -s "$PASS_FILE" ]; then
  # Stable random password, generated once; only readable by you.
  (umask 077; head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 28 > "$PASS_FILE")
fi
PASSWORD="$(cat "$PASS_FILE")"
OPENCODE="$(command -v opencode)"

lan_ip() { ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1; }
ts_ip() { ip -4 -o addr show tailscale0 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -1; }

urlencode() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }

print_qr() {
  local lan ts link
  lan="$(lan_ip)"; ts="$(ts_ip)"
  link="parallax://connect?name=$(urlencode "$(hostname)")&password=$(urlencode "$PASSWORD")"
  [ -n "$lan" ] && link="$link&url=$(urlencode "http://$lan:$PORT")"
  [ -n "$ts" ] && link="$link&url=$(urlencode "http://$ts:$PORT")"
  echo
  echo "  Scan this in Parallax  →  Connect  →  Scan QR"
  echo
  qrencode -t ANSIUTF8 -m 2 "$link"
  echo
  [ -n "$lan" ] && echo "  Home Wi-Fi:  http://$lan:$PORT"
  [ -n "$ts" ] && echo "  Tailscale:   http://$ts:$PORT   (works away from home if the phone runs Tailscale)"
  echo "  Password:    $PASSWORD"
  echo
  if command -v ufw >/dev/null 2>&1; then
    echo "  Your firewall (ufw) blocks phones by default. Allow this port once with:"
    [ -n "$lan" ] && echo "    sudo ufw allow from ${lan%.*}.0/24 to any port $PORT proto tcp"
    [ -n "$ts" ] && echo "    sudo ufw allow in on tailscale0 to any port $PORT proto tcp"
    echo
  fi
}

case "${1:-}" in
  --qr)
    print_qr ;;
  --install)
    mkdir -p "$HOME/.config/systemd/user"
    cat > "$HOME/.config/systemd/user/$UNIT" <<EOF
[Unit]
Description=opencode server for the Parallax phone app
After=network-online.target

[Service]
WorkingDirectory=$WORKDIR
Environment=OPENCODE_SERVER_PASSWORD=$PASSWORD
Environment=PATH=$PATH
ExecStart=$OPENCODE serve --hostname 0.0.0.0 --port $PORT
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
EOF
    chmod 600 "$HOME/.config/systemd/user/$UNIT"
    systemctl --user daemon-reload
    systemctl --user enable --now "$UNIT"
    echo "Installed and started $UNIT (runs at login)."
    print_qr ;;
  --uninstall)
    systemctl --user disable --now "$UNIT" 2>/dev/null || true
    rm -f "$HOME/.config/systemd/user/$UNIT"
    systemctl --user daemon-reload
    echo "Removed $UNIT." ;;
  "")
    print_qr
    cd "$WORKDIR"
    echo "  Starting opencode on port $PORT (Ctrl+C to stop)…"
    OPENCODE_SERVER_PASSWORD="$PASSWORD" exec "$OPENCODE" serve --hostname 0.0.0.0 --port "$PORT" ;;
  *)
    sed -n '2,9p' "$0"; exit 2 ;;
esac
