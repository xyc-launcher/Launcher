package com.mclauncher.ui;

import com.mclauncher.core.*;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;

/** xyc Launcher 主界面：深色头部 + 卡片 + 三个功能分页 + 日志区。 */
public class LauncherFrame extends JFrame {

    /* ---- 深色主题配色 ---- */
    private static final Color HEAD = new Color(0x141418);
    private static final Color BG = new Color(0x1e1e24);
    private static final Color CARD = new Color(0x26262d);
    private static final Color CTRL = new Color(0x2f2f37);
    private static final Color CTRL_HOVER = new Color(0x3a3a44);
    private static final Color FG = new Color(0xe4e4e4);
    private static final Color SUB = new Color(0xaaaaaa);
    private static final Color GREEN = new Color(0x4caf50);
    private static final Color PURPLE = new Color(0x7e57c2);
    private static final Color LOG_BG = new Color(0x121216);
    private static final Color LOG_FG = new Color(0x9fe0a8);

    private final Config cfg;
    private final Path root;

    private final DefaultListModel<Account> accountModel = new DefaultListModel<>();
    private JList<Account> accountList;

    private final DefaultComboBoxModel<VersionManager.VersionEntry> versionModel = new DefaultComboBoxModel<>();
    private JComboBox<VersionManager.VersionEntry> versionBox;
    private JComboBox<String> loaderKindBox, loaderVersionBox;
    private JTextField packSearchField;
    private DefaultListModel<ModpackManager.SearchHit> packModel = new DefaultListModel<>();
    private JList<ModpackManager.SearchHit> packList;

    private JSpinner memorySpinner;
    private JComboBox<String> mirrorBox, javaModeBox;
    private JButton launchBtn;
    private JTextArea logArea;
    private JLabel statusLabel;

    private ModpackManager.InstalledPack currentPack;

    public LauncherFrame(Config cfg, Path root) {
        super("xyc Launcher");
        this.cfg = cfg;
        this.root = root;
        buildUI();
        loadAccounts();
        refreshManifest();
    }

