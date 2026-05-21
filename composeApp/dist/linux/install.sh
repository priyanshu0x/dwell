#!/usr/bin/env sh
set -eu

APP_BIN="${APP_BIN:-/usr/bin/screensaver-app}"
AUTOSTART_DIR="${XDG_CONFIG_HOME:-"$HOME/.config"}/autostart"
SYSTEMD_USER_DIR="${XDG_CONFIG_HOME:-"$HOME/.config"}/systemd/user"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ ! -x "$APP_BIN" ]; then
  printf '%s\n' "Screen Saver App binary is not executable: $APP_BIN" >&2
  printf '%s\n' "Set APP_BIN=/path/to/screensaver-app when running this installer." >&2
  exit 1
fi

mkdir -p "$AUTOSTART_DIR" "$SYSTEMD_USER_DIR"

APP_BIN_SED=$(printf '%s' "$APP_BIN" | sed 's/[#&]/\\&/g')

sed "s#Exec=screensaver-app --show#Exec=$APP_BIN_SED --show#" "$SCRIPT_DIR/screensaver-app.desktop" \
  > "$AUTOSTART_DIR/screensaver-app.desktop"

sed "s#ExecStart=/usr/bin/screensaver-app --daemon#ExecStart=$APP_BIN_SED --daemon#" "$SCRIPT_DIR/screensaver-app.service" \
  > "$SYSTEMD_USER_DIR/screensaver-app.service"

if command -v systemctl >/dev/null 2>&1; then
  systemctl --user daemon-reload || true
  systemctl --user enable --now screensaver-app.service || true
fi

mkdir -p "$HOME/.screensaver"
cat > "$HOME/.screensaver/xscreensaver-program.txt" <<EOF
"Screen Saver App"  $APP_BIN --show
EOF

printf '%s\n' "Installed Screen Saver App desktop entry and user service."
printf '%s\n' "xscreensaver program entry written to $HOME/.screensaver/xscreensaver-program.txt"
