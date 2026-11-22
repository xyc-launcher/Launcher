package com.mclauncher.main;

import com.mclauncher.core.Config;
import com.mclauncher.ui.LauncherFrame;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;

/** 程序入口：初始化外观/字体，加载配置并显示主界面。 */
public class Main {

    public static void main(String[] args) {
        try {
            // 用 Metal LAF：系统(Windows) LAF 会忽略大量 UIManager 与组件级深色配色，
            // 导致下拉弹出列表/滚动条/进度条/未选中页签等仍是浅色，颜色不统一。
            // Metal 尊重这些深色配置，能让全局深色真正生效。
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception ignore) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignore2) {}
        }

        installGlobalFont();
        setDarkUI();

        Path root = resolveRoot();
        Config cfg = Config.load(root);

        SwingUtilities.invokeLater(() -> {
            LauncherFrame frame = new LauncherFrame(cfg, root);
            frame.setVisible(true);
        });
    }

    /** 注入全局深色默认值，让弹窗/列表/下拉/滚动条/进度条/菜单等与深色界面协调（Metal LAF 下生效）。 */
    private static void setDarkUI() {
        java.awt.Color bg = new java.awt.Color(0x1e1e24);
        java.awt.Color card = new java.awt.Color(0x26262d);
        java.awt.Color ctrl = new java.awt.Color(0x2f2f37);
        java.awt.Color sel = new java.awt.Color(0x3a3a44);
        java.awt.Color fg = new java.awt.Color(0xe4e4e4);
        java.awt.Color sub = new java.awt.Color(0xaaaaaa);
        java.awt.Color caret = new java.awt.Color(0x9fe0a8);
        java.awt.Color border = new java.awt.Color(0x44444c);

        // 基础面板 / 标签 / 根窗
        UIManager.put("Panel.background", bg); UIManager.put("Panel.foreground", fg);
        UIManager.put("RootPane.background", bg);
        UIManager.put("OptionPane.background", bg); UIManager.put("OptionPane.messageForeground", fg);
        UIManager.put("OptionPane.buttonBackground", ctrl);
        UIManager.put("Label.foreground", fg);
        UIManager.put("InternalFrame.background", bg); UIManager.put("Desktop.background", bg);

        // 列表 / 下拉
        UIManager.put("List.background", card); UIManager.put("List.foreground", fg);
        UIManager.put("List.selectionBackground", sel); UIManager.put("List.selectionForeground", fg);
        UIManager.put("ComboBox.background", card); UIManager.put("ComboBox.foreground", fg);
        UIManager.put("ComboBox.selectionBackground", sel); UIManager.put("ComboBox.selectionForeground", fg);
        UIManager.put("ComboBox.buttonBackground", ctrl);
        UIManager.put("ComboBox.arrowBackground", ctrl);

        // 文本输入
        UIManager.put("TextField.background", card); UIManager.put("TextField.foreground", fg);
        UIManager.put("TextField.caretForeground", caret);
        UIManager.put("PasswordField.background", card); UIManager.put("PasswordField.foreground", fg);
        UIManager.put("PasswordField.caretForeground", caret);
        UIManager.put("FormattedTextField.background", card); UIManager.put("FormattedTextField.foreground", fg);
        UIManager.put("TextArea.background", new java.awt.Color(0x121216)); UIManager.put("TextArea.foreground", fg);
        UIManager.put("TextPane.background", card); UIManager.put("TextPane.foreground", fg);
        UIManager.put("EditorPane.background", card); UIManager.put("EditorPane.foreground", fg);

        // 滚动 / 视口
        UIManager.put("ScrollPane.background", bg); UIManager.put("ScrollPane.viewportBorder", null);
        UIManager.put("Viewport.background", bg);
        UIManager.put("ScrollBar.background", card); UIManager.put("ScrollBar.foreground", ctrl);
        UIManager.put("ScrollBar.track", card); UIManager.put("ScrollBar.thumb", ctrl);
        UIManager.put("ScrollBar.trackHighlight", ctrl); UIManager.put("ScrollBar.thumbHighlight", sel);

        // 页签 / 数字微调
        UIManager.put("TabbedPane.background", ctrl); UIManager.put("TabbedPane.foreground", fg);
        UIManager.put("TabbedPane.contentAreaColor", bg);
        UIManager.put("TabbedPane.selected", sel); UIManager.put("TabbedPane.unselectedBackground", ctrl);
        UIManager.put("Spinner.background", card); UIManager.put("Spinner.foreground", fg);
        UIManager.put("Spinner.arrowButtonBackground", ctrl);

        // 按钮（普通深灰；绿色启动/紫色皮肤站按钮在 LauncherFrame 里组件级覆盖）
        UIManager.put("Button.background", ctrl); UIManager.put("Button.foreground", fg);
        UIManager.put("Button.highlight", border); UIManager.put("Button.light", ctrl);
        UIManager.put("Button.shadow", new java.awt.Color(0x24242a)); UIManager.put("Button.darkShadow", border);
        UIManager.put("Button.select", sel); UIManager.put("Button.focus", new javax.swing.border.EmptyBorder(0,0,0,0));

        // 进度条
        UIManager.put("ProgressBar.background", card); UIManager.put("ProgressBar.foreground", new java.awt.Color(0x4caf50));
        UIManager.put("ProgressBar.selectionBackground", new java.awt.Color(0x121216)); UIManager.put("ProgressBar.selectionForeground", fg);

        // 表格 / 表头
        UIManager.put("Table.background", card); UIManager.put("Table.foreground", fg);
        UIManager.put("Table.selectionBackground", sel); UIManager.put("Table.selectionForeground", fg);
        UIManager.put("TableHeader.background", ctrl); UIManager.put("TableHeader.foreground", fg);

        // 提示 / 菜单 / 弹出
        UIManager.put("ToolTip.background", new java.awt.Color(0x2f2f37)); UIManager.put("ToolTip.foreground", fg);
        UIManager.put("MenuBar.background", bg); UIManager.put("MenuBar.foreground", fg);
        UIManager.put("Menu.background", bg); UIManager.put("Menu.foreground", fg);
        UIManager.put("Menu.selectionBackground", sel); UIManager.put("Menu.selectionForeground", fg);
        UIManager.put("MenuItem.background", card); UIManager.put("MenuItem.foreground", fg);
        UIManager.put("MenuItem.selectionBackground", sel); UIManager.put("MenuItem.selectionForeground", fg);
        UIManager.put("CheckBoxMenuItem.background", card); UIManager.put("CheckBoxMenuItem.foreground", fg);
        UIManager.put("RadioButtonMenuItem.background", card); UIManager.put("RadioButtonMenuItem.foreground", fg);
        UIManager.put("PopupMenu.background", card); UIManager.put("PopupMenu.foreground", fg);
    }

    private static void installGlobalFont() {
        // 优先微软雅黑（Windows 上消除中文乱码），不存在则回退系统默认字体
        String[] candidates = {"微软雅黑", "Microsoft YaHei", "Noto Sans CJK SC", "PingFang SC", "SansSerif"};
        Font base = null;
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, 13);
            if (!"Dialog".equals(f.getFamily()) || "SansSerif".equals(name)) { base = f; break; }
        }
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof Font) {
                Font old = (Font) value;
                UIManager.put(key, new Font(base.getFamily(), old.getStyle(), old.getSize()));
            }
        }
    }

    private static Path resolveRoot() {
        try {
            File jar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // jar 在目录中运行时取其父目录；class 目录运行时取工作目录
            if (jar.isFile()) return jar.getAbsoluteFile().getParentFile().toPath();
        } catch (URISyntaxException ignore) {}
        return Paths.get(System.getProperty("user.dir"));
    }
}
