// Windows System Media Transport Controls, for MinGW builds.
//
// The same library as smtc_jni.cpp — same exports, same behaviour — written
// against the raw WinRT ABI headers instead of C++/WinRT, which MinGW does not
// ship. Without it a locally built desktop app had no media keys and no card in
// the volume flyout; only the MSVC-built CI artifacts did.
//
// The threading is unchanged: one thread of this library's own owns a hidden
// window, the SMTC object hung off it, and the message loop that delivers both
// button presses and the updates posted from Kotlin.

#include <jni.h>

#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <roapi.h>
#include <winstring.h>
#include <systemmediatransportcontrolsinterop.h>
// MinGW's windows.foundation.h specialises IReference for both BYTE and boolean,
// which are the same unsigned char there, and refuses to compile. Nothing here
// uses either; skipping the BYTE one is enough.
#define ____FIReference_1_BYTE_INTERFACE_DEFINED__
#include <windows.foundation.h>
#include <windows.media.h>
#include <windows.storage.streams.h>

#include <atomic>
#include <mutex>
#include <string>
#include <thread>

using namespace ABI::Windows::Foundation;
using namespace ABI::Windows::Media;
using namespace ABI::Windows::Storage::Streams;

namespace {

JavaVM* g_vm = nullptr;
std::thread g_thread;
std::atomic<bool> g_running{false};
std::atomic<bool> g_finished{false};
HWND g_window = nullptr;
ISystemMediaTransportControls* g_controls = nullptr;
EventRegistrationToken g_button_token{};
std::mutex g_mutex;

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

template <typename T>
void release(T*& value) {
    if (value != nullptr) {
        value->Release();
        value = nullptr;
    }
}

/** An HSTRING that frees itself. */
class Hstring {
public:
    explicit Hstring(const std::wstring& value) {
        WindowsCreateString(value.c_str(), static_cast<UINT32>(value.size()), &handle_);
    }
    explicit Hstring(const wchar_t* value) {
        WindowsCreateString(value, static_cast<UINT32>(wcslen(value)), &handle_);
    }
    ~Hstring() {
        if (handle_ != nullptr) WindowsDeleteString(handle_);
    }
    Hstring(const Hstring&) = delete;
    Hstring& operator=(const Hstring&) = delete;
    HSTRING get() const { return handle_; }

private:
    HSTRING handle_ = nullptr;
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

using ButtonHandler = ITypedEventHandler<SystemMediaTransportControls*, SystemMediaTransportControlsButtonPressedEventArgs*>;

/** The ButtonPressed delegate. Lives as long as the library; its count only guards misuse. */
class ButtonPressed final : public ButtonHandler {
public:
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID id, void** out) override {
        if (out == nullptr) return E_POINTER;
        if (id == __uuidof(IUnknown) || id == __uuidof(IAgileObject) || id == __uuidof(ButtonHandler)) {
            *out = static_cast<ButtonHandler*>(this);
            AddRef();
            return S_OK;
        }
        *out = nullptr;
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++count_; }
    ULONG STDMETHODCALLTYPE Release() override { return --count_; }

