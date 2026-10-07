// Windows System Media Transport Controls, for the desktop player.
//
// SMTC is what puts a track in the volume flyout, on the lock screen and under
// the media keys. It is WinRT, which the JVM cannot reach, so this is the same
// arrangement the Automix analyser already uses: a small library beside the
// application, spoken to over JNI.
//
// Everything happens on one thread of this library's own. SMTC is acquired from
// a window handle, and a window needs a message loop to deliver anything — so
// the thread creates a hidden top-level window, pumps it, and lives as long as
// the player does. The window is never shown; it exists only to be the handle
// SMTC is asked for.

#include <jni.h>

// Both before <windows.h>: its `min`/`max` macros break the standard headers
// C++/WinRT includes, which is the usual way this file would fail to compile.
#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <systemmediatransportcontrolsinterop.h>

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Storage.Streams.h>

#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <thread>

using namespace winrt;
using namespace winrt::Windows::Media;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Storage::Streams;

namespace {

JavaVM* g_vm = nullptr;
std::thread g_thread;
std::atomic<bool> g_running{false};
std::atomic<DWORD> g_thread_id{0};
HWND g_window = nullptr;
SystemMediaTransportControls g_controls{nullptr};
std::mutex g_mutex;

// Posted to the pump thread rather than acted on from the caller's: every
// WinRT object here belongs to the apartment that created it.
constexpr UINT WM_BITCHORD_UPDATE = WM_APP + 1;
constexpr UINT WM_BITCHORD_QUIT = WM_APP + 2;

struct Update {
    std::wstring title;
    std::wstring artist;
    std::wstring album;
    std::wstring art;
    bool playing;
    long long position_ms;
    long long duration_ms;
};

std::wstring widen(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jchar* chars = env->GetStringChars(value, nullptr);
    const jsize length = env->GetStringLength(value);
    std::wstring out(reinterpret_cast<const wchar_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(value, chars);
    return out;
}

// The button ids Kotlin knows; see DesktopWindowsMedia.
constexpr jint BUTTON_PLAY = 0;
constexpr jint BUTTON_PAUSE = 1;
constexpr jint BUTTON_NEXT = 2;
constexpr jint BUTTON_PREVIOUS = 3;
constexpr jint BUTTON_STOP = 4;

void notify(jint button) {
    if (g_vm == nullptr) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void**>(&env), nullptr) != JNI_OK) return;
        attached = true;
    }
    jclass type = env->FindClass("com/music/bitchord/desktop/DesktopWindowsMedia");
    if (type != nullptr) {
        jmethodID method = env->GetStaticMethodID(type, "onButton", "(I)V");
        if (method != nullptr) env->CallStaticVoidMethod(type, method, button);
        env->DeleteLocalRef(type);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) g_vm->DetachCurrentThread();
}

void apply(const Update& update) {
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_controls == nullptr) return;

    g_controls.PlaybackStatus(
        update.playing ? MediaPlaybackStatus::Playing : MediaPlaybackStatus::Paused);

    auto updater = g_controls.DisplayUpdater();
    updater.Type(MediaPlaybackType::Music);
    auto music = updater.MusicProperties();
    music.Title(update.title);
    music.Artist(update.artist);
    music.AlbumTitle(update.album);
    if (!update.art.empty()) {
        // A remote URL is fine: the shell fetches it itself.
        try {
            updater.Thumbnail(RandomAccessStreamReference::CreateFromUri(Uri(update.art)));
        } catch (...) {
            updater.Thumbnail(nullptr);
        }
    } else {
        updater.Thumbnail(nullptr);
    }
    updater.Update();

    SystemMediaTransportControlsTimelineProperties timeline;
    timeline.StartTime(TimeSpan{0});
    timeline.MinSeekTime(TimeSpan{0});
    timeline.Position(std::chrono::milliseconds{update.position_ms});
    timeline.MaxSeekTime(std::chrono::milliseconds{update.duration_ms});
    timeline.EndTime(std::chrono::milliseconds{update.duration_ms});
    g_controls.UpdateTimelineProperties(timeline);
}

