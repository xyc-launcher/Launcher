#!/usr/bin/env bash
# xyc Launcher - 跨平台启动脚本（X11 / Wayland 原生）
#
# Java 检测原则（重要）：
#   启动时会先检测系统里已经安装的 Java；只要本地有可用 Java，就直接用它，
#   不会下载任何东西。只有当系统里完全没有 Java 时，才会按需自动下载。
#
# 图形栈选择：
#   * Wayland 会话 + 本地 Java 是 JBR（含 WLToolkit）→ 原生 Wayland。
#   * Wayland 会话 + 本地普通 Java（无 WLToolkit）→ 用本地 Java 走 XWayland，不下载。
#   * X11 会话 → 用本地 Java 走原生 X11。
#
# 可通过 XYC_UI 强制：
#   XYC_UI=wayland  强制原生 Wayland（需要 JBR，没有会自动下载到 .jbr/）
#   XYC_UI=x11      强制 X11/XWayland
#   XYC_UI=auto     自动（默认）：见上方图形栈选择
#
# 其他环境变量：
#   XYC_FORCE_NATIVE_WAYLAND=1  即使本地 Java 不支持 WLToolkit，也强制下载 JBR 走原生 Wayland
#   JBR_HOME        手动指定 JBR 目录（含 bin/java）
#   XYC_JBR_VERSION 手动指定 JBR 版本串（默认 21.0.11-linux-x64-b1163.125 之类，按架构拼）
#   XYC_JBR_URL     手动指定 JBR 下载地址（覆盖自动拼装）
#   XYC_JBR_DOWNLOAD=0  禁止自动下载 JBR（没有就回退 X11）
#   XYC_VULKAN=1    在 Wayland 下启用 Vulkan 硬件渲染（需要显卡/Vulkan 驱动）
#   JAVA_HOME       指定标准 Java
#   XYC_JAVA        直接指定启动器用的 java 可执行文件（最高优先级）

set -u

# [patch 2026-11-22] niri/sway 等 non-reparenting WM 下，Swing 需此变量才能正常绘制
# （否则窗口建出来但内容空白 = 白屏）
export _JAVA_AWT_WM_NONREPARENTING="${_JAVA_AWT_WM_NONREPARENTING:-1}"

cd -- "$(dirname -- "$0")" || exit 1
ROOT="$(pwd -P)"
JAR="$ROOT/launcher.jar"

if [ ! -f "$JAR" ]; then
    echo "错误：当前目录找不到 launcher.jar（$JAR）" >&2
    echo "请把 run.sh 和 launcher.jar 放在同一个文件夹里。" >&2
    exit 1
fi

# 日志统一走 stderr，保证 stdout 只用于函数返回 Java 路径（避免命令替换被日志污染）
log() { printf '%s\n' "$*" >&2; }

# ---- 架构：x64 / aarch64 / x86 ----
detect_arch() {
    local a
    a="$(uname -m 2>/dev/null | tr '[:upper:]' '[:lower:]')"
    case "$a" in
        x86_64|amd64)        echo x64 ;;
        aarch64|arm64)       echo aarch64 ;;
        i386|i486|i586|i686) echo x86 ;;
        *)                   echo x64 ;;
    esac
}
ARCH="$(detect_arch)"

# ---- 是否处于 Wayland 会话 ----
is_wayland_session() {
    if [ -n "${WAYLAND_DISPLAY:-}" ]; then return 0; fi
    if [ "${XDG_SESSION_TYPE:-}" = "wayland" ]; then return 0; fi
    # 有些发行版只设 XDG_CURRENT_DESKTOP，配合 wayland socket 判断
    if [ -n "${XDG_RUNTIME_DIR:-}" ]; then
        local s
        for s in "$XDG_RUNTIME_DIR"/wayland-*; do
            [ -S "$s" ] && return 0
        done
    fi
    return 1
}

# ---- 解析 java 主版本号（兼容 java 8 的 "1.8.0_x" 与 9+ 的 "17.0.9"）----
java_major() {
    local vout v
    vout="$("$1" -version 2>&1 | head -n 1)" || return 1
    v="$(printf '%s' "$vout" | sed -n 's/.*version "\([^"]*\)".*/\1/p')"
    case "$v" in
        1.*) v="${v#1.}"; v="${v%%.*}" ;;
        *)   v="${v%%.*}" ;;
    esac
    printf '%s' "$v"
}

