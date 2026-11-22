using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using System.Windows.Forms;
using Microsoft.Win32;

// Token: 0x02000002 RID: 2
internal static class Program
{
    // Token: 0x06000001 RID: 1 RVA: 0x00002050 File Offset: 0x00000250
    private static void Main()
    {
        Program.exeDir = Path.GetDirectoryName(Application.ExecutablePath);
        try
        {
            Program.jarPath = Program.ResolveJar();
        }
        catch (Exception ex)
        {
            MessageBox.Show("启动器资源解包失败：" + ex.Message, "xyc Launcher", MessageBoxButtons.OK, MessageBoxIcon.Hand);
            return;
        }
        int num = -1;
        string text = Program.FindJava(out num);
        if (text == null)
        {
            text = Program.AutoFetchJava(out num);
        }
        if (Environment.GetEnvironmentVariable("XYC_DEBUG") != null)
        {
            try
            {
                File.WriteAllText(Path.Combine(Program.exeDir, "_java_probe.txt"), string.Concat(new object[]
                {
                    "path=",
                    (text == null) ? "(none)" : text,
                    "\r\nfoundMajor=",
                    num,
                    "\r\n"
                }));
            }
            catch
            {
            }
        }
        if (text == null)
        {
            string text2;
            if (num > 0)
            {
                text2 = "检测到的 Java 版本为 " + num + "，但本启动器需要 Java 8 或更高。\n\n请安装 Java 8 / 17 / 21 / 25（Eclipse Temurin、BellSoft Liberica、Amazon Corretto、Microsoft OpenJDK、Zulu 均可），装完重新双击本程序即可。";
            }
            else
            {
                text2 = "自动下载 Java 运行时失败，本机也没有可用的 Java。\n\n请手动安装 Java 8 或更高版本（推荐 17 / 21 / 25），安装完成后重新双击本程序。";
            }
            MessageBox.Show(text2, "xyc Launcher", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
        }
        else
        {
            try
            {
                ProcessStartInfo processStartInfo = new ProcessStartInfo();
                processStartInfo.FileName = text;
                processStartInfo.Arguments = "-jar \"" + Program.jarPath + "\"";
                processStartInfo.WorkingDirectory = Program.exeDir;
                processStartInfo.UseShellExecute = false;
                processStartInfo.CreateNoWindow = true;
                processStartInfo.EnvironmentVariables["XYC_HOME"] = Program.exeDir;
                try
                {
                    processStartInfo.EnvironmentVariables["XYC_EXE"] = Process.GetCurrentProcess().MainModule.FileName;
                }
                catch
                {
                    try
                    {
                        processStartInfo.EnvironmentVariables["XYC_EXE"] = Application.ExecutablePath;
                    }
                    catch
                    {
                    }
                }
                Process.Start(processStartInfo);
            }
            catch (Exception ex)
            {
                MessageBox.Show("启动失败：" + ex.Message, "xyc Launcher", MessageBoxButtons.OK, MessageBoxIcon.Hand);
            }
        }
    }

    // Token: 0x06000002 RID: 2 RVA: 0x000022A4 File Offset: 0x000004A4
    private static string ResolveJar()
    {
        string text = Path.Combine(Program.exeDir, "launcher.jar");
        string text2;
        if (File.Exists(text))
        {
            text2 = text;
        }
        else
        {
            byte[] array = Program.ReadEmbeddedJar();
            if (array == null || array.Length == 0)
            {
                throw new Exception("程序内没有找到内嵌的 launcher.jar");
            }
            string text3;
            using (SHA1 sha = new SHA1Managed())
            {
                byte[] array2 = sha.ComputeHash(array);
                text3 = BitConverter.ToString(array2).Replace("-", "").Substring(0, 16)
                    .ToLowerInvariant();
            }
            string text4 = Path.Combine(Path.GetTempPath(), "xyclauncher");
            Directory.CreateDirectory(text4);
            string text5 = Path.Combine(text4, "launcher-" + text3 + ".jar");
            if (!File.Exists(text5) || new FileInfo(text5).Length != (long)array.Length)
            {
                string text6 = string.Concat(new object[]
                {
                    text5,
                    ".",
                    Process.GetCurrentProcess().Id,
                    ".tmp"
                });
                File.WriteAllBytes(text6, array);
                if (File.Exists(text5))
                {
                    File.Delete(text5);
                }
                File.Move(text6, text5);
            }
            text2 = text5;
        }
        return text2;
    }

    // Token: 0x06000003 RID: 3 RVA: 0x00002424 File Offset: 0x00000624
    private static byte[] ReadEmbeddedJar()
    {
        byte[] array;
        using (Stream manifestResourceStream = typeof(Program).Assembly.GetManifestResourceStream("launcher.jar"))
        {
            if (manifestResourceStream == null)
            {
                array = null;
            }
            else
            {
                using (MemoryStream memoryStream = new MemoryStream())
                {
                    byte[] array2 = new byte[8192];
                    int num;
                    while ((num = manifestResourceStream.Read(array2, 0, array2.Length)) > 0)
                    {
                        memoryStream.Write(array2, 0, num);
                    }
                    array = memoryStream.ToArray();
                }
            }
        }
        return array;
    }

    // Token: 0x06000004 RID: 4 RVA: 0x000024E0 File Offset: 0x000006E0
    private static string AutoFetchJava(out int foundMajor)
    {
        foundMajor = -1;
        string text = Path.Combine(Program.exeDir, "runtime");
        string text2 = Program.FindJavaInTree(text, out foundMajor);
        string text3;
        if (text2 != null)
        {
            text3 = text2;
        }
        else
        {
            bool is64BitOperatingSystem = Environment.Is64BitOperatingSystem;
            string[] array = new string[]
            {
                "https://cdn.azul.com/zulu/bin/zulu8.98.0.201-ca-jre8.0.514-win_" + (is64BitOperatingSystem ? "x64" : "i686") + ".zip",
                "https://api.adoptium.net/v3/binary/latest/8/ga/windows/" + (is64BitOperatingSystem ? "x64" : "x86") + "/jre/hotspot/normal/eclipse"
            };
            MessageBox.Show("这台电脑上没有检测到 Java，启动器会自动下载一个内置 Java 8 运行时（约 40MB，视网速 1~3 分钟）。\n\n下载完成后会自动打开启动器，不需要你手动安装 Java。", "xyc Launcher", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
            Exception ex = null;
            foreach (string text4 in array)
            {
                string text5 = Path.Combine(Program.exeDir, "runtime_download.zip");
                try
                {
                    if (File.Exists(text5))
                    {
                        File.Delete(text5);
                    }
                    using (WebClient webClient = new WebClient())
                    {
                        webClient.Headers.Add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) xycLauncher/2.0");
                        webClient.DownloadFile(text4, text5);
                    }
                    if (!File.Exists(text5) || new FileInfo(text5).Length < 5242880L)
                    {
                        throw new IOException("下载的文件不完整");
                    }
                    if (!Directory.Exists(text))
                    {
                        Directory.CreateDirectory(text);
                    }
                    ZipFile.ExtractToDirectory(text5, text);
                    try
                    {
                        File.Delete(text5);
                    }
                    catch
                    {
                    }
                    string text6 = Program.FindJavaInTree(text, out foundMajor);
                    if (text6 != null)
                    {
                        return text6;
                    }
                    ex = new IOException("解压完成但没找到 java.exe");
                }
                catch (Exception ex2)
                {
                    ex = ex2;
                }
            }
            MessageBox.Show("自动下载 Java 失败：" + ((ex == null) ? "未知原因" : ex.Message) + "\n\n可以手动安装 Java 8 或更高版本后再双击本程序。", "xyc Launcher", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
            text3 = null;
        }
        return text3;
    }

    // Token: 0x06000005 RID: 5 RVA: 0x00002718 File Offset: 0x00000918
    private static string FindJavaInTree(string root, out int majorOut)
    {
        majorOut = -1;
        string text = null;
        try
        {
            if (!Directory.Exists(root))
            {
                return null;
            }
            foreach (string text2 in Directory.GetFiles(root, "java.exe", SearchOption.AllDirectories))
            {
                string text3 = text2.Replace('\\', '/').ToLowerInvariant();
                if (text3.EndsWith("/bin/java.exe"))
                {
                    int num = Program.JavaMajor(text2);
                    if (num >= 8 && num > majorOut)
                    {
                        majorOut = num;
                        text = text2;
                    }
                }
            }
        }
        catch
        {
        }
        return text;
    }

    // Token: 0x06000006 RID: 6 RVA: 0x000027D8 File Offset: 0x000009D8
    private static string FindJava(out int bestPathMajor)
    {
        bestPathMajor = -1;
        HashSet<string> hashSet = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        Program.AddEnv(hashSet, "JAVA_HOME");
        Program.AddEnv(hashSet, "JDK_HOME");
        Program.ScanRuntime(hashSet, Path.Combine(Program.exeDir, ".minecraft", "runtime"));
        string environmentVariable = Environment.GetEnvironmentVariable("PATH");
        if (!string.IsNullOrEmpty(environmentVariable))
        {
            foreach (string text in environmentVariable.Split(new char[] { ';' }))
            {
                if (!string.IsNullOrEmpty(text))
                {
                    string text2 = Path.Combine(text.Trim(), "java.exe");
                    if (File.Exists(text2))
                    {
                        hashSet.Add(text2);
                    }
                }
            }
        }
        Program.AddRegistryJavaSoft(hashSet);
        Program.AddRegistryUninstall(hashSet);
        foreach (string text3 in Program.KnownRoots())
        {
            Program.ScanTree(text3, 3, hashSet);
        }
        foreach (string text4 in Program.DriveRoots())
        {
            Program.ScanTree(text4, 2, hashSet);
        }
        Program.AddFromWhere(hashSet);
        string text5 = null;
        int num = -1;
        int num2 = -1;
        foreach (string text6 in hashSet)
        {
            if (!string.IsNullOrEmpty(text6) && File.Exists(text6))
            {
                int num3 = Program.JavaMajor(text6);
                if (num3 > num2)
                {
                    num2 = num3;
                }
                if (num3 >= 8 && num3 > num)
                {
                    num = num3;
                    text5 = text6;
                }
            }
        }
        if (num2 > bestPathMajor)
        {
            bestPathMajor = num2;
        }
        return text5;
    }

    // Token: 0x06000007 RID: 7 RVA: 0x00002A30 File Offset: 0x00000C30
    private static void AddEnv(HashSet<string> sink, string name)
    {
        string environmentVariable = Environment.GetEnvironmentVariable(name);
        if (!string.IsNullOrEmpty(environmentVariable))
        {
            string text = Path.Combine(environmentVariable.Trim().Trim(new char[] { '"' }), "bin", "java.exe");
            if (File.Exists(text))
            {
                sink.Add(text);
            }
        }
    }

    // Token: 0x06000008 RID: 8 RVA: 0x00002A94 File Offset: 0x00000C94
    private static void ScanRuntime(HashSet<string> sink, string rt)
    {
        if (Directory.Exists(rt))
        {
            Program.ScanTree(rt, 3, sink);
        }
    }

    // Token: 0x06000009 RID: 9 RVA: 0x00002AB8 File Offset: 0x00000CB8
    private static void ScanTree(string dir, int depth, HashSet<string> sink)
    {
        if (depth >= 0 && !string.IsNullOrEmpty(dir) && Directory.Exists(dir))
        {
            try
            {
                string text = Path.Combine(dir, "java.exe");
                if (File.Exists(text) && Program.IsBinDir(dir))
                {
                    sink.Add(text);
                }
                string text2 = Path.Combine(dir, "bin", "java.exe");
                if (File.Exists(text2))
                {
                    sink.Add(text2);
                }
                bool flag = Program.Javaish.IsMatch(Path.GetFileName(dir));
                string[] directories = Directory.GetDirectories(dir);
                foreach (string text3 in directories)
                {
                    string fileName = Path.GetFileName(text3);
                    if (flag || Program.Javaish.IsMatch(fileName) || depth >= 2)
                    {
                        Program.ScanTree(text3, depth - 1, sink);
                    }
                    else
                    {
                        Program.ScanTree(text3, 0, sink);
                    }
                }
            }
            catch
            {
            }
        }
    }

    // Token: 0x0600000A RID: 10 RVA: 0x00002BD8 File Offset: 0x00000DD8
    private static bool IsBinDir(string dir)
    {
        return string.Equals(Path.GetFileName(dir.TrimEnd(new char[] { '\\' })), "bin", StringComparison.OrdinalIgnoreCase);
    }

    // Token: 0x0600000B RID: 11 RVA: 0x00002C10 File Offset: 0x00000E10
    private static IEnumerable<string> KnownRoots()
    {
        string environmentVariable = Environment.GetEnvironmentVariable("ProgramFiles");
        string environmentVariable2 = Environment.GetEnvironmentVariable("ProgramFiles(x86)");
        string environmentVariable3 = Environment.GetEnvironmentVariable("ProgramW6432");
        string environmentVariable4 = Environment.GetEnvironmentVariable("LOCALAPPDATA");
        string environmentVariable5 = Environment.GetEnvironmentVariable("USERPROFILE");
        string environmentVariable6 = Environment.GetEnvironmentVariable("ProgramData");
        List<string> list = new List<string>();
        Program.AddRoot(list, environmentVariable, "Java");
        Program.AddRoot(list, environmentVariable2, "Java");
        Program.AddRoot(list, environmentVariable, "Eclipse Adoptium");
        Program.AddRoot(list, environmentVariable2, "Eclipse Adoptium");
        Program.AddRoot(list, environmentVariable, "Eclipse Foundation");
        Program.AddRoot(list, environmentVariable, "Temurin");
        Program.AddRoot(list, environmentVariable, "Microsoft");
        Program.AddRoot(list, environmentVariable2, "Microsoft");
        Program.AddRoot(list, environmentVariable, "Zulu");
        Program.AddRoot(list, environmentVariable, "Amazon Corretto");
        Program.AddRoot(list, environmentVariable, "BellSoft");
        Program.AddRoot(list, environmentVariable, "BellSoft\\LibericaJDK");
        Program.AddRoot(list, environmentVariable, "Semeru");
        Program.AddRoot(list, environmentVariable, "IBM");
        Program.AddRoot(list, environmentVariable, "Alibaba");
        Program.AddRoot(list, environmentVariable, "Dragonwell");
        Program.AddRoot(list, environmentVariable, "GraalVM");
        Program.AddRoot(list, environmentVariable, "Android\\openjdk");
        Program.AddRoot(list, environmentVariable2, "Android\\openjdk");
        Program.AddRoot(list, environmentVariable4, "Programs");
        Program.AddRoot(list, environmentVariable5, ".jdks");
        Program.AddRoot(list, environmentVariable5, "scoop\\apps");
        Program.AddRoot(list, environmentVariable6, "chocolatey\\lib");
        if (!string.IsNullOrEmpty(environmentVariable3) && !string.Equals(environmentVariable3, environmentVariable, StringComparison.OrdinalIgnoreCase))
        {
            Program.AddRoot(list, environmentVariable3, "Java");
            Program.AddRoot(list, environmentVariable3, "Eclipse Adoptium");
        }
        return list;
    }

    // Token: 0x0600000C RID: 12 RVA: 0x00002DF0 File Offset: 0x00000FF0
    private static void AddRoot(List<string> roots, string baseDir, string sub)
    {
        if (!string.IsNullOrEmpty(baseDir))
        {
            string text = Path.Combine(baseDir, sub);
            if (Directory.Exists(text))
            {
                roots.Add(text);
            }
        }
    }

    // Token: 0x0600000D RID: 13 RVA: 0x00002E2C File Offset: 0x0000102C
    private static IEnumerable<string> DriveRoots()
    {
        List<string> list = new List<string>();
        DriveInfo[] drives = DriveInfo.GetDrives();
        int i = 0;
        while (i < drives.Length)
        {
            DriveInfo driveInfo = drives[i];
            try
            {
                if (driveInfo.DriveType == DriveType.Fixed && driveInfo.IsReady)
                {
                    foreach (string text in new string[] { "Java", "jdk", "jdk17", "jdk21", "jdk25", "openjdk", "Program Files\\Java" })
                    {
                        string text2 = Path.Combine(driveInfo.RootDirectory.FullName, text);
                        if (Directory.Exists(text2))
                        {
                            list.Add(text2);
                        }
                    }
                    foreach (string text3 in Directory.GetDirectories(driveInfo.RootDirectory.FullName))
                    {
                        if (Program.Javaish.IsMatch(Path.GetFileName(text3)) && !list.Contains(text3))
                        {
                            list.Add(text3);
                        }
                    }
                }
            }
            catch
            {
            }
            IL_0140:
            i++;
            continue;
            goto IL_0140;
        }
        return list;
    }

    // Token: 0x0600000E RID: 14 RVA: 0x00002FB4 File Offset: 0x000011B4
    private static void AddRegistryJavaSoft(HashSet<string> sink)
    {
        string[] array = new string[] { "SOFTWARE\\JavaSoft\\Java Development Kit", "SOFTWARE\\JavaSoft\\JDK", "SOFTWARE\\JavaSoft\\Java Runtime Environment", "SOFTWARE\\JavaSoft\\JRE", "SOFTWARE\\JavaSoft\\Java SE Development Kit", "SOFTWARE\\JavaSoft\\Java SE Runtime Environment" };
        RegistryHive[] array2 = new RegistryHive[]
        {
            RegistryHive.LocalMachine,
            RegistryHive.CurrentUser
        };
        RegistryView[] array3 = new RegistryView[]
        {
            RegistryView.Registry64,
            RegistryView.Registry32
        };
        foreach (RegistryHive registryHive in array2)
        {
            foreach (RegistryView registryView in array3)
            {
                string[] array6 = array;
                int k = 0;
                while (k < array6.Length)
                {
                    string text = array6[k];
                    try
                    {
                        using (RegistryKey registryKey = RegistryKey.OpenBaseKey(registryHive, registryView))
                        {
                            using (RegistryKey registryKey2 = registryKey.OpenSubKey(text))
                            {
                                if (registryKey2 != null)
                                {
                                    string[] subKeyNames = registryKey2.GetSubKeyNames();
                                    int l = 0;
                                    while (l < subKeyNames.Length)
                                    {
                                        string text2 = subKeyNames[l];
                                        using (RegistryKey registryKey3 = registryKey2.OpenSubKey(text2))
                                        {
                                            if (registryKey3 != null)
                                            {
                                                string text3 = registryKey3.GetValue("JavaHome") as string;
                                                if (!string.IsNullOrEmpty(text3))
                                                {
                                                    string text4 = Path.Combine(text3, "bin", "java.exe");
                                                    if (File.Exists(text4))
                                                    {
                                                        sink.Add(text4);
                                                    }
                                                }
                                            }
                                        }
                                        IL_0181:
                                        l++;
                                        continue;
                                        goto IL_0181;
                                    }
                                }
                            }
                        }
                    }
                    catch
                    {
                    }
                    IL_01D2:
                    k++;
                    continue;
                    goto IL_01D2;
                }
            }
        }
    }

    // Token: 0x0600000F RID: 15 RVA: 0x00003240 File Offset: 0x00001440
    private static void AddRegistryUninstall(HashSet<string> sink)
    {
        string[] array = new string[] { "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall", "SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall" };
        RegistryHive[] array2 = new RegistryHive[]
        {
            RegistryHive.LocalMachine,
            RegistryHive.CurrentUser
        };
        RegistryView[] array3 = new RegistryView[]
        {
            RegistryView.Registry64,
            RegistryView.Registry32
        };
        foreach (RegistryHive registryHive in array2)
        {
            foreach (RegistryView registryView in array3)
            {
                string[] array6 = array;
                int k = 0;
                while (k < array6.Length)
                {
                    string text = array6[k];
                    try
                    {
                        using (RegistryKey registryKey = RegistryKey.OpenBaseKey(registryHive, registryView))
                        {
                            using (RegistryKey registryKey2 = registryKey.OpenSubKey(text))
                            {
                                if (registryKey2 != null)
                                {
                                    string[] subKeyNames = registryKey2.GetSubKeyNames();
                                    int l = 0;
                                    while (l < subKeyNames.Length)
                                    {
                                        string text2 = subKeyNames[l];
                                        try
                                        {
                                            using (RegistryKey registryKey3 = registryKey2.OpenSubKey(text2))
                                            {
                                                if (registryKey3 != null)
                                                {
                                                    string text3 = registryKey3.GetValue("DisplayName") as string;
                                                    if (!string.IsNullOrEmpty(text3))
                                                    {
                                                        if (Program.Javaish.IsMatch(text3))
                                                        {
                                                            string text4 = registryKey3.GetValue("InstallLocation") as string;
                                                            if (!string.IsNullOrEmpty(text4))
                                                            {
                                                                text4 = text4.Trim().Trim(new char[] { '"' });
                                                                string text5 = Path.Combine(text4, "bin", "java.exe");
                                                                if (File.Exists(text5))
                                                                {
                                                                    sink.Add(text5);
                                                                    goto IL_027A;
                                                                }
                                                            }
                                                            string text6 = registryKey3.GetValue("DisplayIcon") as string;
                                                            if (!string.IsNullOrEmpty(text6))
                                                            {
                                                                text6 = text6.Trim();
                                                                int num = text6.IndexOf(',');
                                                                if (num > 0)
                                                                {
                                                                    text6 = text6.Substring(0, num);
                                                                }
                                                                text6 = text6.Trim(new char[] { '"' });
                                                                if (text6.EndsWith("java.exe", StringComparison.OrdinalIgnoreCase) || text6.EndsWith("javaw.exe", StringComparison.OrdinalIgnoreCase))
                                                                {
                                                                    if (File.Exists(text6))
                                                                    {
                                                                        sink.Add(text6);
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        catch
                                        {
                                        }
                                        IL_027A:
                                        l++;
                                        continue;
                                        goto IL_027A;
                                    }
                                }
                            }
                        }
                    }
                    catch
                    {
                    }
                    IL_02CB:
                    k++;
                    continue;
                    goto IL_02CB;
                }
            }
        }
    }

    // Token: 0x06000010 RID: 16 RVA: 0x000035DC File Offset: 0x000017DC
    private static void AddFromWhere(HashSet<string> sink)
    {
        try
        {
            using (Process process = Process.Start(new ProcessStartInfo
            {
                FileName = "cmd.exe",
                Arguments = "/c where java",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                CreateNoWindow = true
            }))
            {
                string text = process.StandardOutput.ReadToEnd();
                process.WaitForExit(3000);
                foreach (string text2 in text.Split(new char[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries))
                {
                    string text3 = text2.Trim();
                    if (text3.EndsWith("java.exe", StringComparison.OrdinalIgnoreCase))
                    {
                        if (File.Exists(text3))
                        {
                            sink.Add(text3);
                        }
                    }
                }
            }
        }
        catch
        {
        }
    }

    // Token: 0x06000011 RID: 17 RVA: 0x000036F4 File Offset: 0x000018F4
    private static int JavaMajor(string exePath)
    {
        int num;
        try
        {
            Process process = Process.Start(new ProcessStartInfo
            {
                FileName = exePath,
                Arguments = "-version",
                UseShellExecute = false,
                RedirectStandardError = true,
                RedirectStandardOutput = true,
                CreateNoWindow = true
            });
            string text = process.StandardError.ReadToEnd() + "\n" + process.StandardOutput.ReadToEnd();
            if (!process.WaitForExit(6000))
            {
                try
                {
                    process.Kill();
                }
                catch
                {
                }
                num = -1;
            }
            else
            {
                Match match = Regex.Match(text, "version \"(\\d+)(?:\\.(\\d+))?");
                if (!match.Success)
                {
                    num = -1;
                }
                else
                {
                    int num2 = int.Parse(match.Groups[1].Value);
                    if (num2 == 1 && match.Groups[2].Success)
                    {
                        num = int.Parse(match.Groups[2].Value);
                    }
                    else
                    {
                        num = num2;
                    }
                }
            }
        }
        catch
        {
            num = -1;
        }
        return num;
    }

    // Token: 0x04000001 RID: 1
    private static string exeDir;

    // Token: 0x04000002 RID: 2
    private static string jarPath;

    // Token: 0x04000003 RID: 3
    private static readonly Regex Javaish = new Regex("jdk|jre|java|temurin|adoptium|zulu|corretto|graal|semeru|dragonwell|openjdk|bellsoft|liberica", RegexOptions.IgnoreCase);
}