LRESULT CALLBACK WindowProc(HWND window, UINT message, WPARAM wparam, LPARAM lparam) {
    if (message == WM_BITCHORD_UPDATE) {
        auto* update = reinterpret_cast<Update*>(lparam);
        apply(*update);
        delete update;
        return 0;
    }
    if (message == WM_BITCHORD_QUIT) {
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(window, message, wparam, lparam);
}

void pump() {
    init_apartment(apartment_type::single_threaded);
    g_thread_id = GetCurrentThreadId();

    WNDCLASSEXW description{};
    description.cbSize = sizeof(description);
    description.lpfnWndProc = WindowProc;
    description.hInstance = GetModuleHandleW(nullptr);
    description.lpszClassName = L"BitChordSmtcWindow";
    RegisterClassExW(&description);

    // Never shown. SMTC only needs a handle to hang itself off, and a visible
    // window here would be a second BitChord in the taskbar.
    g_window = CreateWindowExW(
        0, L"BitChordSmtcWindow", L"BitChord", WS_OVERLAPPED,
        0, 0, 0, 0, nullptr, nullptr, description.hInstance, nullptr);
    if (g_window == nullptr) {
        g_running = false;
        return;
    }

    try {
        auto interop = get_activation_factory<SystemMediaTransportControls, ISystemMediaTransportControlsInterop>();
        check_hresult(interop->GetForWindow(
            g_window, guid_of<SystemMediaTransportControls>(), put_abi(g_controls)));
    } catch (...) {
        g_running = false;
        DestroyWindow(g_window);
        g_window = nullptr;
        return;
    }

    g_controls.IsEnabled(true);
    g_controls.IsPlayEnabled(true);
    g_controls.IsPauseEnabled(true);
    g_controls.IsNextEnabled(true);
    g_controls.IsPreviousEnabled(true);
    g_controls.IsStopEnabled(true);
    g_controls.ButtonPressed([](auto&&, const SystemMediaTransportControlsButtonPressedEventArgs& args) {
        switch (args.Button()) {
            case SystemMediaTransportControlsButton::Play: notify(BUTTON_PLAY); break;
            case SystemMediaTransportControlsButton::Pause: notify(BUTTON_PAUSE); break;
            case SystemMediaTransportControlsButton::Next: notify(BUTTON_NEXT); break;
            case SystemMediaTransportControlsButton::Previous: notify(BUTTON_PREVIOUS); break;
            case SystemMediaTransportControlsButton::Stop: notify(BUTTON_STOP); break;
            default: break;
        }
    });

    g_running = true;

    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }

    {
        std::lock_guard<std::mutex> guard(g_mutex);
        if (g_controls != nullptr) {
            g_controls.IsEnabled(false);
            g_controls = nullptr;
        }
    }
    if (g_window != nullptr) {
        DestroyWindow(g_window);
        g_window = nullptr;
    }
    g_running = false;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsMedia_nativeStart(JNIEnv*, jobject) {
    if (g_thread.joinable()) return g_running ? JNI_TRUE : JNI_FALSE;
    g_thread = std::thread(pump);
    // The controls exist or they do not within a moment; a caller that had to
    // poll would be the only one who cared.
    for (int i = 0; i < 200 && !g_running && g_thread_id == 0; ++i) {
        Sleep(5);
    }
    for (int i = 0; i < 100 && !g_running; ++i) {
        Sleep(5);
    }
    return g_running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsMedia_nativeUpdate(
    JNIEnv* env, jobject, jstring title, jstring artist, jstring album, jstring art,
    jboolean playing, jlong position_ms, jlong duration_ms) {
    if (!g_running) return;
    auto* update = new Update{
        widen(env, title), widen(env, artist), widen(env, album), widen(env, art),
        playing == JNI_TRUE, position_ms, duration_ms,
    };
    if (!PostMessageW(g_window, WM_BITCHORD_UPDATE, 0, reinterpret_cast<LPARAM>(update))) {
        delete update;
    }
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopWindowsMedia_nativeStop(JNIEnv*, jobject) {
    if (g_window != nullptr) PostMessageW(g_window, WM_BITCHORD_QUIT, 0, 0);
    if (g_thread.joinable()) g_thread.join();
}

}  // extern "C"
