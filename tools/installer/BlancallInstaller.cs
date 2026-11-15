using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("Blancall 安装程序")]
[assembly: AssemblyDescription("Blancall installer")]
[assembly: AssemblyProduct("Blancall")]
[assembly: AssemblyCompany("ilyskyo")]
[assembly: AssemblyCopyright("Copyright (c) 2026 ilyskyo")]
[assembly: AssemblyVersion("7.6.1.2")]
[assembly: AssemblyFileVersion("7.6.1.2")]

/// <summary>
/// 自绘安装向导（六语 + DPI 感知）：不经过系统 Temp，也不在安装时生成 unins000.exe。
/// payload 是嵌入的 app image zip，直接解到安装目录；卸载器是预置的 BlancallUninstall.exe。
/// </summary>
internal static class BlancallInstaller
{
    private const string PayloadResource = "payload.zip";
    private const string LicenseResource = "license.txt";
    private const string AppVersion = "7.6.1";

    [STAThread]
    private static int Main(string[] args)
    {
        Localization.Current = Localization.Detect();

        bool silent = false;
        bool noIcons = false;
        string silentDir = null;
        foreach (string arg in args)
        {
            if (arg.StartsWith("/DIR=", StringComparison.OrdinalIgnoreCase))
            {
                silentDir = arg.Substring(5).Trim('"');
            }
            else if (arg.Equals("/SILENT", StringComparison.OrdinalIgnoreCase) ||
                     arg.Equals("/VERYSILENT", StringComparison.OrdinalIgnoreCase))
            {
                silent = true;
            }
            else if (arg.Equals("/NOICONS", StringComparison.OrdinalIgnoreCase))
            {
                noIcons = true;
            }
        }

        if (silent)
        {
            string dir = string.IsNullOrEmpty(silentDir) ? DefaultInstallDir() : silentDir;
            try
            {
                InstallCore(dir, !noIcons, !noIcons, delegate(int percent, string file) { });
                return 0;
            }
            catch (Exception error)
            {
                LogError(error);
                MessageBox.Show("Blancall\r\n\r\n" + Localization.T("ErrInstall") + "\r\n" + error.Message,
                    "Blancall", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
        }

        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new WizardForm());
        return 0;
    }

    private static string DefaultInstallDir()
    {
        return Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Programs", "Blancall");
    }

    private static string ReadLicense()
    {
        using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(LicenseResource))
        {
            if (stream == null) return "MIT License\n\nCopyright (c) 2026 ilyskyo";
            using (var reader = new StreamReader(stream)) return reader.ReadToEnd();
        }
    }