# ---- 校验 java 可执行文件（要求 Java 17+，jar 是 Java 17 字节码）----
try_java() {
    local candidate="$1" major
    [ -n "$candidate" ] || return 1
    [ -x "$candidate" ] || return 1
    "$candidate" -version >/dev/null 2>&1 || return 1
    major="$(java_major "$candidate")"
    # 版本串无法识别时放行；识别出来但 <17 则拒绝，避免 UnsupportedClassVersionError
    case "$major" in
        ''|*[!0-9]*) : ;;
        *) [ "$major" -ge 17 ] 2>/dev/null || return 1 ;;
    esac
    printf '%s' "$candidate"; return 0
}

# ================= 标准 Java（X11 / XWayland 模式） =================
find_bundled() {
    local base="$ROOT/.minecraft/runtime" found
    [ -d "$base" ] || return 1
    found="$(find "$base" -type f -path '*/bin/java' -perm -u+x 2>/dev/null | head -n 1)"
    [ -n "$found" ] || return 1
    try_java "$found"
}
find_javahome() { [ -n "${JAVA_HOME:-}" ] || return 1; try_java "$JAVA_HOME/bin/java"; }
find_path() { command -v java >/dev/null 2>&1 || return 1; try_java "$(command -v java)"; }
find_common() {
    local d p found
    for d in /usr/lib/jvm/* /opt/java/* /opt/jdk* /usr/java/* \
             /usr/lib64/jvm/* "$HOME/.sdkman/candidates/java/"*; do
        [ -d "$d" ] || continue
        for p in "$d/bin/java" "$d/bin/jre" "$d/jre/bin/java"; do
            [ -x "$p" ] || continue
            if found="$(try_java "$p")"; then printf '%s' "$found"; return 0; fi
        done
    done
    return 1
}
bootstrap_temurin() {
    local dest="$ROOT/.minecraft/runtime/launcher-jre-$ARCH"
    local marker="$dest/bin/java" tmp archive url ok i
    if [ -x "$marker" ] && "$marker" -version >/dev/null 2>&1; then printf '%s' "$marker"; return 0; fi
    tmp="$(mktemp -d "${TMPDIR:-/tmp}/xyc-jre.XXXXXX")" || return 1
    archive="$tmp/jre.tar.gz"
    url="https://api.adoptium.net/v3/binary/latest/21/ga/linux/$ARCH/jre/hotspot/normal/eclipse"
    log "未找到 Java，正在下载 Temurin JRE 21（约 45MB）…"
    ok=0
    for i in 1 2 3 4 5; do
        if command -v curl >/dev/null 2>&1; then
            curl -fL --retry 2 --connect-timeout 20 -o "$archive" "$url" && ok=1 && break
        elif command -v wget >/dev/null 2>&1; then
            wget -q --tries=2 --timeout=30 -O "$archive" "$url" && ok=1 && break
        else
            log "错误：系统没有 curl 或 wget，无法下载 Java。请安装 Java 17+ 或设置 JAVA_HOME。" >&2
            rm -rf "$tmp"; return 1
        fi
        log "  下载失败，重试 $i/5 …"; sleep 2
    done
    [ "$ok" = 1 ] || { log "错误：Java 下载失败，请检查网络。" >&2; rm -rf "$tmp"; return 1; }
    log "正在解压…"
    mkdir -p "$dest"
    if ! tar -xzf "$archive" -C "$dest" --strip-components=1 2>/dev/null; then
        tar -xzf "$archive" -C "$dest" 2>/dev/null || {
            log "错误：Java 解压失败。" >&2; rm -rf "$tmp"; return 1; }
    fi
    rm -rf "$tmp"; chmod -R u+x "$dest/bin" 2>/dev/null || true
    local found
    found="$(find "$dest" -type f -path '*/bin/java' -perm -u+x 2>/dev/null | head -n 1)"
    [ -n "$found" ] && try_java "$found"
}

# 只检测本地已安装的 Java，绝不下载
find_local_java() {
    local f
    for f in find_bundled find_javahome find_path find_common; do
        if result="$($f 2>/dev/null)" && [ -n "$result" ]; then
            printf '%s' "$result"; return 0
        fi
    done
    return 1
}

# 检测某个 java 是否具备原生 Wayland 能力（JBR 自带 WLToolkit / libawt_wlawt.so）
java_supports_wayland() {
    local java_bin="$1" jhome real
    real="$(readlink -f "$java_bin" 2>/dev/null || printf '%s' "$java_bin")"
    jhome="$(dirname "$(dirname "$real")")"
    [ -f "$jhome/lib/libawt_wlawt.so" ] && return 0
    "$java_bin" -version 2>&1 | grep -qi 'JBR' && return 0
    return 1
}

# 本地 Java + 下载兜底（仅当本地完全没有 Java 时才下载）
find_standard_java() {
    local f
    f="$(find_local_java 2>/dev/null || true)"
    [ -n "$f" ] && { printf '%s' "$f"; return 0; }
    f="$(bootstrap_temurin 2>/dev/null || true)"
    [ -n "$f" ] && { printf '%s' "$f"; return 0; }
    return 1
}

