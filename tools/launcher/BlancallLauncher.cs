using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

[assembly: AssemblyTitle("Blancall")]
[assembly: AssemblyDescription("Blancall - local-first cloze memorization")]
[assembly: AssemblyProduct("Blancall")]
[assembly: AssemblyCompany("ilyskyo")]
[assembly: AssemblyCopyright("Copyright (c) 2026 ilyskyo")]
[assembly: AssemblyVersion("7.6.1.2")]
[assembly: AssemblyFileVersion("7.6.1.2")]

/// <summary>
/// 安装包与快捷方式的原生入口：定位应用目录后以 javaw 启动 jar。
/// 不用 .vbs/.bat，避免脚本宿主被安全策略拦截和控制台窗口闪现。
/// </summary>
internal static class BlancallLauncher
{
    [STAThread]
    private static int Main()
    {
        string dir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
        string javaw = Path.Combine(dir, "runtime", "bin", "javaw.exe");
        string jar = Path.Combine(dir, "app", "blancall-win.jar");
        if (!File.Exists(javaw) || !File.Exists(jar))
        {
            ShowError("Blancall 安装不完整：缺少 runtime 或 app 目录。\n请重新运行安装程序。");
            return 1;
        }

        try
        {
            var start = new ProcessStartInfo(javaw)
            {
                Arguments = "-Dcompose.application.configure.swing.globals=true -jar \"" + jar + "\"",
                WorkingDirectory = dir,
                UseShellExecute = false,
                CreateNoWindow = true,
            };
            Process.Start(start);
            return 0;
        }
        catch (Exception error)
        {
            ShowError("无法启动 Blancall：\n" + error.Message);
            return 1;
        }
    }

    private static void ShowError(string message)
    {
        MessageBox.Show(message, "Blancall", MessageBoxButtons.OK, MessageBoxIcon.Error);
    }
}