    private static List<string> InstallCore(
        string dir,
        bool desktopShortcut,
        bool startMenuShortcut,
        Action<int, string> report)
    {
        var warnings = new List<string>();
        if (string.IsNullOrWhiteSpace(dir)) throw new InvalidOperationException(Localization.T("ErrDirEmpty"));
        dir = Path.GetFullPath(dir);
        if (IsAppRunning(dir)) throw new InvalidOperationException(Localization.T("ErrRunning"));

        string existingJar = Path.Combine(dir, "app", "blancall-win.jar");
        if (File.Exists(existingJar))
        {
            try { File.SetAttributes(existingJar, FileAttributes.Normal); } catch { }
            try
            {
                // 安全软件的实时扫描会短暂独占文件，单次探测容易把可恢复的占用报成失败。
                for (int attempt = 0; ; attempt++)
                {
                    try
                    {
                        using (var probe = new FileStream(
                            existingJar, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
                        {
                        }
                        break;
                    }
                    catch (IOException)
                    {
                        if (attempt >= 2) throw;
                        Thread.Sleep(250);
                    }
                }
            }
            catch (IOException)
            {
                throw new InvalidOperationException(Localization.T("ErrLocked"));
            }
            catch (UnauthorizedAccessException)
            {
            }
        }

        Directory.CreateDirectory(dir);
        string staging = Path.Combine(dir, ".blancall-staging");
        TryDeleteDirectory(staging);
        Directory.CreateDirectory(staging);

        long totalBytes = 0;
        long stagedBytes = 0;
        int fileCount = 0;
        using (Stream payload = Assembly.GetExecutingAssembly().GetManifestResourceStream(PayloadResource))
        {
            if (payload == null) throw new InvalidOperationException("payload missing");
            using (var archive = new ZipArchive(payload, ZipArchiveMode.Read))
            {
                foreach (ZipArchiveEntry entry in archive.Entries) totalBytes += entry.Length;
                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    string relative = entry.FullName.Replace('/', Path.DirectorySeparatorChar);
                    string target = ResolveInside(staging, relative, entry.FullName);
                    if (entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\"))
                    {
                        Directory.CreateDirectory(target);
                        continue;
                    }
                    Directory.CreateDirectory(Path.GetDirectoryName(target));
                    try
                    {
                        entry.ExtractToFile(target, true);
                    }
                    catch (UnauthorizedAccessException error)
                    {
                        throw new IOException(Localization.T("ErrWriteDenied") + entry.FullName, error);
                    }
                    catch (IOException error)
                    {
                        throw new IOException(Localization.T("ErrWriteDenied") + entry.FullName, error);
                    }
                    stagedBytes += entry.Length;
                    fileCount++;
                    report(totalBytes <= 0 ? 0 : (int)(stagedBytes * 90 / totalBytes), entry.FullName);
                }
            }
        }

        // 新文件全部就位后再替换旧文件：替换被占用时重试，彻底失败也不会让旧安装停摆。
        // jar 最先换：它最容易被安全软件扫锁，冲突时在动其它文件之前失败。
        string[] stagedFiles = Directory.GetFiles(staging, "*", SearchOption.AllDirectories);
        string stagedJar = Path.Combine(staging, "app", "blancall-win.jar");
        int fileCountAll = Math.Max(1, fileCount);
        int moved = 0;
        if (File.Exists(stagedJar))
        {
            string jarTarget = Path.Combine(dir, "app", "blancall-win.jar");
            Directory.CreateDirectory(Path.GetDirectoryName(jarTarget));
            ReplaceWithRetry(stagedJar, jarTarget);
            moved++;
            report(90 + moved * 9 / fileCountAll, "app\\blancall-win.jar");
        }
        foreach (string stagedFile in stagedFiles)
        {
            if (string.Equals(stagedFile, stagedJar, StringComparison.OrdinalIgnoreCase)) continue;
            string relative = stagedFile.Substring(staging.Length + 1);
            string target = Path.Combine(dir, relative);
            Directory.CreateDirectory(Path.GetDirectoryName(target));
            ReplaceWithRetry(stagedFile, target);
            moved++;
            report(90 + moved * 9 / fileCountAll, relative);
        }
        foreach (string stagedDir in Directory.GetDirectories(staging, "*", SearchOption.AllDirectories))
        {
            Directory.CreateDirectory(Path.Combine(dir, stagedDir.Substring(staging.Length + 1)));
        }
        TryDeleteDirectory(staging);
        report(100, "");

        TryDeleteFile(Path.Combine(dir, "Blancall.vbs"));
        TryDeleteFile(Path.Combine(dir, "Blancall.bat"));

        string programs = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "Microsoft", "Windows", "Start Menu", "Programs");
        if (startMenuShortcut) TryDeleteFile(Path.Combine(programs, "Blancall.lnk"));

        try
        {
            if (startMenuShortcut)
            {
                string folder = Path.Combine(programs, "Blancall");
                Directory.CreateDirectory(folder);
                CreateShortcut(
                    Path.Combine(folder, "Blancall.lnk"),
                    Path.Combine(dir, "Blancall.exe"),
                    dir,
                    Localization.T("ShortcutDesc"));
                CreateShortcut(
                    Path.Combine(folder, Localization.T("UninstallShortcut") + ".lnk"),
                    Path.Combine(dir, "BlancallUninstall.exe"),
                    dir,
                    Localization.T("UninstallShortcut"));
            }
            if (desktopShortcut)
            {
                CreateShortcut(
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "Blancall.lnk"),
                    Path.Combine(dir, "Blancall.exe"),
                    dir,
                    Localization.T("ShortcutDesc"));
            }
        }
        catch (Exception error)
        {
            warnings.Add(Localization.T("WarnShortcut") + error.Message);
            LogError(error);
        }

        try
        {
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Uninstall\Blancall"))
            {
                if (key == null) throw new InvalidOperationException("registry");
                key.SetValue("DisplayName", "Blancall");
                key.SetValue("DisplayVersion", AppVersion);
                key.SetValue("Publisher", "ilyskyo");
                key.SetValue("DisplayIcon", Path.Combine(dir, "Blancall.exe"));
                key.SetValue("InstallLocation", dir);
                key.SetValue("UninstallString", "\"" + Path.Combine(dir, "BlancallUninstall.exe") + "\"");
                key.SetValue("QuietUninstallString", "\"" + Path.Combine(dir, "BlancallUninstall.exe") + "\" /SILENT");
                key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                key.SetValue("EstimatedSize", (int)Math.Max(0, totalBytes / 1024), RegistryValueKind.DWord);
            }
        }
        catch (Exception error)
        {
            warnings.Add(Localization.T("WarnRegistry") + error.Message);
            LogError(error);
        }
        if (warnings.Count > 0) LogText("warnings: " + string.Join(" | ", warnings.ToArray()));
        return warnings;
    }

