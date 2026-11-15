using System;
using System.Drawing;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Windows.Forms;

/// <summary>
/// .NET Framework 的 WinForms 不识别 PerMonitorV2 清单，在 200% 屏幕上会按 96 DPI 建窗，
/// 于是窗口只有设计尺寸的一半。这里按窗口实际 DPI 统一缩放控件位置、尺寸与字体。
/// </summary>
internal static class DpiScale
{
    [DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

    public static void Apply(Form form, Size design)
    {
        float factor = Factor(form);
        if (factor < 1.01f) return;

        // 只缩放布局，不再乘字号：点制字号由 GDI 按显示器 DPI 自动放大，再乘一次会得到 4 倍字。
        ScaleBounds(form, factor);
        form.ClientSize = new Size(Round(design.Width, factor), Round(design.Height, factor));
    }

    private static float Factor(Control control)
    {
#if DPI_TEST
        float forced;
        if (float.TryParse(Environment.GetEnvironmentVariable("BLANCALL_DPI_TEST"),
                NumberStyles.Float, CultureInfo.InvariantCulture, out forced) &&
            forced >= 0.5f && forced <= 4f)
        {
            return forced;
        }
#endif
        try
        {
            uint dpi = GetDpiForWindow(control.Handle);
            if (dpi >= 48 && dpi <= 960) return dpi / 96f;
        }
        catch
        {
        }
        using (Graphics graphics = control.CreateGraphics()) return graphics.DpiX / 96f;
    }

    private static void ScaleBounds(Control parent, float factor)
    {
        foreach (Control child in parent.Controls)
        {
            if (child.Dock == DockStyle.None)
            {
                child.Location = new Point(Round(child.Left, factor), Round(child.Top, factor));
                child.Size = new Size(Round(child.Width, factor), Round(child.Height, factor));
            }
            else if (child.Dock == DockStyle.Top || child.Dock == DockStyle.Bottom)
            {
                child.Height = Round(child.Height, factor);
            }
            else if (child.Dock == DockStyle.Left || child.Dock == DockStyle.Right)
            {
                child.Width = Round(child.Width, factor);
            }
            ScaleBounds(child, factor);
        }
    }

    private static int Round(int value, float factor)
    {
        return (int)Math.Round(value * factor, MidpointRounding.AwayFromZero);
    }
}
