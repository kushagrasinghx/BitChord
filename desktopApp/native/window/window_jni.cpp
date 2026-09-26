#define _WIN32_WINNT 0x0A00
#include <jni.h>

#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <dwmapi.h>
#include <windowsx.h>

#include <string>

namespace {

HWND g_window = nullptr;
WNDPROC g_original_proc = nullptr;

struct FindContext {
    const wchar_t* title;
    HWND found;
};

BOOL CALLBACK find_own_window(HWND window, LPARAM param) {
    auto* context = reinterpret_cast<FindContext*>(param);
    DWORD pid = 0;
    GetWindowThreadProcessId(window, &pid);
    if (pid != GetCurrentProcessId() || !IsWindowVisible(window)) return TRUE;

    wchar_t buffer[256] = {};
    GetWindowTextW(window, buffer, 255);
    if (wcscmp(buffer, context->title) != 0) return TRUE;
    context->found = window;
    return FALSE;
}

HWND find_window(JNIEnv* env, jstring title) {
    if (g_window != nullptr && IsWindow(g_window)) return g_window;
    const jchar* chars = env->GetStringChars(title, nullptr);
    const jsize length = env->GetStringLength(title);
    std::wstring wanted(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(title, chars);
    FindContext context{wanted.c_str(), nullptr};
    EnumWindows(find_own_window, reinterpret_cast<LPARAM>(&context));
    return context.found;
}

int resize_border(HWND window, bool horizontal) {
    const UINT dpi = GetDpiForWindow(window);
    const int frame = GetSystemMetricsForDpi(horizontal ? SM_CXFRAME : SM_CYFRAME, dpi);
    const int padding = GetSystemMetricsForDpi(SM_CXPADDEDBORDER, dpi);
    return frame + padding;
}

LRESULT CALLBACK frame_proc(HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
    if (message == WM_NCCALCSIZE && wparam == TRUE) {
        // Keep the native overlapped-window styles but give the full surface to Compose. DWM still
        // owns the outer frame, shadow and transitions; only the standard caption is removed.
        if (IsZoomed(window)) {
            // A frameless client normally expands to the maximized window rectangle, which can
            // extend a few pixels into a taskbar docked at the top of the monitor. Use the monitor
            // work area for the client portion so the first row is never hidden behind the taskbar.
            MONITORINFO monitor{sizeof(MONITORINFO)};
            const HMONITOR handle = MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST);
            if (handle != nullptr && GetMonitorInfoW(handle, &monitor) != FALSE) {
                auto* sizes = reinterpret_cast<NCCALCSIZE_PARAMS*>(lparam);
                sizes->rgrc[0] = monitor.rcWork;
            }
        }
        return 0;
    }

    if (message == WM_NCHITTEST && !IsZoomed(window)) {
        POINT point{GET_X_LPARAM(lparam), GET_Y_LPARAM(lparam)};
        RECT bounds{};
        GetWindowRect(window, &bounds);
        const int border_x = resize_border(window, true);
        const int border_y = resize_border(window, false);
        const bool left = point.x < bounds.left + border_x;
        const bool right = point.x >= bounds.right - border_x;
        const bool top = point.y < bounds.top + border_y;
        const bool bottom = point.y >= bounds.bottom - border_y;
        if (top && left) return HTTOPLEFT;
        if (top && right) return HTTOPRIGHT;
        if (bottom && left) return HTBOTTOMLEFT;
        if (bottom && right) return HTBOTTOMRIGHT;
        if (left) return HTLEFT;
        if (right) return HTRIGHT;
        if (top) return HTTOP;
        if (bottom) return HTBOTTOM;
    }

    WNDPROC original = g_original_proc;
    if (message == WM_NCDESTROY) {
        g_window = nullptr;
        g_original_proc = nullptr;
    }
    return original != nullptr
        ? CallWindowProcW(original, window, message, wparam, lparam)
        : DefWindowProcW(window, message, wparam, lparam);
}

bool install_frame(HWND window) {
    if (window == nullptr) return false;
    if (window == g_window && g_original_proc != nullptr) return true;

    SetLastError(0);
    auto previous = reinterpret_cast<WNDPROC>(
        SetWindowLongPtrW(window, GWLP_WNDPROC, reinterpret_cast<LONG_PTR>(frame_proc)));
    if (previous == nullptr && GetLastError() != 0) return false;

    g_window = window;
    g_original_proc = previous;

    LONG_PTR style = GetWindowLongPtrW(window, GWL_STYLE);
    style &= ~static_cast<LONG_PTR>(WS_POPUP);
    style |= WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_THICKFRAME |
        WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
    SetWindowLongPtrW(window, GWL_STYLE, style);

    // Ask DWM to own the non-client rendering and Windows 11 corner policy. Unlike an AWT shape,
    // these do not clip the surface and are automatically disabled when maximized or snapped.
    const DWMNCRENDERINGPOLICY policy = DWMNCRP_ENABLED;
    DwmSetWindowAttribute(
        window, DWMWA_NCRENDERING_POLICY, &policy, sizeof(policy));
#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif
    const int rounded = 2;  // DWMWCP_ROUND; kept numeric so older SDKs can still build the bridge.
    DwmSetWindowAttribute(
        window, static_cast<DWMWINDOWATTRIBUTE>(DWMWA_WINDOW_CORNER_PREFERENCE),
        &rounded, sizeof(rounded));

    MARGINS margins{1, 1, 1, 1};
    DwmExtendFrameIntoClientArea(window, &margins);
    SetWindowPos(
        window, nullptr, 0, 0, 0, 0,
        SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
    return true;
}

bool send_system_command(UINT command) {
    HWND window = g_window;
    return window != nullptr && IsWindow(window) &&
        PostMessageW(window, WM_SYSCOMMAND, command, 0) != 0;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeInstall(
    JNIEnv* env, jclass, jstring title) {
    return install_frame(find_window(env, title)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeMinimize(JNIEnv*, jclass) {
    return send_system_command(SC_MINIMIZE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsFrame_nativeToggleMaximize(JNIEnv*, jclass) {
    HWND window = g_window;
    if (window == nullptr || !IsWindow(window)) return JNI_FALSE;
    return send_system_command(IsZoomed(window) ? SC_RESTORE : SC_MAXIMIZE)
        ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
