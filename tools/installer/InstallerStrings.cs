using System;
using System.Collections.Generic;
using System.Globalization;

/// <summary>
/// 安装器/卸载器的六语文案（与 App 的六种界面语言一致）。
/// 默认跟随系统语言；安装向导在欢迎页提供手动切换。
/// </summary>
internal static class Localization
{
    public static string Current = "en";

    public static readonly string[][] Languages = new string[][]
    {
        new string[] { "zh-CN", "简体中文" },
        new string[] { "zh-TW", "繁體中文" },
        new string[] { "en", "English" },
        new string[] { "ja", "日本語" },
        new string[] { "ko", "한국어" },
        new string[] { "ru", "Русский" },
    };

    private static readonly Dictionary<string, Dictionary<string, string>> Table =
        new Dictionary<string, Dictionary<string, string>>(StringComparer.OrdinalIgnoreCase)
        {
            {
                "zh-CN", new Dictionary<string, string>
                {
                    { "AppTitle", "Blancall 安装程序" },
                    { "WelcomeTitle", "欢迎使用 Blancall" },
                    { "Intro", "本向导将把 Blancall 安装到你的电脑上。\r\n\r\nBlancall 以 MIT 许可证发布。安装不需要接受任何协议；许可证文本会随程序一并安装。\r\n\r\n学习数据保存在 %LOCALAPPDATA%\\Blancall，卸载时不会被删除。" },
                    { "LicenseLink", "查看完整 MIT 许可证" },
                    { "LanguageLabel", "安装程序语言" },
                    { "OptionsTitle", "选择安装位置" },
                    { "DirLabel", "安装到：" },
                    { "Browse", "浏览..." },
                    { "DesktopShortcut", "创建桌面快捷方式" },
                    { "StartMenuShortcut", "创建开始菜单快捷方式（含卸载入口）" },
                    { "DataNote", "学习数据始终保存在 %LOCALAPPDATA%\\Blancall，卸载只删除程序目录与快捷方式。" },
                    { "ProgressTitle", "正在安装 Blancall，请稍候…" },
                    { "DoneTitle", "安装完成。" },
                    { "Launch", "立即启动 Blancall" },
                    { "Back", "上一步" },
                    { "Next", "下一步" },
                    { "Install", "安装" },
                    { "Cancel", "取消" },
                    { "Finish", "完成" },
                    { "Close", "关闭" },
                    { "LicenseTitle", "MIT 许可证" },
                    { "ErrDirEmpty", "请选择安装目录。" },
                    { "ErrRunning", "Blancall 正在运行，请先从托盘退出后再安装。" },
                    { "ErrLocked", "Blancall 正在运行，或安装文件被占用。请完全退出 Blancall（包括托盘图标）后重试。" },
                    { "ErrStillLocked", "文件仍被其他程序占用（可能是安全软件正在扫描），请稍后重试：" },
                    { "ErrInstall", "安装失败：" },
                    { "ProgressPageTitle", "正在安装" },
                    { "FinishPageTitle", "安装完成" },
                    { "WarnShortcut", "快捷方式创建失败：" },
                    { "WarnRegistry", "卸载入口写入失败：" },
                    { "ErrWriteDenied", "无法写入文件（可能被占用或只读）：" },
                    { "ShortcutDesc", "Blancall - 本地挖空记忆" },
                    { "UninstallShortcut", "卸载 Blancall" },
                    { "UninstallConfirmTitle", "卸载 Blancall" },
                    { "UninstallConfirm", "确定卸载 Blancall？\r\n\r\n学习数据保存在 %LOCALAPPDATA%\\Blancall，不会被删除。" },
                    { "UninstallNotFound", "找不到 Blancall 程序目录，无法卸载。" },
                    { "UninstallRunning", "Blancall 正在运行，请先从托盘退出后再卸载。" },
                    { "UninstallFailed", "卸载失败：" },
                }
            },
            {
                "zh-TW", new Dictionary<string, string>
                {
                    { "AppTitle", "Blancall 安裝程式" },
                    { "WelcomeTitle", "歡迎使用 Blancall" },
                    { "Intro", "本精靈將把 Blancall 安裝到你的電腦上。\r\n\r\nBlancall 以 MIT 授權條款發佈。安裝不需要接受任何協議；授權條款文字會隨程式一併安裝。\r\n\r\n學習資料儲存在 %LOCALAPPDATA%\\Blancall，解除安裝時不會被刪除。" },
                    { "LicenseLink", "檢視完整 MIT 授權條款" },
                    { "LanguageLabel", "安裝程式語言" },
                    { "OptionsTitle", "選擇安裝位置" },
                    { "DirLabel", "安裝到：" },
                    { "Browse", "瀏覽..." },
                    { "DesktopShortcut", "建立桌面捷徑" },
                    { "StartMenuShortcut", "建立開始功能表捷徑（含解除安裝）" },
                    { "DataNote", "學習資料始終儲存在 %LOCALAPPDATA%\\Blancall，解除安裝只刪除程式目錄與捷徑。" },
                    { "ProgressTitle", "正在安裝 Blancall，請稍候…" },
                    { "DoneTitle", "安裝完成。" },
                    { "Launch", "立即啟動 Blancall" },
                    { "Back", "上一步" },
                    { "Next", "下一步" },
                    { "Install", "安裝" },
                    { "Cancel", "取消" },
                    { "Finish", "完成" },
                    { "Close", "關閉" },
                    { "LicenseTitle", "MIT 授權條款" },
                    { "ErrDirEmpty", "請選擇安裝目錄。" },
                    { "ErrRunning", "Blancall 正在執行，請先從系統匣結束後再安裝。" },
                    { "ErrLocked", "Blancall 正在執行，或安裝檔案被占用。請完全結束 Blancall（包括系統匣圖示）後再試一次。" },
                    { "ErrStillLocked", "檔案仍被其他程式占用（可能是安全軟體正在掃描），請稍後再試：" },
                    { "ErrInstall", "安裝失敗：" },
                    { "ProgressPageTitle", "安裝中" },
                    { "FinishPageTitle", "安裝完成" },
                    { "WarnShortcut", "建立捷徑失敗：" },
                    { "WarnRegistry", "解除安裝入口寫入失敗：" },
                    { "ErrWriteDenied", "無法寫入檔案（可能被占用或唯讀）：" },
                    { "ShortcutDesc", "Blancall - 本地挖空記憶" },
                    { "UninstallShortcut", "解除安裝 Blancall" },
                    { "UninstallConfirmTitle", "解除安裝 Blancall" },
                    { "UninstallConfirm", "確定解除安裝 Blancall？\r\n\r\n學習資料儲存在 %LOCALAPPDATA%\\Blancall，不會被刪除。" },
                    { "UninstallNotFound", "找不到 Blancall 程式目錄，無法解除安裝。" },
                    { "UninstallRunning", "Blancall 正在執行，請先從系統匣結束後再解除安裝。" },
                    { "UninstallFailed", "解除安裝失敗：" },
                }
            },
            {
                "en", new Dictionary<string, string>
                {
                    { "AppTitle", "Blancall Setup" },
                    { "WelcomeTitle", "Welcome to Blancall" },
                    { "Intro", "This wizard will install Blancall on your computer.\r\n\r\nBlancall is released under the MIT License. You do not need to accept any agreement; the license text is installed with the app.\r\n\r\nLearning data is stored in %LOCALAPPDATA%\\Blancall and is not removed when uninstalling." },
                    { "LicenseLink", "View the full MIT license" },
                    { "LanguageLabel", "Installer language" },
                    { "OptionsTitle", "Choose install location" },
                    { "DirLabel", "Install to:" },
                    { "Browse", "Browse..." },
                    { "DesktopShortcut", "Create a desktop shortcut" },
                    { "StartMenuShortcut", "Create a Start Menu shortcut (includes uninstaller)" },
                    { "DataNote", "Learning data always stays in %LOCALAPPDATA%\\Blancall. Uninstalling only removes the program folder and shortcuts." },
                    { "ProgressTitle", "Installing Blancall, please wait..." },
                    { "DoneTitle", "Installation complete." },
                    { "Launch", "Launch Blancall now" },
                    { "Back", "Back" },
                    { "Next", "Next" },
                    { "Install", "Install" },
                    { "Cancel", "Cancel" },
                    { "Finish", "Finish" },
                    { "Close", "Close" },
                    { "LicenseTitle", "MIT License" },
                    { "ErrDirEmpty", "Please choose an install folder." },
                    { "ErrRunning", "Blancall is running. Please exit it from the tray before installing." },
                    { "ErrLocked", "Blancall is running, or an installation file is in use. Please exit Blancall completely (including the tray icon) and try again." },
                    { "ErrStillLocked", "The file is still in use by another program (possibly a security scanner). Please try again in a moment: " },
                    { "ErrInstall", "Installation failed:" },
                    { "ProgressPageTitle", "Installing" },
                    { "FinishPageTitle", "Installation complete" },
                    { "WarnShortcut", "Could not create a shortcut: " },
                    { "WarnRegistry", "Could not register the uninstall entry: " },
                    { "ErrWriteDenied", "Could not write file (in use or read-only): " },
                    { "ShortcutDesc", "Blancall - local-first cloze memorization" },
                    { "UninstallShortcut", "Uninstall Blancall" },
                    { "UninstallConfirmTitle", "Uninstall Blancall" },
                    { "UninstallConfirm", "Uninstall Blancall?\r\n\r\nLearning data in %LOCALAPPDATA%\\Blancall will not be deleted." },
                    { "UninstallNotFound", "Blancall's program folder was not found." },
                    { "UninstallRunning", "Blancall is running. Please exit it from the tray before uninstalling." },
                    { "UninstallFailed", "Uninstall failed:" },
                }
            },
            {
                "ja", new Dictionary<string, string>
                {
                    { "AppTitle", "Blancall セットアップ" },
                    { "WelcomeTitle", "Blancall へようこそ" },
                    { "Intro", "このウィザードは Blancall をコンピューターにインストールします。\r\n\r\nBlancall は MIT ライセンスで公開されています。同意が必要な契約はありません。ライセンス全文はアプリと一緒にインストールされます。\r\n\r\n学習データは %LOCALAPPDATA%\\Blancall に保存され、アンインストールしても削除されません。" },
                    { "LicenseLink", "MIT ライセンス全文を表示" },
                    { "LanguageLabel", "セットアップの言語" },
                    { "OptionsTitle", "インストール先の選択" },
                    { "DirLabel", "インストール先：" },
                    { "Browse", "参照..." },
                    { "DesktopShortcut", "デスクトップにショートカットを作成" },
                    { "StartMenuShortcut", "スタートメニューにショートカットを作成（アンインストーラーを含む）" },
                    { "DataNote", "学習データは常に %LOCALAPPDATA%\\Blancall に保存されます。アンインストールではプログラム本体とショートカットのみ削除します。" },
                    { "ProgressTitle", "Blancall をインストールしています。しばらくお待ちください…" },
                    { "DoneTitle", "インストールが完了しました。" },
                    { "Launch", "Blancall を今すぐ起動" },
                    { "Back", "戻る" },
                    { "Next", "次へ" },
                    { "Install", "インストール" },
                    { "Cancel", "キャンセル" },
                    { "Finish", "完了" },
                    { "Close", "閉じる" },
                    { "LicenseTitle", "MIT ライセンス" },
                    { "ErrDirEmpty", "インストール先を選択してください。" },
                    { "ErrRunning", "Blancall が実行中です。トレイから終了してからインストールしてください。" },
                    { "ErrLocked", "Blancall が実行中か、インストールファイルが使用されています。Blancall を完全に終了（トレイアイコンを含む）してから再試行してください。" },
                    { "ErrStillLocked", "ファイルが他のプログラムに使用されています（セキュリティソフトのスキャン中の可能性があります）。しばらくしてから再試行してください：" },
                    { "ErrInstall", "インストールに失敗しました：" },
                    { "ProgressPageTitle", "インストール中" },
                    { "FinishPageTitle", "インストール完了" },
                    { "WarnShortcut", "ショートカットを作成できませんでした：" },
                    { "WarnRegistry", "アンインストール情報を登録できませんでした：" },
                    { "ErrWriteDenied", "ファイルに書き込めません（使用中または読み取り専用）：" },
                    { "ShortcutDesc", "Blancall - ローカルで穴埋め暗記" },
                    { "UninstallShortcut", "Blancall をアンインストール" },
                    { "UninstallConfirmTitle", "Blancall をアンインストール" },
                    { "UninstallConfirm", "Blancall をアンインストールしますか？\r\n\r\n%LOCALAPPDATA%\\Blancall の学習データは削除されません。" },
                    { "UninstallNotFound", "Blancall のプログラムフォルダーが見つかりません。" },
                    { "UninstallRunning", "Blancall が実行中です。トレイから終了してからアンインストールしてください。" },
                    { "UninstallFailed", "アンインストールに失敗しました：" },
                }
            },
            {
                "ko", new Dictionary<string, string>
                {
                    { "AppTitle", "Blancall 설치 프로그램" },
                    { "WelcomeTitle", "Blancall에 오신 것을 환영합니다" },
                    { "Intro", "이 마법사는 Blancall을 컴퓨터에 설치합니다.\r\n\r\nBlancall은 MIT 라이선스로 배포됩니다. 동의해야 하는 계약은 없으며, 라이선스 전문은 앱과 함께 설치됩니다.\r\n\r\n학습 데이터는 %LOCALAPPDATA%\\Blancall에 저장되며 제거해도 삭제되지 않습니다." },
                    { "LicenseLink", "MIT 라이선스 전체 보기" },
                    { "LanguageLabel", "설치 프로그램 언어" },
                    { "OptionsTitle", "설치 위치 선택" },
                    { "DirLabel", "설치 위치:" },
                    { "Browse", "찾아보기..." },
                    { "DesktopShortcut", "바탕 화면에 바로 가기 만들기" },
                    { "StartMenuShortcut", "시작 메뉴에 바로 가기 만들기(제거 프로그램 포함)" },
                    { "DataNote", "학습 데이터는 항상 %LOCALAPPDATA%\\Blancall에 저장됩니다. 제거 시 프로그램 폴더와 바로 가기만 삭제됩니다." },
                    { "ProgressTitle", "Blancall을 설치하는 중입니다. 잠시 기다려 주세요..." },
                    { "DoneTitle", "설치가 완료되었습니다." },
                    { "Launch", "Blancall 지금 시작" },
                    { "Back", "이전" },
                    { "Next", "다음" },
                    { "Install", "설치" },
                    { "Cancel", "취소" },
                    { "Finish", "완료" },
                    { "Close", "닫기" },
                    { "LicenseTitle", "MIT 라이선스" },
                    { "ErrDirEmpty", "설치 폴더를 선택하세요." },
                    { "ErrRunning", "Blancall이 실행 중입니다. 트레이에서 종료한 후 설치하세요." },
                    { "ErrLocked", "Blancall이 실행 중이거나 설치 파일이 사용 중입니다. Blancall을 완전히 종료한 후(트레이 아이콘 포함) 다시 시도하세요." },
                    { "ErrStillLocked", "파일이 다른 프로그램에서 사용 중입니다(보안 소프트웨어 검사 중일 수 있음). 잠시 후 다시 시도하세요: " },
                    { "ErrInstall", "설치 실패:" },
                    { "ProgressPageTitle", "설치 중" },
                    { "FinishPageTitle", "설치 완료" },
                    { "WarnShortcut", "바로 가기를 만들지 못했습니다: " },
                    { "WarnRegistry", "제거 항목을 등록하지 못했습니다: " },
                    { "ErrWriteDenied", "파일에 쓸 수 없습니다(사용 중이거나 읽기 전용): " },
                    { "ShortcutDesc", "Blancall - 로컬 빈칸 암기" },
                    { "UninstallShortcut", "Blancall 제거" },
                    { "UninstallConfirmTitle", "Blancall 제거" },
                    { "UninstallConfirm", "Blancall을 제거하시겠습니까?\r\n\r\n%LOCALAPPDATA%\\Blancall의 학습 데이터는 삭제되지 않습니다." },
                    { "UninstallNotFound", "Blancall 프로그램 폴더를 찾을 수 없습니다." },
                    { "UninstallRunning", "Blancall이 실행 중입니다. 트레이에서 종료한 후 제거하세요." },
                    { "UninstallFailed", "제거 실패:" },
                }
            },
            {
                "ru", new Dictionary<string, string>
                {
                    { "AppTitle", "Установка Blancall" },
                    { "WelcomeTitle", "Добро пожаловать в Blancall" },
                    { "Intro", "Этот мастер установит Blancall на ваш компьютер.\r\n\r\nBlancall распространяется по лицензии MIT. Принимать какие-либо соглашения не нужно; текст лицензии устанавливается вместе с приложением.\r\n\r\nУчебные данные хранятся в %LOCALAPPDATA%\\Blancall и не удаляются при удалении программы." },
                    { "LicenseLink", "Открыть полный текст лицензии MIT" },
                    { "LanguageLabel", "Язык установщика" },
                    { "OptionsTitle", "Выбор папки установки" },
                    { "DirLabel", "Установить в:" },
                    { "Browse", "Обзор..." },
                    { "DesktopShortcut", "Создать ярлык на рабочем столе" },
                    { "StartMenuShortcut", "Создать ярлык в меню «Пуск» (с деинсталлятором)" },
                    { "DataNote", "Учебные данные всегда хранятся в %LOCALAPPDATA%\\Blancall. При удалении удаляются только папка программы и ярлыки." },
                    { "ProgressTitle", "Установка Blancall, подождите..." },
                    { "DoneTitle", "Установка завершена." },
                    { "Launch", "Запустить Blancall сейчас" },
                    { "Back", "Назад" },
                    { "Next", "Далее" },
                    { "Install", "Установить" },
                    { "Cancel", "Отмена" },
                    { "Finish", "Готово" },
                    { "Close", "Закрыть" },
                    { "LicenseTitle", "Лицензия MIT" },
                    { "ErrDirEmpty", "Выберите папку установки." },
                    { "ErrRunning", "Blancall запущен. Закройте его из трея перед установкой." },
                    { "ErrLocked", "Blancall запущен или файл установки занят. Полностью закройте Blancall (включая значок в трее) и повторите попытку." },
                    { "ErrStillLocked", "Файл все еще занят другой программой (возможно, идет проверка антивирусом). Повторите попытку позже: " },
                    { "ErrInstall", "Ошибка установки:" },
                    { "ProgressPageTitle", "Установка" },
                    { "FinishPageTitle", "Установка завершена" },
                    { "WarnShortcut", "Не удалось создать ярлык: " },
                    { "WarnRegistry", "Не удалось зарегистрировать деинсталлятор: " },
                    { "ErrWriteDenied", "Не удалось записать файл (занят или только для чтения): " },
                    { "ShortcutDesc", "Blancall - локальное запоминание с пропусками" },
                    { "UninstallShortcut", "Удалить Blancall" },
                    { "UninstallConfirmTitle", "Удалить Blancall" },
                    { "UninstallConfirm", "Удалить Blancall?\r\n\r\nУчебные данные в %LOCALAPPDATA%\\Blancall не будут удалены." },
                    { "UninstallNotFound", "Папка программы Blancall не найдена." },
                    { "UninstallRunning", "Blancall запущен. Закройте его из трея перед удалением." },
                    { "UninstallFailed", "Ошибка удаления:" },
                }
            },
        };

    public static string T(string key)
    {
        Dictionary<string, string> map;
        if (Table.TryGetValue(Current, out map) && map.ContainsKey(key)) return map[key];
        if (Table.TryGetValue("en", out map) && map.ContainsKey(key)) return map[key];
        return key;
    }

    /** 跟随系统 UI 语言：简繁中文、英、日、韩、俄；其他语言回退英文。 */
    public static string Detect()
    {
#if LANG_TEST
        string forced = Environment.GetEnvironmentVariable("BLANCALL_LANG");
        foreach (string[] language in Languages)
        {
            if (string.Equals(language[0], forced, StringComparison.OrdinalIgnoreCase)) return language[0];
        }
#endif
        string name = (CultureInfo.CurrentUICulture.Name ?? "").ToLowerInvariant();
        if (name.StartsWith("zh"))
        {
            if (name.Contains("tw") || name.Contains("hk") || name.Contains("mo") || name.Contains("hant"))
            {
                return "zh-TW";
            }
            return "zh-CN";
        }
        if (name.StartsWith("ja")) return "ja";
        if (name.StartsWith("ko")) return "ko";
        if (name.StartsWith("ru")) return "ru";
        return "en";
    }
}
