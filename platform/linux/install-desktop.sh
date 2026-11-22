#!/usr/bin/env bash
# 把 xyc Launcher 注册到系统应用菜单（生成桌面项 + 可选创建桌面图标）
set -eu
cd -- "$(dirname -- "$0")" || exit 1
ROOT="$(pwd -P)"

LOCAL="$HOME/.local/share/applications"
mkdir -p "$LOCAL"
DESK="$LOCAL/xyc-launcher.desktop"

sed "s#__ROOT__#$ROOT#g" "$ROOT/xyc-launcher.desktop" > "$DESK"
chmod +x "$DESK"
update-desktop-database "$LOCAL" 2>/dev/null || true

echo "已注册到应用菜单：$DESK"

# 询问是否也放一个图标到桌面
if [ -d "$HOME/Desktop" ]; then
    printf '是否同时在桌面创建图标？[y/N] '
    read -r ans || ans=""
    case "$ans" in
        y|Y|yes|YES)
            cp "$DESK" "$HOME/Desktop/xyc-launcher.desktop"
            chmod +x "$HOME/Desktop/xyc-launcher.desktop"
            echo "已在桌面创建图标（若图标未显示，请在文件管理器中右键选择“允许启动”）。"
            ;;
    esac
fi