# ================= JBR（Wayland 原生模式） =================
jbr_default_version() {
    case "$ARCH" in
        aarch64) echo "21.0.11-linux-aarch64-b1163.125" ;;
        x86)     echo "21.0.9-linux-x86-b1038.91" ;;
        *)       echo "21.0.11-linux-x64-b1163.125" ;;
    esac
}

find_existing_jbr() {
    local d f
    # 1) 环境变量
    if [ -n "${JBR_HOME:-}" ]; then
        f="$(try_java "$JBR_HOME/bin/java" 2>/dev/null || true)"
        [ -n "$f" ] && { printf '%s' "$f"; return 0; }
    fi
    # 2) 目录内 .jbr（支持 bin/java 直接在根，或一层子目录）
    d="$ROOT/.jbr"
    [ -d "$d" ] || return 1
    for f in "$d/bin/java" "$d"/*/bin/java; do
        [ -x "$f" ] || continue
        if [ -n "$(strings "$f" 2>/dev/null | grep -m1 -i jetbrains)" ] || \
           "$f" -version 2>&1 | grep -qi 'JBR'; then
            found="$(try_java "$f" 2>/dev/null || true)"
            [ -n "$found" ] && { printf '%s' "$found"; return 0; }
        fi
    done
    # 放宽：.jbr 下任意能跑的 java 也接受（用户可能自己放的）
    for f in "$d/bin/java" "$d"/*/bin/java; do
        [ -x "$f" ] || continue
        found="$(try_java "$f" 2>/dev/null || true)"
        [ -n "$found" ] && { printf '%s' "$found"; return 0; }
    done
    return 1
}

download_jbr() {
    local ver="${XYC_JBR_VERSION:-$(jbr_default_version)}"
    local url="${XYC_JBR_URL:-https://cache-redirector.jetbrains.com/intellij-jbr/jbr-$ver.tar.gz}"
    local dest="$ROOT/.jbr" archive tmp ok i

    if [ "${XYC_JBR_DOWNLOAD:-1}" = "0" ]; then
        log "提示：未找到 JBR，且已设置 XYC_JBR_DOWNLOAD=0，回退 X11/XWayland 模式。"
        return 1
    fi

    tmp="$(mktemp -d "${TMPDIR:-/tmp}/xyc-jbr.XXXXXX")" || return 1
    archive="$tmp/jbr.tar.gz"
    log "Wayland 原生模式需要 JetBrains Runtime（含 WLToolkit），正在下载 JBR21（约 95MB）…"
    log "下载地址：$url"
    log "（只需下载一次，之后放在同目录 .jbr/ 里；也可设 JBR_HOME 指向已有 JBR）"
    ok=0
    for i in 1 2 3 4 5; do
        if command -v curl >/dev/null 2>&1; then
            curl -fL --retry 2 --connect-timeout 20 -o "$archive" "$url" && ok=1 && break
        elif command -v wget >/dev/null 2>&1; then
            wget -q --tries=2 --timeout=30 -O "$archive" "$url" && ok=1 && break
        else
            log "错误：没有 curl 或 wget，无法下载 JBR。" >&2; rm -rf "$tmp"; return 1
        fi
        log "  下载失败，重试 $i/5 …"; sleep 2
    done
    [ "$ok" = 1 ] || { log "错误：JBR 下载失败，将回退 X11/XWayland。" >&2; rm -rf "$tmp"; return 1; }
    log "正在解压 JBR…"
    rm -rf "$dest"; mkdir -p "$dest"
    if ! tar -xzf "$archive" -C "$dest" --strip-components=1 2>/dev/null; then
        tar -xzf "$archive" -C "$dest" 2>/dev/null || {
            log "错误：JBR 解压失败。" >&2; rm -rf "$tmp"; return 1; }
    fi
    rm -rf "$tmp"; chmod -R u+x "$dest/bin" 2>/dev/null || true
    local found
    found="$(find "$dest" -type f -path '*/bin/java' -perm -u+x 2>/dev/null | head -n 1)"
    [ -n "$found" ] && try_java "$found"
}

# ================= 决策 =================
UI_MODE="${XYC_UI:-auto}"
SESSION_WAYLAND=0
is_wayland_session && SESSION_WAYLAND=1

# 第一步：纯检测本地已安装的 Java（不下载任何东西）
LOCAL_JAVA="$(find_local_java 2>/dev/null || true)"
LOCAL_JAVA_WL=0
[ -n "$LOCAL_JAVA" ] && java_supports_wayland "$LOCAL_JAVA" && LOCAL_JAVA_WL=1

