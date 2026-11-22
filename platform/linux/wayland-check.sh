#!/usr/bin/env bash
# 诊断当前图形栈与 Wayland 原生支持状态
cd -- "$(dirname -- "$0")" || exit 1
ROOT="$(pwd -P)"

echo "=== 会话检测 ==="
echo "WAYLAND_DISPLAY = ${WAYLAND_DISPLAY:-（未设置）}"
echo "XDG_SESSION_TYPE = ${XDG_SESSION_TYPE:-（未设置）}"
echo "DISPLAY = ${DISPLAY:-（未设置）}"
if [ -n "${XDG_RUNTIME_DIR:-}" ]; then
    echo "Wayland sockets:"
    ls -l "$XDG_RUNTIME_DIR"/wayland-* 2>/dev/null || echo "  （无）"
fi

echo
echo "=== Java 运行时 ==="
if [ -n "${XYC_JAVA:-}" ]; then echo "XYC_JAVA = $XYC_JAVA"; fi
if [ -n "${JBR_HOME:-}" ]; then echo "JBR_HOME = $JBR_HOME"; fi
for f in "$ROOT/.jbr/bin/java" "$ROOT/.jbr"/*/bin/java; do
    [ -x "$f" ] || continue
    echo "bundled JBR: $f"
    "$f" -version 2>&1 | head -1
done

echo
echo "=== 原生 Wayland 工具包（WLToolkit）检测 ==="
check_jbr() {
    local j="$1"
    [ -x "$j" ] || return 1
    echo -n "  $j : "
    if "$j" -Dawt.toolkit.name=WLToolkit -XshowSettings:properties -version 2>&1 \
        | grep -qi 'awt.toolkit.name = WLToolkit'; then
        # 真正判断：找 libawt_wlawt.so
        local jdir lib
        jdir="$(dirname "$(dirname "$j")")"
        lib="$(find "$jdir" -name 'libawt_wlawt.so' 2>/dev/null | head -1)"
        if [ -n "$lib" ]; then
            echo "支持原生 Wayland ✓ (libawt_wlawt.so 存在)"
            return 0
        fi
        echo "参数被接受，但未找到 libawt_wlawt.so"
    else
        echo "不支持"
    fi
    return 1
}
found=0
[ -n "${JBR_HOME:-}" ] && check_jbr "$JBR_HOME/bin/java" && found=1
for f in "$ROOT/.jbr/bin/java" "$ROOT/.jbr"/*/bin/java; do
    [ -x "$f" ] || continue
    check_jbr "$f" && found=1
done
if [ "$found" = 0 ] && command -v java >/dev/null 2>&1; then
    check_jbr "$(command -v java)" || echo "  （标准 OpenJDK 通常不含 WLToolkit，Wayland 下走 XWayland）"
fi

echo
echo "=== 结论 ==="
if [ -n "${WAYLAND_DISPLAY:-}" ] || [ "${XDG_SESSION_TYPE:-}" = "wayland" ]; then
    echo "当前是 Wayland 会话。运行 ./run.sh 时："
    echo "  · 若本地 Java 是 JBR（含 WLToolkit）→ 原生 Wayland"
    echo "  · 若本地是普通 Java（无 WLToolkit）→ XWayland，不下载"
    echo "  · 若完全没有 Java → 才下载 JBR 走原生"
    echo "强制 XWayland：XYC_UI=x11 ./run.sh"
    echo "强制原生（本地不支持时下载 JBR）：XYC_FORCE_NATIVE_WAYLAND=1 ./run.sh"
else
    echo "当前不是 Wayland 会话，run.sh 将使用本地 Java 走 X11 模式（无需下载）。"
    echo "测试原生 Wayland：XYC_UI=wayland ./run.sh（需 JBR）"
fi
