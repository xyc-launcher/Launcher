# xyc Launcher

一个极简的便携式跨平台 Minecraft Java 版启动器。基于 Java/Swing 框架。可以按游戏版本要求自动配置运行时。

## 下载与运行

可以前往 [Github发布页](https://github.com/xyc-launcher/Launcher/releases) 下载本程序。

下载后请核验文件哈希和是否相同。

### Windows

前提：

- .NET Framework 4.x
- Java 17 或更高版本

发布页文件说明

- `xyc.Launcher1.0.1.exe` 64位启动器
- `xyc.Launcher.32bit1.0.1.exe` 32位启动器

请按照你的电脑配置选择


### Linux

在发布页下载 `xyc.Launcher.Linux.v1.0.3.zip` 文件并解压，然后进入目录赋予权限后运行 `run.sh`

```bash
# 1. 解压/下载后进入 linux 目录
cd linux

# 2. 赋予执行权限（仅首次）
chmod +x run.sh

# 3. 启动
./run.sh
```

`run.sh` 会**先检测系统里已经安装的 Java，有就直接用，绝不重复下载**。检测顺序：

1. 内置运行时 `.minecraft/runtime/` 下的 `jdk-*/bin/java`
2. 环境变量 `JAVA_HOME`
3. `PATH` 里的 `java`
4. 常见目录 `/usr/lib/jvm`、`/opt/java`、`/usr/java`、`~/.sdkman/candidates/java` 等

**只有上面全部找不到时**，才会按需自动下载 **Temurin JRE 21**（约 45MB）到 `.minecraft/runtime/`。也就是说：系统里只要装了 Java，就什么都不用装、也不会下载任何东西；终端会打印「使用本地已安装的 Java（未下载任何东西）」。

> **启动器自身要求 Java 17 或更高**（jar 是 Java 17 字节码）；本地若只有 Java 8/11，脚本会自动跳过并下载 JRE 21。
>
> **游戏本体所需的 Java 由启动器按游戏版本自动单独准备**（老版本精确匹配 Java 8，新版用 17/21），与跑界面的 Java 互不干扰。即使你用 Java 21 跑启动器，也照样能启动需要 Java 8 的老版本游戏。想手动指定跑界面的 Java，设 `JAVA_HOME` 或 `XYC_JAVA` 即可。

## 校验与签名（Windows）

exe 带 Authenticode 数字签名（SHA-256 / RSA，含 DigiCert 时间戳），证书 Subject 是 `_xycwww_`，有效期到 2036 年；文件属性里是 `xyc Launcher 1.0.1.0`、© `_xycwww_`。

这张证书是自签名的，没有 CA 签发，所以 Windows 第一次运行会弹「未知发布者」。这不是文件有问题，只是没有第三方为身份背书；要消掉这个提示只能买代码签名证书（OV/EV，一年几百到几千）。

### 发布包拆解

Release 里的 exe 是「.NET 外壳 + 内嵌 jar」两层，拆出来的东西都在 `unpacked/`：

- `xyc_embedded.jar`：从 exe 原样取出的 jar，125,473 字节，44 个 class
- `classes/`：同一个 jar 展开后的内容，可直接反编译
- `shell/pe-info.txt`：PE 结构、版本资源、外壳方法名和字符串常量
- `shell/xyc.Launcher.cer`：签名证书（DER）
- `extract.py`：复现脚本，拿原版 exe 自己跑一遍就能得到上面这些

两个 exe（64 / 32 位）里的 jar 逐字节相同，只有外壳不一样。

## Linux 图形模式（X11 / Wayland）

`run.sh` 会**自动识别当前图形会话，并优先复用本地已有 Java，不会为了 Wayland 而强制下载**：

| 会话 | 本地 Java 情况 | 行为 |
| --- | --- | --- |
| Wayland | 本地 Java 就是 JBR（含 `libawt_wlawt.so`） | 直接复用，**原生 Wayland**，不下载 |
| Wayland | 本地是普通 Java（OpenJDK/Temurin…） | 直接用它走 **XWayland**，**不下载** |
| Wayland | 完全没有 Java | 才自动下载 JBR21 走原生 |
| X11 | 有本地 Java | 直接用它走原生 X11，不下载 |

也就是说：**默认不会因为 Wayland 就下载 95MB 的 JBR**。终端会明确告诉你用的是本地 Java 还是原生 Wayland；如果本地 Java 不是 JBR，会提示「本地 Java 不含 WLToolkit，在 Wayland 下通过 XWayland 运行」。

**想要原生 Wayland** 时有两种方式，按需选择：

```bash
# 方式 A：想原生，但允许自动下载 JBR（仅在本地 Java 不支持时下载一次到 .jbr/）
XYC_FORCE_NATIVE_WAYLAND=1 ./run.sh

# 方式 B：自己已有 JBR，直接指定（完全不下载）
JBR_HOME=/path/to/jbr ./run.sh
# 或把 JBR 解压到启动器目录的 .jbr/ 文件夹，脚本自动识别
```

### 强制图形模式

| 命令 | 效果 |
| --- | --- |
| `./run.sh` | 自动：优先用本地 Java；本地是 JBR 才走原生 Wayland，否则 X11/XWayland，**均不下载** |
| `XYC_FORCE_NATIVE_WAYLAND=1 ./run.sh` | Wayland 下强制原生：本地 Java 不支持时自动下载 JBR |
| `XYC_UI=wayland ./run.sh` | 强制原生 Wayland（没有 JBR 会自动下载） |
| `XYC_UI=x11 ./run.sh` | 强制 X11 / XWayland（用本地 Java） |
| `XYC_VULKAN=1 XYC_UI=wayland ./run.sh` | 原生 Wayland + Vulkan 硬件渲染（需显卡驱动支持） |

### 为什么需要 JBR

原生 Wayland 工具包（`WLToolkit`）目前**只在 JetBrains Runtime 里提供**，标准 OpenJDK / Temurin / 微软 OpenJDK 都没有。普通 Java 在 Wayland 下只能走 XWayland 兼容层（高分屏模糊、窗口集成差）。JBR 由 JetBrains 发布、基于 OpenJDK 21、可自由使用。

> 已知限制（JBR 的 WLToolkit 实验性部分）：原生窗口标题栏/decoration、拖放、部分输入法尚不完善；对本启动器的标准 Swing 界面无影响。若遇到兼容问题，`XYC_UI=x11 ./run.sh` 即可走 XWayland 回退。

### 诊断与桌面集成

```bash
# Wayland 原生支持诊断
chmod +x wayland-check.sh
./wayland-check.sh

# 注册到应用菜单 / 桌面（可选）
./install-desktop.sh
```

图形界面直接双击：在文件管理器里右键 `run.sh` → 属性 → 权限 → 勾选「允许作为程序执行」，之后双击即可；或在「打开方式」里选终端运行。

## 功能

### 游戏版本下载

「刷新版本列表」拉取全部版本（release / snapshot / old_beta / old_alpha）。启动时自动下载版本 JSON、客户端 jar、全部库文件（含 native 解压）与资源文件。

下载默认走 BMCLAPI 国内镜像，官方源→镜像逐文件回退；断点续传 + 8 轮重试 + 403 指数退避，客户端大文件慢网也能下完；最终按 sha1 校验，不符自动重下。

### Mod 加载器

支持 Fabric / Quilt / Forge / NeoForge。选好加载器版本后启动时自动下载加载器库并生成启动 profile；Forge / NeoForge 会自动下载官方 installer 并静默安装。

### 整合包

搜索 Modrinth 整合包（`.mrpack`），下载并解析索引，自动把 mods 与覆盖文件部署到 `.minecraft/instances/<包名>`（Windows 为 `.minecraft\instances\<包名>\`）独立实例目录，按整合包要求自动装对应 MC 版本与加载器。

### Java 自动检测与下载

启动时读取版本 JSON 的 `javaVersion` 要求；本机有可用的 Java 就直接用，否则自动下载对应版本并解压。Windows 下从 `JAVA_HOME`、注册表、`.minecraft\runtime`、`PATH` 里找；Linux 下从内置运行时、`JAVA_HOME`、`PATH` 及常见 JVM 目录里找。

老版本精确匹配 Java 8（统一走 Adoptium），新版用 17/21。「Java」下拉默认「自动：按游戏版本要求（推荐）」，也可选「自动：使用最新版」「固定 Java 17」「固定 Java 21」。

### 启动参数

完整解析版本 JSON 的 `arguments.jvm`（`--enable-native-access`、`--add-exports`、`-Djava.library.path`、`-cp` 等 Java 25 必需项）与 `arguments.game`；按规则（os/arch/features）精确启用参数，自定义分辨率自动传入宽高，quick play / demo 等未启用功能参数不会被误传，杜绝「Only one quick play option」类崩溃。

### 文件完整性校验

「校验文件完整性」按钮：按版本 JSON 的 sha1 逐文件核对客户端 jar、全部库与资源，缺失或损坏自动补下并返回修复数量。

### 账号

- 离线模式：输入任意名字，生成离线账号（单机 / 局域网 / 部分服务器）。
- 微软正版登录：设备码全链路（OAuth → Xbox Live → XSTS → Minecraft → 玩家档案），自动保存 `refreshToken`，令牌过期自动刷新。
- 皮肤站外置登录：填写 Yggdrasil API（默认 LittleSkin）+ 账号密码；启动时自动下载并注入 `authlib-injector`，支持 LittleSkin、Blessing Skin 等兼容站点。

### 设置

内存 512MB ~ 16GB；下载源 bmclapi 国内镜像 / mojang 官方；窗口 960×640（可在源码 Config 调整）。

## Windows 专有功能

以下功能由 Windows 外壳 / 独立模块提供，Linux 开源版不含：

### 自动更新

启动时查 GitHub 最新 Release（认 `config.json` 里的 `githubRepo` 或 `updateUrl`），拿到新包先比大小再比 sha256，对得上才换。

替换走一个临时批处理：旧 exe 改名备份 → 拷新文件 → 成功删备份，失败自动回滚，下次打开会提示上次没更成功。国内直连慢时先试 `gh-proxy.com` / `ghproxy.net` 镜像，再回原地址。

### P2P 局域网分发

同一局域网内的设备自动互相发现：UDP 广播，默认 48901 端口，30 秒没有心跳就当对方离线；端口被占用时可用环境变量 `XYC_P2P_PORT` 换一个。

谁已经下过某个文件，别人就直接从他那拉，不走公网。传输走局域网 HTTP，端口由系统临时分配、随广播告知对方。同一个文件最多同时向 5 台设备并发拉取，谁先给完算谁的。

落盘前按 sha1 核对，先写临时文件、校验通过再原子替换，对不上直接丢掉重下，不会留半截文件。只对外提供游戏目录里的文件，路径越界一律拒绝。

不需要这功能就把 `config.json` 里的 `p2pEnabled` 改成 `false`。公共网络请勿开启，原因见「安全提示」。

### 证书回落

1.0.1 起 HTTPS 会在 Java 自带证书校验失败时回落到 Windows 根证书库。装过企业代理 / 抓包工具根证书的机器，那些连接也会被当成可信，介意就自己留意。

## 仓库结构

仓库根目录是 Windows 版（`.exe` 在 Releases 下载，`run.bat` / `build.bat` / `unpacked/` 在此），`linux/` 文件夹是完整的 Linux 版，两套互不干扰。

## Linux 目录结构与编译

`linux/` 是自包含的一整份，拷到任意位置都能跑：

```
linux/
├── run.sh                 主启动脚本（自动识别 X11/Wayland，自动找/下 Java）
├── wayland-check.sh       Wayland 原生支持诊断
├── build.sh               从源码重新编译 launcher.jar
├── install-desktop.sh     注册到应用菜单/桌面
├── xyc-launcher.desktop   桌面项模板
├── launcher.jar           启动器本体（跨平台）
├── config.json            首次运行后自动生成（含账号，勿外发）
├── .jbr/                  Wayland 原生运行时（首次在 Wayland 下运行自动下载）
├── .minecraft/            游戏目录（版本/库/资源/运行时/整合包实例）
├── src/                   完整 Java 源码
├── assets/icon.png        图标
├── HeadlessTest.java      无头逻辑自测
├── LICENSE                MIT
└── SECURITY.md            安全说明
```

从源码重新编译需要 JDK（含 `javac`，如 `sudo apt install openjdk-21-jdk`）：

```bash
./build.sh
```

会用源码重建 `launcher.jar`。想用别的 JDK：`JAVAC=/path/to/javac JAR=/path/to/jar ./build.sh`。

自测：

```bash
mkdir -p out && javac -encoding UTF-8 -cp launcher.jar -d out HeadlessTest.java
java -cp out:launcher.jar HeadlessTest
```

## 常见问题

- **Windows 首次运行弹「未知发布者」**：exe 是自签名证书，没有 CA 背书，属正常现象，见「校验与签名」。
- **Linux 双击 run.sh 没反应**：文件管理器需先允许「作为程序执行」，或直接在终端 `./run.sh`。
- **提示下载 Java 失败**：系统里没检测到 Java 且网络不通。手动装 Java 17+ 后设 `JAVA_HOME`，或把任意 JDK 解压到 `.minecraft/runtime/` 下即可，脚本会优先用本地的。
- **Linux Wayland 下没走原生、提示走 XWayland**：因为本地 Java 不是 JBR。这是默认行为（不下载）；想要原生就设 `XYC_FORCE_NATIVE_WAYLAND=1` 自动下载 JBR，或 `JBR_HOME` 指定已有 JBR。
- **Linux Wayland 下窗口样式怪 / 输入法异常**：JBR WLToolkit 的实验性限制，`XYC_UI=x11 ./run.sh` 可回退 XWayland。
- **Linux 中文显示成方块**：系统缺少中文字体，安装 `fonts-noto-cjk`（Debian/Ubuntu）或 `wqy-microhei` 后重启启动器。
- **自动更新第一次要手动填一次源（Windows）**：点右下「检查更新」会弹框让你填 `xyc-launcher/Launcher`，填一次就记进 `config.json`。不填则自动检查跳过。
- **自动更新可能被 GitHub 限流**：同一 IP 每小时只给 60 次 API 请求，报错就过一会儿再点。
- **微软登录要输设备码**：需跳浏览器手动输入一次性码，比网页回调多一步。
- **提示「未获取到 Minecraft 资料」**：这个微软账号没买 Java 版，先用离线模式。
- **首次启动要下 Java**：几十 MB，网速慢会停在下载阶段，等它跑完。
- **Forge / NeoForge 安装超时**：靠官方 installer，国内网络偶尔超时，重试通常就好。
- **整合包只认 `.mrpack`**：CurseForge 的 zip 格式需要先转换。
- **加载器版本列表缺版本**：走国内镜像，镜像没同步时会缺，换下载源或稍后刷新。
- **P2P 服务偏脆（Windows）**：有人连着不发请求会拖慢局域网互传。
- **游戏启动崩溃且日志提到 Java 版本**：老版本（1.12 及以前）必须用 Java 8，新版用 17/21；启动器会自动按版本准备，若手动指定过 `javaPath`，可在设置里改回「自动」。
- **想换游戏目录**：改 `config.json` 的 `gameDir`，或在同目录建 `.minecraft` 文件夹即可（默认就是它）。

## 安全提示

### 分享文件夹前

删掉 `config.json`，那是你的账号文件。登录令牌在新版里是加密存放的，密钥单独一个文件，所以整个启动器文件夹都别直接发出去。别接收别人发来的 `config.json`，也不要拿别人的配置覆盖自己的，用启动器自己生成的那份。

### 配置与执行

配置里的 Java 路径会被直接执行（Windows 只校验文件名是不是 `java.exe` 和 `-version` 的输出）。别用别人给的配置。

### 下载源

版本和加载器尽量从官方或 BMCLAPI 下，别为了快随手切到不认识的镜像。`mcimirror.com` 这类第三方镜像提供的是版本清单，选它就意味着装哪个客户端由它决定。Java 运行库和皮肤站插件下载时没做哈希校验，只靠 HTTPS 保证来源可信。

### P2P（Windows）

P2P 没有口令。同一局域网里连上端口就能取走游戏目录里的文件（日志、存档、别的启动器留下的账号库等，前提是猜对路径），还会广播这台机器的电脑名。

在公共 Wi-Fi、网吧、公司网络里别开着 P2P：那等于把游戏目录挂在局域网里给人看。不需要就把 `p2pEnabled` 关掉。首次启动会被 Windows 防火墙拦一下，选「专用网络」允许，否则同网段看不见对方。

### 证书回落（Windows）

见上方「证书回落」。装过企业代理 / 抓包工具根证书的机器，那些 HTTPS 连接也会被当成可信，介意就自己留意。

## 许可与声明

MIT License，见 [LICENSE](LICENSE)。Copyright (C) 2026 xycwww2025。

可以自由使用、修改、再分发，包括商业用途，也可闭源使用；只需保留版权声明和许可声明。不提供任何担保。

Minecraft 是 Mojang Studios 的商标。本项目为非官方工具，不提供游戏本体，与 Mojang Studios / Microsoft 无关联。
