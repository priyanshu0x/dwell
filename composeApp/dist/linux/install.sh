#!/usr/bin/env sh
set -eu

APP_BIN="${APP_BIN:-/usr/bin/screensaver-app}"
AUTOSTART_DIR="${XDG_CONFIG_HOME:-"$HOME/.config"}/autostart"
SYSTEMD_USER_DIR="${XDG_CONFIG_HOME:-"$HOME/.config"}/systemd/user"

mkdir -p "$AUTOSTART_DIR" "$SYSTEMD_USER_DIR"

sed "s#Exec=screensaver-app --show#Exec=$APP_BIN --show#" "$(dirname "$0")/screensaver-app.desktop" \
  > "$AUTOSTART_DIR/screensaver-app.desktop"

sed "s#ExecStart=/usr/bin/screensaver-app --daemon#ExecStart=$APP_BIN --daemon#" "$(dirname "$0")/screensaver-app.service" \
  > "$SYSTEMD_USER_DIR/screensaver-app.service"

if command -v systemctl >/dev/null 2>&1; then
  systemctl --user daemon-reload || true
  systemctl --user enable --now screensaver-app.service || true
fi

printf '%s\n' "Installed Screen Saver App desktop entry and user service."