    private void buildUI() {
        setSize(1040, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(BG);

        // 头部
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEAD);
        header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        JLabel title = new JLabel("xyc Launcher");
        title.setFont(new Font("微软雅黑", Font.BOLD, 26));
        title.setForeground(GREEN);
        JLabel sub = new JLabel("  Minecraft Java 版启动器 · 下载 / 加载器 / 整合包");
        sub.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        sub.setForeground(SUB);
        JPanel titleWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleWrap.setOpaque(false);
        titleWrap.add(title); titleWrap.add(sub);
        header.add(titleWrap, BorderLayout.WEST);
        rootPanel.add(header, BorderLayout.NORTH);

        // 主体：左账号 + 右内容
        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        body.setBackground(BG);
        body.add(buildAccountPanel(), BorderLayout.WEST);
        body.add(buildCenter(), BorderLayout.CENTER);
        rootPanel.add(body, BorderLayout.CENTER);

        // 底部状态 + 日志
        logArea = new JTextArea(8, 20);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(LOG_BG);
        logArea.setForeground(LOG_FG);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBackground(BG);
        logScroll.getViewport().setBackground(LOG_BG);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0x33333a)), "运行日志",
                0, 0, new Font("微软雅黑", Font.PLAIN, 12), SUB));
        rootPanel.add(logScroll, BorderLayout.SOUTH);

        setContentPane(rootPanel);
    }

    private JPanel buildAccountPanel() {
        JPanel p = cardPanel();
        p.setPreferredSize(new Dimension(280, 0));
        JLabel head = sectionLabel("账号");
        p.add(head);

        accountList = new JList<>(accountModel);
        accountList.setBackground(CARD);
        accountList.setForeground(FG);
        accountList.setSelectionBackground(CTRL_HOVER);
        accountList.setSelectionForeground(FG);
        accountList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                                    boolean s, boolean f) {
                Account a = (Account) v;
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                c.setText("<html><b>" + a.name + "</b><br><font color=#888888>" + a.displayType() + "</font></html>");
                c.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                c.setOpaque(true);
                c.setBackground(s ? CTRL_HOVER : CARD);
                c.setForeground(FG);
                return c;
            }
        });
        JScrollPane sp = new JScrollPane(accountList);
        sp.setBackground(BG);
        sp.getViewport().setBackground(CARD);
        sp.setPreferredSize(new Dimension(250, 220));
        p.add(sp);

        JButton offline = greenish("添加离线账号");
        JButton ms = greenish("微软正版登录");
        JButton skin = purple("皮肤站登录");
        JButton del = plain("删除选中账号");
        offline.addActionListener(e -> addOffline());
        ms.addActionListener(e -> addMicrosoft());
        skin.addActionListener(e -> addSkinStation());
        del.addActionListener(e -> deleteAccount());
        for (JButton b : new JButton[]{offline, ms, skin, del}) p.add(b);
        return p;
    }

    private JPanel buildCenter() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        tabs.setBackground(CTRL);
        tabs.setForeground(FG);
        tabs.addTab("原版游戏", buildVanillaTab());
        tabs.addTab("Mod 加载器", buildLoaderTab());
        tabs.addTab("整合包", buildModpackTab());
        for (int i = 0; i < tabs.getTabCount(); i++) tabs.setBackgroundAt(i, CTRL);
        p.add(tabs, BorderLayout.CENTER);

        // 设置 + 启动
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        bottom.setBackground(CARD);
        memorySpinner = new JSpinner(new SpinnerNumberModel(cfg.memoryMb, 512, 16384, 256));
        darkCombo(memorySpinner);
        mirrorBox = new JComboBox<>(new String[]{"bmclapi 国内镜像", "mojang 官方"});
        mirrorBox.setSelectedIndex("mojang".equals(cfg.downloadMirror) ? 1 : 0);
        javaModeBox = new JComboBox<>(new String[]{"自动：按游戏版本要求（推荐）", "自动：使用最新版 Java", "固定 Java 17", "固定 Java 21"});
        int jmi = cfg.javaMajor == 0 ? 1 : (cfg.javaMajor == 17 ? 2 : (cfg.javaMajor == 21 ? 3 : 0));
        javaModeBox.setSelectedIndex(jmi);
        dark(bottom, memorySpinner, mirrorBox, javaModeBox);
        JLabel lblMem = new JLabel("内存(MB):"); lblMem.setForeground(FG);
        JLabel lblSrc = new JLabel("下载源:"); lblSrc.setForeground(FG);
        JLabel lblJava = new JLabel("Java:"); lblJava.setForeground(FG);
        bottom.add(lblMem); bottom.add(memorySpinner);
        bottom.add(lblSrc); bottom.add(mirrorBox);
        bottom.add(lblJava); bottom.add(javaModeBox);
        JButton verifyBtn = plain("校验文件完整性");
        verifyBtn.addActionListener(e -> verifyFiles());
        bottom.add(verifyBtn);

        launchBtn = new JButton("启动游戏");
        launchBtn.setFont(new Font("微软雅黑", Font.BOLD, 16));
        launchBtn.setBackground(GREEN);
        launchBtn.setForeground(Color.WHITE);
        launchBtn.setFocusPainted(false);
        launchBtn.setPreferredSize(new Dimension(160, 42));
        launchBtn.addActionListener(e -> launch());
        bottom.add(launchBtn);

        statusLabel = new JLabel("就绪");
        statusLabel.setForeground(SUB);
        JPanel statusWrap = new JPanel(new BorderLayout());
        statusWrap.setOpaque(false);
        statusWrap.add(bottom, BorderLayout.NORTH);
        statusWrap.add(statusLabel, BorderLayout.SOUTH);
        p.add(statusWrap, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildVanillaTab() {
        JPanel p = cardPanel();
        p.add(sectionLabel("选择游戏版本"));
        versionBox = new JComboBox<>(versionModel);
        versionBox.setPreferredSize(new Dimension(360, 32));
        dark(versionBox);
        p.add(versionBox);
        JButton refresh = plain("刷新版本列表");
        refresh.addActionListener(e -> refreshManifest());
        p.add(refresh);
        return p;
    }

    private JPanel buildLoaderTab() {
        JPanel p = cardPanel();
        p.add(sectionLabel("选择 Mod 加载器（随启动自动安装）"));
        loaderKindBox = new JComboBox<>(new String[]{"Fabric", "Quilt", "Forge", "NeoForge"});
        loaderVersionBox = new JComboBox<>();
        dark(loaderKindBox, loaderVersionBox);
        loaderKindBox.addActionListener(e -> loadLoaderVersions());
        p.add(loaderKindBox);
        p.add(loaderVersionBox);
        JButton load = plain("加载加载器版本");
        load.addActionListener(e -> loadLoaderVersions());
        p.add(load);
        return p;
    }

    private JPanel buildModpackTab() {
        JPanel p = cardPanel();
        p.add(sectionLabel("搜索 Modrinth 整合包"));
        JPanel search = new JPanel(new BorderLayout(6, 0));
        search.setOpaque(false);
        packSearchField = new JTextField();
        packSearchField.setBackground(CARD);
        packSearchField.setForeground(FG);
        packSearchField.setCaretColor(LOG_FG);
        JButton searchBtn = plain("搜索");
        searchBtn.addActionListener(e -> searchPacks());
        search.add(packSearchField, BorderLayout.CENTER);
        search.add(searchBtn, BorderLayout.EAST);
        p.add(search);

        packList = new JList<>(packModel);
        packList.setBackground(CARD);
        packList.setForeground(FG);
        packList.setSelectionBackground(CTRL_HOVER);
        packList.setSelectionForeground(FG);
        packList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                ModpackManager.SearchHit h = (ModpackManager.SearchHit) v;
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, s, f);
                c.setText("<html><b>" + h.title + "</b><br><font color=#888888>" + h.description + "</font></html>");
                c.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                c.setOpaque(true);
                c.setBackground(s ? CTRL_HOVER : CARD);
                c.setForeground(FG);
                return c;
            }
        });
        JScrollPane sp = new JScrollPane(packList);
        sp.setBackground(BG);
        sp.getViewport().setBackground(CARD);
        sp.setPreferredSize(new Dimension(420, 220));
        p.add(sp);
        JButton install = greenish("下载并安装选中整合包");
        install.addActionListener(e -> installPack());
        p.add(install);
        return p;
    }

    /* ---------- 账号 ---------- */
    private void loadAccounts() {
        accountModel.clear();
        for (var m : cfg.accounts) accountModel.addElement(Account.fromMap(m));
        if (cfg.selectedAccount >= 0 && cfg.selectedAccount < accountModel.size())
            accountList.setSelectedIndex(cfg.selectedAccount);
    }

    private void persistAccounts() {
        cfg.accounts.clear();
        for (int i = 0; i < accountModel.size(); i++) cfg.accounts.add(accountModel.get(i).toMap());
        cfg.selectedAccount = accountList.getSelectedIndex();
        cfg.save();
    }

    private void addOffline() {
        String name = JOptionPane.showInputDialog(this, "输入离线玩家昵称：", "Player");
        if (name == null || name.trim().isEmpty()) return;
        accountModel.addElement(Account.offline(name.trim()));
        persistAccounts();
    }

    private void addMicrosoft() {
        setBusy(true);
        new Thread(() -> {
            try {
                Account a = MicrosoftAuth.login(cfg,
                    (code, uri, exp, interval) -> SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this,
                            "请在浏览器打开 " + uri + "\n输入代码：" + code + "\n完成后回到本程序等待。",
                            "微软设备码登录", JOptionPane.INFORMATION_MESSAGE)),
                    remaining -> setStatus("等待授权，剩余 " + remaining + " 秒"));
                SwingUtilities.invokeLater(() -> { accountModel.addElement(a); persistAccounts(); log("微软登录成功：" + a.name); });
            } catch (Exception e) {
                final String msg = errText(e);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "微软登录失败：" + msg, "登录失败", JOptionPane.ERROR_MESSAGE));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    private void addSkinStation() {
        JTextField api = new JTextField("https://littleskin.cn/api/yggdrasil");
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        JPanel dlg = new JPanel(new GridLayout(0, 1));
        dlg.setBackground(BG);
        JLabel l1 = new JLabel("Yggdrasil API："); l1.setForeground(FG); dlg.add(l1); dlg.add(api);
        JLabel l2 = new JLabel("账号："); l2.setForeground(FG); dlg.add(l2); dlg.add(user);
        JLabel l3 = new JLabel("密码："); l3.setForeground(FG); dlg.add(l3); dlg.add(pass);
        if (JOptionPane.showConfirmDialog(this, dlg, "皮肤站登录", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        setBusy(true);
        new Thread(() -> {
            try {
                Account a = SkinStationAuth.login(api.getText(), user.getText(), new String(pass.getPassword()));
                SwingUtilities.invokeLater(() -> { accountModel.addElement(a); persistAccounts(); log("皮肤站登录成功：" + a.name); });
            } catch (Exception e) {
                final String msg = errText(e);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                    "皮肤站登录失败：" + msg, "登录失败", JOptionPane.ERROR_MESSAGE));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    private void deleteAccount() {
        int i = accountList.getSelectedIndex();
        if (i >= 0) { accountModel.remove(i); persistAccounts(); }
    }

    /* ---------- 版本 / 加载器 ---------- */
    private void refreshManifest() {
        setBusy(true);
        new Thread(() -> {
            try {
                List<VersionManager.VersionEntry> list = VersionManager.fetchManifest(cfg.manifestUrl());
                SwingUtilities.invokeLater(() -> {
                    versionModel.removeAllElements();
                    for (var e : list) versionModel.addElement(e);
                    log("版本列表已加载，共 " + list.size() + " 个");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> log("版本列表加载失败：" + errText(e)));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    private void loadLoaderVersions() {
        final String kind = selectedLoaderKind();
        final VersionManager.VersionEntry entry = (VersionManager.VersionEntry) versionBox.getSelectedItem();
        final String mc = entry != null ? entry.id : cfg.selectedVersion;
        setBusy(true);
        new Thread(() -> {
            try {
                List<String> versions = ModLoaderManager.listLoaderVersions(kind, mc);
                SwingUtilities.invokeLater(() -> {
                    loaderVersionBox.removeAllItems();
                    for (String v : versions) loaderVersionBox.addItem(v);
                    log(kind + " 版本已加载（MC " + mc + "），共 " + versions.size() + " 个");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, errText(e)));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    /** 加载器下拉 → 内部 kind 标识。 */
    private String selectedLoaderKind() {
        int i = loaderKindBox == null ? 0 : loaderKindBox.getSelectedIndex();
        switch (i) {
            case 1: return ModLoaderManager.QUILT;
            case 2: return ModLoaderManager.FORGE;
            case 3: return ModLoaderManager.NEOFORGE;
            default: return ModLoaderManager.FABRIC;
        }
    }

    /* ---------- 整合包 ---------- */
    private void searchPacks() {
        String q = packSearchField.getText();
        setBusy(true);
        new Thread(() -> {
            try {
                List<ModpackManager.SearchHit> hits = ModpackManager.search(q, 20);
                SwingUtilities.invokeLater(() -> {
                    packModel.clear();
                    for (var h : hits) packModel.addElement(h);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, errText(e)));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    private void installPack() {
        ModpackManager.SearchHit hit = packList.getSelectedValue();
        if (hit == null) { JOptionPane.showMessageDialog(this, "请先选择一个整合包"); return; }
        setBusy(true);
        new Thread(() -> {
            try {
                List<ModpackManager.PackVersion> versions = ModpackManager.listVersions(hit.projectId);
                if (versions.isEmpty()) throw new Exception("该整合包没有可用版本");
                ModpackManager.PackVersion pv = versions.get(0);
                ModpackManager.InstalledPack pack = ModpackManager.install(cfg, hit, pv, uiListener());
                SwingUtilities.invokeLater(() -> {
                    currentPack = pack;
                    log("整合包安装完成：" + pack.title + " MC=" + pack.mcVersion
                        + (pack.loaderKind != null ? " 加载器=" + pack.loaderVersion : ""));
                    JOptionPane.showMessageDialog(this, "整合包安装完成，点击启动游戏即可运行");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, errText(e), "安装失败", JOptionPane.ERROR_MESSAGE));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    /* ---------- 校验 ---------- */
    private void verifyFiles() {
        VersionManager.VersionEntry entry = (VersionManager.VersionEntry) versionBox.getSelectedItem();
        String id = entry == null ? cfg.selectedVersion : entry.id;
        if (id == null || id.isEmpty()) { JOptionPane.showMessageDialog(this, "请先选择版本"); return; }
        setBusy(true);
        new Thread(() -> {
            try {
                int fixed = Downloader.verify(cfg, id, uiListener());
                SwingUtilities.invokeLater(() -> log("完整性校验完成，修复文件数：" + fixed));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, errText(e)));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    /* ---------- 启动 ---------- */
    private void launch() {
        int idx = accountList.getSelectedIndex();
        if (idx < 0) { JOptionPane.showMessageDialog(this, "请先选择或添加账号"); return; }
        Account account = accountModel.get(idx);
        if (account.tokenExpired()) {
            log("微软令牌已过期，正在刷新…");
            try {
                Account refreshed = MicrosoftAuth.refresh(cfg, account);
                accountModel.set(idx, refreshed);
                account = refreshed;
                persistAccounts();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "令牌刷新失败，请重新登录：" + errText(e));
                return;
            }
        }

        cfg.memoryMb = (Integer) memorySpinner.getValue();
        cfg.downloadMirror = mirrorBox.getSelectedIndex() == 1 ? "mojang" : "bmclapi";
        int javaMode = javaModeBox.getSelectedIndex();
        cfg.javaMajor = javaMode == 0 ? -1 : (javaMode == 1 ? 0 : (javaMode == 2 ? 17 : 21));
        cfg.save();

        setBusy(true);
        final Account acc = account;
        new Thread(() -> {
            try {
                String mcId;
                String jsonUrl;
                ModLoaderManager.ModProfile modProfile = null;

                if (currentPack != null) {
                    mcId = currentPack.mcVersion;
                    jsonUrl = resolveJsonUrl(mcId);
                } else {
                    VersionManager.VersionEntry entry = (VersionManager.VersionEntry) versionBox.getSelectedItem();
                    if (entry == null) throw new Exception("请先选择游戏版本");
                    mcId = entry.id;
                    jsonUrl = entry.url;
                }
                cfg.selectedVersion = mcId;
                Downloader.Result result = Downloader.install(cfg, mcId, jsonUrl, uiListener());

                // 加载器：整合包指定，或手动选择
                String loaderKind = currentPack != null ? currentPack.loaderKind
                        : (loaderVersionBox != null && loaderVersionBox.getItemCount() > 0
                           && loaderVersionBox.getSelectedItem() != null ? selectedLoaderKind() : null);
                String loaderVer = currentPack != null ? currentPack.loaderVersion
                        : (loaderVersionBox != null && loaderVersionBox.getSelectedItem() != null
                           ? loaderVersionBox.getSelectedItem().toString() : null);
                if (loaderKind != null && loaderVer != null) {
                    if (ModLoaderManager.isInstallerBased(loaderKind)) {
                        // Forge / NeoForge：跑官方安装器 → 生成继承版本 → 再按继承合并父版本
                        Path javaExe = JavaRuntime.forVersion(cfg, result.javaVersionMajor, uiListener());
                        ModLoaderManager.ModProfile forge = ModLoaderManager.installForge(
                                cfg, loaderKind, mcId, loaderVer, javaExe, uiListener());
                        result = Downloader.installLocal(cfg, forge.installedVersionId, uiListener());
                        modProfile = null;
                        log(loaderKind + " " + loaderVer + " 已就绪");
                    } else {
                        modProfile = ModLoaderManager.install(cfg, loaderKind, mcId, loaderVer, uiListener());
                    }
                }

                Path gameDir = currentPack != null ? currentPack.instanceDir : null;
                GameLauncher launcher = new GameLauncher(cfg, acc, result, modProfile, gameDir);
                SwingUtilities.invokeLater(() -> log("正在启动游戏…"));
                Process p = launcher.start(this::log);
                new Thread(() -> {
                    try { int code = p.waitFor(); SwingUtilities.invokeLater(() -> log("游戏进程退出，代码 " + code)); }
                    catch (InterruptedException ignore) {}
                }).start();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, errText(e), "启动失败", JOptionPane.ERROR_MESSAGE));
            } finally { SwingUtilities.invokeLater(() -> setBusy(false)); }
        }).start();
    }

    private String resolveJsonUrl(String mcId) throws Exception {
        List<VersionManager.VersionEntry> list = VersionManager.fetchManifest(cfg.manifestUrl());
        VersionManager.VersionEntry e = VersionManager.find(list, mcId);
        if (e == null) throw new Exception("清单中找不到版本 " + mcId);
        return e.url;
    }

    /* ---------- 通用 ---------- */
    private Downloader.Listener uiListener() {
        return new Downloader.Listener() {
            @Override public void status(String m) { SwingUtilities.invokeLater(() -> setStatus(m)); }
            @Override public void progress(int done, int total) {
                SwingUtilities.invokeLater(() -> setStatus("进度 " + done + "/" + total));
            }
        };
    }

    /** 错误消息为 null（如 NPE）时回退到异常类型名，避免界面显示 "null"。 */
    private String errText(Exception e) {
        if (e == null) return "未知错误";
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }

    private void setBusy(boolean busy) {
        launchBtn.setEnabled(!busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void setStatus(String s) { statusLabel.setText(s); }
    private void log(String s) { logArea.append(s + "\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); }

    private JPanel cardPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        return p;
    }

    private JLabel sectionLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("微软雅黑", Font.BOLD, 15));
        l.setForeground(new Color(0xeeeeee));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** 给任意组件统一深色。 */
    private void dark(JComponent... cs) {
        for (JComponent c : cs) { c.setBackground(CARD); c.setForeground(FG); }
    }

    /** 下拉/微调框连编辑器一起深色。 */
    private void darkCombo(JComponent c) {
        c.setBackground(CARD); c.setForeground(FG);
        if (c instanceof JComboBox) {
            JComboBox<?> cb = (JComboBox<?>) c;
            if (cb.getEditor() != null && cb.getEditor().getEditorComponent() instanceof JComponent ec) {
                ec.setBackground(CARD); ec.setForeground(FG);
            }
        } else if (c instanceof JSpinner && ((JSpinner) c).getEditor() instanceof JSpinner.DefaultEditor) {
            JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) ((JSpinner) c).getEditor();
            ed.getTextField().setBackground(CARD);
            ed.getTextField().setForeground(FG);
        }
    }

    private JButton greenish(String t) {
        JButton b = new JButton(t);
        b.setBackground(GREEN); b.setForeground(Color.WHITE); b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }
    private JButton purple(String t) {
        JButton b = new JButton(t);
        b.setBackground(PURPLE); b.setForeground(Color.WHITE); b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }
    private JButton plain(String t) {
        JButton b = new JButton(t);
        b.setBackground(CTRL); b.setForeground(FG);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(0x44444c)));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }
}
