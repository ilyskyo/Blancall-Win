using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("Blancall 卸载程序")]
[assembly: AssemblyDescription("Uninstall Blancall")]
[assembly: AssemblyProduct("Blancall")]
[assembly: AssemblyCompany("ilyskyo")]
[assembly: AssemblyCopyright("Copyright (c) 2026 ilyskyo")]
[assembly: AssemblyVersion("7.6.1.2")]
[assembly: AssemblyFileVersion("7.6.1.2")]

/// <summary>
/// 预置卸载器：删除程序目录、开始菜单/桌面快捷方式与卸载注册项，
/// 但**不删除** %LOCALAPPDATA%\Blancall 学习数据。
/// 程序目录由 cmd 在本进程退出后删除（卸载器自身文件被占用）。
/// </summary>
internal static class BlancallUninstall
{
    [STAThread]
    private static int Main(string[] args)
    {
        Localization.Current = Localization.Detect();

        bool silent = false;
        foreach (string arg in args)
        {
            if (arg.Equals("/SILENT", StringComparison.OrdinalIgnoreCase) ||
                arg.Equals("/VERYSILENT", StringComparison.OrdinalIgnoreCase))
            {
                silent = true;
            }
        }

        string dir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
        if (string.IsNullOrEmpty(dir) || !File.Exists(Path.Combine(dir, "Blancall.exe")))
        {
            if (!silent) MessageBox.Show(Localization.T("UninstallNotFound"),
                Localization.T("UninstallConfirmTitle"), MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }

        if (IsRunning(dir))
        {
            MessageBox.Show(Localization.T("UninstallRunning"),
                Localization.T("UninstallConfirmTitle"), MessageBoxButtons.OK, MessageBoxIcon.Warning);
            return 1;
        }

        if (!silent)
        {
            DialogResult answer = MessageBox.Show(
                Localization.T("UninstallConfirm"),
                Localization.T("UninstallConfirmTitle"),
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question);
            if (answer != DialogResult.Yes) return 1;
        }

        try
        {
            string programs = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "Microsoft", "Windows", "Start Menu", "Programs");
            TryDeleteDirectory(Path.Combine(programs, "Blancall"));
            TryDeleteFile(Path.Combine(programs, "Blancall.lnk"));
            TryDeleteFile(Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Blancall.lnk"));
            Registry.CurrentUser.DeleteSubKeyTree(
                @"Software\Microsoft\Windows\CurrentVersion\Uninstall\Blancall", false);

            var psi = new ProcessStartInfo("cmd.exe",
                "/c ping -n 3 127.0.0.1 >nul & rmdir /s /q \"" + dir + "\"");
            psi.WorkingDirectory = Environment.SystemDirectory;
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.WindowStyle = ProcessWindowStyle.Hidden;
            Process.Start(psi);
            return 0;
        }
        catch (Exception error)
        {
            if (!silent) MessageBox.Show(Localization.T("UninstallFailed") + "\r\n" + error.Message,
                Localization.T("UninstallConfirmTitle"), MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    /** 旧版缩在托盘时窗口标题可能为空，所以按 runtime\bin\javaw.exe 的路径识别。 */
    private static bool IsRunning(string dir)
    {
        string prefix = dir.TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        try
        {
            foreach (Process process in Process.GetProcessesByName("javaw"))
            {
                try
                {
                    string path = process.MainModule.FileName;
                    if (path != null && path.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return true;
                }
                catch
                {
                }
            }
        }
        catch
        {
        }
        return false;
    }

    private static void TryDeleteFile(string path)
    {
        try { if (File.Exists(path)) File.Delete(path); } catch { }
    }

    private static void TryDeleteDirectory(string path)
    {
        try { if (Directory.Exists(path)) Directory.Delete(path, true); } catch { }
    }
}
