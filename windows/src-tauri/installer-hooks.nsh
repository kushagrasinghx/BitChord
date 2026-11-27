; Tauri installer hooks (tauri.conf.json, bundle.windows.nsis.installerHooks).
;
; This app succeeds upstream's Compose desktop app (desktopApp, 1.7 to 1.8.x, removed
; in e4dd63d8), so installing it replaces that one, as an update would. The old app
; was an MSI with a fixed upgrade code (desktopApp/build.gradle.kts, upgradeUuid), so
; Windows Installer lists every installed version of it exactly; nothing else matches.
; Left in place, it would share this app's folder (%LOCALAPPDATA%\BitChord) and its
; desktop shortcut.
;
; It registered per machine, so removing it can raise a UAC prompt; if that is
; declined, the old app stays and this install carries on.

!define OLD_DESKTOP_UPGRADE_CODE "{8F3C1D24-6A2B-4F5E-9C17-2B8D54E0A913}"

!macro NSIS_HOOK_PREINSTALL
  Push $0
  Push $1
  Push $2
  StrCpy $2 ""
  old_desktop_next:
    ; Index 0 each time: the list shrinks as each version is removed.
    System::Call 'msi::MsiEnumRelatedProductsW(w "${OLD_DESKTOP_UPGRADE_CODE}", i 0, i 0, w .r0) i .r1'
    StrCmp $1 0 0 old_desktop_done
    ; The same product again means its removal failed or was declined: stop asking.
    StrCmp $0 $2 old_desktop_done
    StrCpy $2 $0
    DetailPrint "Replacing the previous BitChord desktop app ($0)"
    IfSilent 0 +3
      ExecWait '"$SYSDIR\msiexec.exe" /x $0 /qn /norestart'
      Goto old_desktop_next
    ExecWait '"$SYSDIR\msiexec.exe" /x $0 /passive /norestart'
    Goto old_desktop_next
  old_desktop_done:
  Pop $2
  Pop $1
  Pop $0
!macroend