# 也检测本地 JBR（JBR_HOME 或目录内 .jbr），避免有 JBR 却走 XWayland
LOCAL_JBR="$(find_existing_jbr 2>/dev/null || true)"
LOCAL_JBR_WL=0
[ -n "$LOCAL_JBR" ] && java_supports_wayland "$LOCAL_JBR" && LOCAL_JBR_WL=1

# 第二步：决定是否走原生 Wayland
USE_WAYLAND=0
case "$UI_MODE" in
    wayland|native) USE_WAYLAND=1 ;;
    x11|xwayland)   USE_WAYLAND=0 ;;
    *)
        if [ "$SESSION_WAYLAND" = 1 ]; then
            if [ "$LOCAL_JAVA_WL" = 1 ] || [ "$LOCAL_JBR_WL" = 1 ]; then
                USE_WAYLAND=1
            elif [ "${XYC_FORCE_NATIVE_WAYLAND:-0}" = "1" ]; then
                USE_WAYLAND=1
            fi
        fi
        ;;
esac

JAVA=""
JVM_ARGS=()

if [ "$USE_WAYLAND" = 1 ]; then
    log "图形模式：Wayland 原生（WLToolkit）"
    if [ "$LOCAL_JAVA_WL" = 1 ]; then
        JAVA="$LOCAL_JAVA"
        log "检测到本地已安装的 JBR，直接复用（无需下载）"
    elif [ "$LOCAL_JBR_WL" = 1 ]; then
        JAVA="$LOCAL_JBR"
        log "检测到本地 JBR（JBR_HOME/.jbr），直接复用（无需下载）"
    else
        JAVA="$(find_existing_jbr 2>/dev/null || true)"
        [ -z "$JAVA" ] && JAVA="$(download_jbr 2>/dev/null || true)"
    fi
    if [ -n "$JAVA" ]; then
        JVM_ARGS+=("-Dawt.toolkit.name=WLToolkit")
        # 让高 DPI / 分数缩放下界面更清晰
        JVM_ARGS+=("-Dsun.java2d.uiScale.enabled=true")
        if [ "${XYC_VULKAN:-0}" = "1" ]; then
            JVM_ARGS+=("-Dsun.java2d.vulkan=True")
            log "已启用 Vulkan 渲染（XYC_VULKAN=1）"
        fi
    else
        log "警告：无法获得 JBR，回退到 X11/XWayland 模式。"
        USE_WAYLAND=0
    fi
fi

if [ "$USE_WAYLAND" = 0 ]; then
    # [patch 2026-11-22] Wayland 会话走 XWayland 时，若 DISPLAY 缺失则自动探测并设置
    if [ -z "${DISPLAY:-}" ] && [ "$SESSION_WAYLAND" = 1 ]; then
        for _xs in /tmp/.X11-unix/X*; do
            if [ -S "$_xs" ]; then
                export DISPLAY=":${_xs##*X}"
                log "自动探测到 XWayland，设置 DISPLAY=$DISPLAY"
                break
            fi
        done
    fi
    if [ -n "$LOCAL_JAVA" ]; then
        JAVA="$LOCAL_JAVA"
        log "使用本地已安装的 Java（未下载任何东西）"
        if [ "$SESSION_WAYLAND" = 1 ] && [ "$LOCAL_JAVA_WL" = 0 ]; then
            log "提示：本地 Java 不含 WLToolkit，在 Wayland 下通过 XWayland 运行。"
            log "      如需原生 Wayland，设 XYC_FORCE_NATIVE_WAYLAND=1 会自动下载 JBR。"
        fi
    else
        log "图形模式：X11 / XWayland（本地未找到 Java，将按需下载）"
        JAVA="$(find_standard_java 2>/dev/null || true)"
    fi
fi

if [ -z "$JAVA" ]; then
    log "错误：无法找到或下载可用的 Java。" >&2
    log "  - 请安装 Java 17+（sudo apt install openjdk-21-jre），或设 JAVA_HOME / XYC_JAVA" >&2
    log "  - Wayland 原生模式：设 JBR_HOME，或设 XYC_FORCE_NATIVE_WAYLAND=1 自动下载 JBR" >&2
    exit 1
fi

if [ -n "${XYC_JAVA:-}" ]; then
    if override="$(try_java "$XYC_JAVA" 2>/dev/null)"; then
        log "使用手动指定 Java: $override"
        JAVA="$override"
        JVM_ARGS=()
    fi
fi

log "使用 Java: $JAVA"
if [ "${#JVM_ARGS[@]}" -gt 0 ]; then log "JVM 参数: ${JVM_ARGS[*]}"; fi
log "启动 xyc Launcher …"
exec "$JAVA" "${JVM_ARGS[@]}" -jar "$JAR" "$@"