    HRESULT STDMETHODCALLTYPE Invoke(
        ISystemMediaTransportControls*, ISystemMediaTransportControlsButtonPressedEventArgs* args) override {
        if (args == nullptr) return S_OK;
        SystemMediaTransportControlsButton button;
        if (FAILED(args->get_Button(&button))) return S_OK;
        switch (button) {
            case SystemMediaTransportControlsButton_Play: notify(BUTTON_PLAY); break;
            case SystemMediaTransportControlsButton_Pause: notify(BUTTON_PAUSE); break;
            case SystemMediaTransportControlsButton_Next: notify(BUTTON_NEXT); break;
            case SystemMediaTransportControlsButton_Previous: notify(BUTTON_PREVIOUS); break;
            case SystemMediaTransportControlsButton_Stop: notify(BUTTON_STOP); break;
            default: break;
        }
        return S_OK;
    }

private:
    std::atomic<ULONG> count_{1};
};

ButtonPressed g_button_handler;

/** WinRT TimeSpan is in 100 ns ticks. */
TimeSpan ticks(long long ms) { return TimeSpan{ms * 10'000}; }

void apply_thumbnail(ISystemMediaTransportControlsDisplayUpdater* updater, const std::wstring& art) {
    if (art.empty()) {
        updater->put_Thumbnail(nullptr);
        return;
    }
    // A remote URL is fine: the shell fetches it itself.
    IUriRuntimeClassFactory* uris = nullptr;
    IUriRuntimeClass* uri = nullptr;
    IRandomAccessStreamReferenceStatics* references = nullptr;
    IRandomAccessStreamReference* reference = nullptr;
    const Hstring uri_class(RuntimeClass_Windows_Foundation_Uri);
    const Hstring reference_class(RuntimeClass_Windows_Storage_Streams_RandomAccessStreamReference);
    const Hstring value(art);
    if (SUCCEEDED(RoGetActivationFactory(uri_class.get(), __uuidof(IUriRuntimeClassFactory), reinterpret_cast<void**>(&uris))) &&
        SUCCEEDED(uris->CreateUri(value.get(), &uri)) &&
        SUCCEEDED(RoGetActivationFactory(
            reference_class.get(), __uuidof(IRandomAccessStreamReferenceStatics), reinterpret_cast<void**>(&references))) &&
        SUCCEEDED(references->CreateFromUri(uri, &reference))) {
        updater->put_Thumbnail(reference);
    } else {
        updater->put_Thumbnail(nullptr);
    }
    release(reference);
    release(references);
    release(uri);
    release(uris);
}

void apply_timeline(const Update& update) {
    ISystemMediaTransportControls2* controls2 = nullptr;
    if (FAILED(g_controls->QueryInterface(__uuidof(ISystemMediaTransportControls2), reinterpret_cast<void**>(&controls2)))) {
        return;
    }
    IInspectable* instance = nullptr;
    ISystemMediaTransportControlsTimelineProperties* timeline = nullptr;
    const Hstring timeline_class(RuntimeClass_Windows_Media_SystemMediaTransportControlsTimelineProperties);
    if (SUCCEEDED(RoActivateInstance(timeline_class.get(), &instance)) &&
        SUCCEEDED(instance->QueryInterface(
            __uuidof(ISystemMediaTransportControlsTimelineProperties), reinterpret_cast<void**>(&timeline)))) {
        timeline->put_StartTime(ticks(0));
        timeline->put_MinSeekTime(ticks(0));
        timeline->put_Position(ticks(update.position_ms));
        timeline->put_MaxSeekTime(ticks(update.duration_ms));
        timeline->put_EndTime(ticks(update.duration_ms));
        controls2->UpdateTimelineProperties(timeline);
    }
    release(timeline);
    release(instance);
    release(controls2);
}

void apply(const Update& update) {
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_controls == nullptr) return;

    g_controls->put_PlaybackStatus(update.playing ? MediaPlaybackStatus_Playing : MediaPlaybackStatus_Paused);

    ISystemMediaTransportControlsDisplayUpdater* updater = nullptr;
    if (SUCCEEDED(g_controls->get_DisplayUpdater(&updater)) && updater != nullptr) {
        updater->put_Type(MediaPlaybackType_Music);
        IMusicDisplayProperties* music = nullptr;
        if (SUCCEEDED(updater->get_MusicProperties(&music)) && music != nullptr) {
            const Hstring title(update.title);
            const Hstring artist(update.artist);
            music->put_Title(title.get());
            music->put_Artist(artist.get());
            IMusicDisplayProperties2* music2 = nullptr;
            if (SUCCEEDED(music->QueryInterface(__uuidof(IMusicDisplayProperties2), reinterpret_cast<void**>(&music2)))) {
                const Hstring album(update.album);
                music2->put_AlbumTitle(album.get());
                release(music2);
            }
            release(music);
        }
        apply_thumbnail(updater, update.art);
        updater->Update();
        release(updater);
    }

    apply_timeline(update);
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

bool create_controls() {
    ISystemMediaTransportControlsInterop* interop = nullptr;
    const Hstring smtc_class(RuntimeClass_Windows_Media_SystemMediaTransportControls);
    HRESULT status = RoGetActivationFactory(
        smtc_class.get(), __uuidof(ISystemMediaTransportControlsInterop), reinterpret_cast<void**>(&interop));
    if (SUCCEEDED(status)) {
        status = interop->GetForWindow(
            g_window, __uuidof(ISystemMediaTransportControls), reinterpret_cast<void**>(&g_controls));
    }
    release(interop);
    if (FAILED(status) || g_controls == nullptr) return false;

    g_controls->put_IsEnabled(true);
    g_controls->put_IsPlayEnabled(true);
    g_controls->put_IsPauseEnabled(true);
    g_controls->put_IsNextEnabled(true);
    g_controls->put_IsPreviousEnabled(true);
    g_controls->put_IsStopEnabled(true);
    g_controls->add_ButtonPressed(&g_button_handler, &g_button_token);
    return true;
}

void pump() {
    const HRESULT apartment = RoInitialize(RO_INIT_SINGLETHREADED);

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

    if (g_window != nullptr && create_controls()) {
        g_running = true;
        g_finished = true;
        MSG message{};
        while (GetMessageW(&message, nullptr, 0, 0) > 0) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    } else {
        g_finished = true;
    }

    {
        std::lock_guard<std::mutex> guard(g_mutex);
        if (g_controls != nullptr) {
            g_controls->remove_ButtonPressed(g_button_token);
            g_controls->put_IsEnabled(false);
            release(g_controls);
        }
    }
    if (g_window != nullptr) {
        DestroyWindow(g_window);
        g_window = nullptr;
    }
    g_running = false;
    if (SUCCEEDED(apartment)) RoUninitialize();
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
    g_finished = false;
    g_thread = std::thread(pump);
    for (int i = 0; i < 400 && !g_finished; ++i) {
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