    /** 安装目录里的任何进程都算运行中：Blancall.exe、runtime\bin\javaw.exe、旧版的 java.exe 启动器。 */
    private static bool IsAppRunning(string dir)
    {
        string prefix = dir.TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        int selfId;
        using (Process self = Process.GetCurrentProcess()) selfId = self.Id;
        try
        {
            foreach (Process process in Process.GetProcesses())
            {
                try
                {
                    using (process)
                    {
                        if (process.Id == selfId) continue;
                        string path = process.MainModule.FileName;
                        if (path != null && path.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) return true;
                    }
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

    private static string ResolveInside(string root, string relative, string entryName)
    {
        string target = Path.GetFullPath(Path.Combine(root, relative));
        string prefix = root.TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        if (target != root && !target.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException("invalid payload path: " + entryName);
        }
        return target;
    }

    private static void ReplaceWithRetry(string staged, string target)
    {
        Exception last = null;
        for (int attempt = 0; attempt < 20; attempt++)
        {
            try
            {
                if (File.Exists(target))
                {
                    try { File.SetAttributes(target, FileAttributes.Normal); } catch { }
                    File.Replace(staged, target, null);
                }
                else
                {
                    File.Move(staged, target);
                }
                return;
            }
            catch (Exception error)
            {
                last = error;
                if (attempt >= 19) break;
                Thread.Sleep(250);
            }
        }
        throw new IOException(Localization.T("ErrStillLocked") + target, last);
    }

    private static void TryDeleteDirectory(string path)
    {
        try { if (Directory.Exists(path)) Directory.Delete(path, true); } catch { }
    }

    private static void CreateShortcut(
        string lnkPath, string target, string workDir, string description)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(lnkPath));
        Type shellType = Type.GetTypeFromProgID("WScript.Shell");
        if (shellType == null) throw new InvalidOperationException("WScript.Shell");
        dynamic shell = Activator.CreateInstance(shellType);
        dynamic link = shell.CreateShortcut(lnkPath);
        link.TargetPath = target;
        link.WorkingDirectory = workDir;
        link.Description = description;
        link.Save();
    }

    private static void TryDeleteFile(string path)
    {
        try { if (File.Exists(path)) File.Delete(path); } catch { }
    }

    private static void LogText(string text)
    {
        try
        {
            string dir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
            if (string.IsNullOrEmpty(dir)) return;
            File.AppendAllText(
                Path.Combine(dir, "install-error.log"),
                DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " " + text + Environment.NewLine);
        }
        catch
        {
        }
    }

    private static void LogError(Exception error)
    {
        try
        {
            string dir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
            if (string.IsNullOrEmpty(dir)) return;
            File.AppendAllText(
                Path.Combine(dir, "install-error.log"),
                DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " " +
                error.GetType().FullName + " HRESULT=0x" + error.HResult.ToString("X8") +
                Environment.NewLine + error + Environment.NewLine);
        }
        catch
        {
        }
    }

    private sealed class WizardForm : Form
    {
        private readonly Label titleLabel;
        private readonly Panel content;
        private readonly Button backButton;
        private readonly Button nextButton;
        private readonly Button cancelButton;
        private readonly Panel welcomePage;
        private readonly Panel optionsPage;
        private readonly Panel progressPage;
        private readonly Panel finishPage;
        private readonly Label intro;
        private readonly LinkLabel licenseLink;
        private readonly Label languageLabel;
        private readonly ComboBox languageBox;
        private readonly Label dirLabel;
        private readonly TextBox dirBox;
        private readonly Button browseButton;
        private readonly CheckBox desktopBox;
        private readonly CheckBox startMenuBox;
        private readonly Label noteLabel;
        private readonly Label progressTitle;
        private readonly ProgressBar progressBar;
        private readonly Label progressStatus;
        private readonly Label doneLabel;
        private readonly CheckBox launchBox;
        private readonly Label warningLabel;
        private readonly string licenseText;
        private readonly string defaultDir;
        private int page;
        private bool installing;
        private bool updatingLanguage;
        private bool dpiApplied;

        public WizardForm()
        {
            licenseText = ReadLicense();
            defaultDir = DefaultInstallDir();
            Localization.Current = Localization.Detect();

            AutoScaleMode = AutoScaleMode.None;
            Text = "Blancall";
            ClientSize = new Size(640, 470);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.White;
            Font = new Font("Segoe UI", 9F);
            try { Icon = System.Drawing.Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

            titleLabel = new Label
            {
                AutoSize = true,
                Location = new Point(24, 18),
                Font = new Font("Segoe UI", 16F, FontStyle.Bold),
                ForeColor = Color.FromArgb(30, 27, 24),
            };
            Controls.Add(titleLabel);

            content = new Panel { Location = new Point(24, 66), Size = new Size(592, 330) };
            Controls.Add(content);

            backButton = new Button { Location = new Point(312, 414), Size = new Size(100, 30) };
            nextButton = new Button { Location = new Point(418, 414), Size = new Size(100, 30) };
            cancelButton = new Button { Location = new Point(524, 414), Size = new Size(100, 30) };
            backButton.Click += delegate { ShowPage(0); };
            nextButton.Click += delegate { OnNext(); };
            cancelButton.Click += delegate { Close(); };
            Controls.Add(backButton);
            Controls.Add(nextButton);
            Controls.Add(cancelButton);

            welcomePage = new Panel { Dock = DockStyle.Fill, Visible = false };
            intro = new Label
            {
                Location = new Point(0, 0),
                Size = new Size(592, 230),
                UseMnemonic = false,
            };
            licenseLink = new LinkLabel { Location = new Point(0, 234), AutoSize = true };
            licenseLink.LinkClicked += delegate { ShowLicense(); };
            languageLabel = new Label { Location = new Point(0, 274), AutoSize = true };
            languageBox = new ComboBox
            {
                Location = new Point(160, 270),
                Size = new Size(220, 26),
                DropDownStyle = ComboBoxStyle.DropDownList,
            };
            foreach (string[] language in Localization.Languages) languageBox.Items.Add(language[1]);
            languageBox.SelectedIndexChanged += delegate
            {
                if (updatingLanguage) return;
                int index = languageBox.SelectedIndex;
                if (index < 0 || index >= Localization.Languages.Length) return;
                Localization.Current = Localization.Languages[index][0];
                ApplyLanguage();
            };
            welcomePage.Controls.Add(intro);
            welcomePage.Controls.Add(licenseLink);
            welcomePage.Controls.Add(languageLabel);
            welcomePage.Controls.Add(languageBox);

            optionsPage = new Panel { Dock = DockStyle.Fill, Visible = false };
            dirLabel = new Label { Location = new Point(0, 10), AutoSize = true };
            dirBox = new TextBox { Location = new Point(110, 6), Size = new Size(366, 26), Text = defaultDir };
            browseButton = new Button { Location = new Point(486, 5), Size = new Size(100, 28) };
            browseButton.Click += delegate
            {
                using (var dialog = new FolderBrowserDialog())
                {
                    dialog.SelectedPath = dirBox.Text;
                    if (dialog.ShowDialog(this) == DialogResult.OK) dirBox.Text = dialog.SelectedPath;
                }
            };
            desktopBox = new CheckBox { Location = new Point(0, 54), AutoSize = true, Checked = true };
            startMenuBox = new CheckBox { Location = new Point(0, 86), AutoSize = true, Checked = true };
            noteLabel = new Label
            {
                Location = new Point(0, 132),
                Size = new Size(580, 90),
                ForeColor = Color.FromArgb(95, 92, 87),
            };
            optionsPage.Controls.Add(dirLabel);
            optionsPage.Controls.Add(dirBox);
            optionsPage.Controls.Add(browseButton);
            optionsPage.Controls.Add(desktopBox);
            optionsPage.Controls.Add(startMenuBox);
            optionsPage.Controls.Add(noteLabel);

            progressPage = new Panel { Dock = DockStyle.Fill, Visible = false };
            progressTitle = new Label { Location = new Point(0, 8), AutoSize = true };
            progressBar = new ProgressBar { Location = new Point(0, 44), Size = new Size(580, 18), Minimum = 0, Maximum = 100 };
            progressStatus = new Label
            {
                Location = new Point(0, 74),
                Size = new Size(580, 80),
                ForeColor = Color.FromArgb(95, 92, 87),
                AutoEllipsis = true,
            };
            progressPage.Controls.Add(progressTitle);
            progressPage.Controls.Add(progressBar);
            progressPage.Controls.Add(progressStatus);

            finishPage = new Panel { Dock = DockStyle.Fill, Visible = false };
            doneLabel = new Label
            {
                Location = new Point(0, 8),
                AutoSize = true,
                Font = new Font("Segoe UI", 11F, FontStyle.Bold),
            };
            launchBox = new CheckBox { Location = new Point(0, 44), AutoSize = true, Checked = true };
            warningLabel = new Label
            {
                Location = new Point(0, 84),
                Size = new Size(580, 160),
                ForeColor = Color.FromArgb(180, 90, 20),
            };
            finishPage.Controls.Add(doneLabel);
            finishPage.Controls.Add(launchBox);
            finishPage.Controls.Add(warningLabel);

            content.Controls.Add(welcomePage);
            content.Controls.Add(optionsPage);
            content.Controls.Add(progressPage);
            content.Controls.Add(finishPage);
            FormClosing += delegate(object sender, FormClosingEventArgs e) { if (installing) e.Cancel = true; };
            ApplyLanguage();
            ShowPage(0);
#if DPI_TEST
            int startPage;
            if (int.TryParse(Environment.GetEnvironmentVariable("BLANCALL_PAGE"), out startPage) &&
                startPage >= 0 && startPage <= 3)
            {
                ShowPage(startPage);
            }
#endif
        }

        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);
            ActiveControl = nextButton;
        }

        protected override void OnLoad(EventArgs e)
        {
            base.OnLoad(e);
            if (dpiApplied) return;
            dpiApplied = true;
            DpiScale.Apply(this, new Size(640, 470));
            CenterToScreen();
        }

        private void ApplyLanguage()
        {
            Text = Localization.T("AppTitle");
            intro.Text = Localization.T("Intro");
            licenseLink.Text = Localization.T("LicenseLink");
            languageLabel.Text = Localization.T("LanguageLabel");
            dirLabel.Text = Localization.T("DirLabel");
            browseButton.Text = Localization.T("Browse");
            desktopBox.Text = Localization.T("DesktopShortcut");
            startMenuBox.Text = Localization.T("StartMenuShortcut");
            noteLabel.Text = Localization.T("DataNote");
            progressTitle.Text = Localization.T("ProgressTitle");
            doneLabel.Text = Localization.T("DoneTitle");
            launchBox.Text = Localization.T("Launch");

            updatingLanguage = true;
            for (int i = 0; i < Localization.Languages.Length; i++)
            {
                if (Localization.Languages[i][0] == Localization.Current)
                {
                    languageBox.SelectedIndex = i;
                    break;
                }
            }
            updatingLanguage = false;
            ShowPage(page);
        }

        private void ShowPage(int target)
        {
            page = target;
            welcomePage.Visible = target == 0;
            optionsPage.Visible = target == 1;
            progressPage.Visible = target == 2;
            finishPage.Visible = target == 3;
            titleLabel.Text = target == 0 ? Localization.T("WelcomeTitle")
                : target == 1 ? Localization.T("OptionsTitle")
                : target == 2 ? Localization.T("ProgressPageTitle")
                : Localization.T("FinishPageTitle");
            backButton.Text = Localization.T("Back");
            nextButton.Text = target == 1 ? Localization.T("Install")
                : target == 3 ? Localization.T("Finish")
                : Localization.T("Next");
            cancelButton.Text = target == 3 ? Localization.T("Close") : Localization.T("Cancel");
            backButton.Enabled = target == 1 && !installing;
            nextButton.Enabled = target != 2;
            cancelButton.Enabled = !installing;
            if (target == 1 && string.IsNullOrWhiteSpace(dirBox.Text)) dirBox.Text = defaultDir;
        }

        private void OnNext()
        {
            if (page == 0)
            {
                ShowPage(1);
                return;
            }
            if (page == 1)
            {
                StartInstall();
                return;
            }
            if (page == 3)
            {
                if (launchBox.Checked)
                {
                    try { Process.Start(Path.Combine(dirBox.Text, "Blancall.exe")); } catch { }
                }
                Close();
            }
        }

        private void StartInstall()
        {
            string dir = dirBox.Text.Trim();
            if (dir.Length == 0)
            {
                MessageBox.Show(this, Localization.T("ErrDirEmpty"), "Blancall",
                    MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }
            installing = true;
            ShowPage(2);
            progressBar.Value = 0;
            progressStatus.Text = "";
            bool desktop = desktopBox.Checked;
            bool startMenu = startMenuBox.Checked;
            var worker = new Thread(delegate()
            {
                try
                {
                    List<string> warnings = InstallCore(
                        dir,
                        desktop,
                        startMenu,
                        delegate(int percent, string file)
                        {
                            BeginInvoke(new Action(delegate()
                            {
                                progressBar.Value = Math.Max(0, Math.Min(100, percent));
                                progressStatus.Text = file;
                            }));
                        });
                    BeginInvoke(new Action(delegate()
                    {
                        installing = false;
                        warningLabel.Text = string.Join("\r\n", warnings.ToArray());
                        ShowPage(3);
                    }));
                }
                catch (Exception error)
                {
                    LogError(error);
                    BeginInvoke(new Action(delegate()
                    {
                        installing = false;
                        MessageBox.Show(this, Localization.T("ErrInstall") + "\r\n" + error.Message, "Blancall",
                            MessageBoxButtons.OK, MessageBoxIcon.Error);
                        ShowPage(1);
                    }));
                }
            });
            worker.IsBackground = true;
            worker.Start();
        }

        private void ShowLicense()
        {
            using (var dialog = new Form())
            {
                dialog.AutoScaleMode = AutoScaleMode.None;
                dialog.Text = Localization.T("LicenseTitle");
                dialog.ClientSize = new Size(560, 420);
                dialog.StartPosition = FormStartPosition.CenterParent;
                dialog.Load += delegate
                {
                    DpiScale.Apply(dialog, new Size(560, 420));
                    dialog.Location = new Point(
                        Left + (Width - dialog.Width) / 2,
                        Top + (Height - dialog.Height) / 2);
                };
                dialog.FormBorderStyle = FormBorderStyle.FixedDialog;
                dialog.MaximizeBox = false;
                dialog.MinimizeBox = false;
                var text = new TextBox
                {
                    Multiline = true,
                    ReadOnly = true,
                    ScrollBars = ScrollBars.Vertical,
                    Dock = DockStyle.Fill,
                    Text = licenseText,
                    Font = new Font("Consolas", 9F),
                };
                var close = new Button { Text = Localization.T("Close"), Dock = DockStyle.Bottom, Height = 32 };
                close.Click += delegate { dialog.Close(); };
                dialog.Controls.Add(text);
                dialog.Controls.Add(close);
                dialog.ShowDialog(this);
            }
        }
    }
}
