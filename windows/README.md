# BitChord for Windows

This folder contains the native WinUI 3 companion for BitChord. It is intentionally
separate from the Android Gradle project so both clients can evolve without
coupling their build systems.

## Requirements

- Windows 10 version 1809 or later
- Visual Studio 2022 17.10 or later
- **.NET Desktop Development** and **Windows App SDK** workloads

## Run

Open `BitChord.sln`, select `BitChord.WinUI`, choose `x64`, and press **F5**.
The app uses Windows 11 Mica/Acrylic-compatible surfaces, adaptive navigation,
keyboard-friendly controls, a persistent mini-player, and reduced-motion settings.

## Build the installer

Select the `BitChord.Package` project and build `Release | x64`. Visual Studio
places the unsigned MSIX bundle under `windows\artifacts`. For distribution, configure a certificate in the project's **Package**
properties and enable `AppxPackageSigningEnabled`. The workflow signs the
package using the `BITCHORD_PFX_BASE64` and `BITCHORD_PFX_PASSWORD` repository
secrets when they are configured. To create the first secret, encode the PFX
without committing it:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\BitChord-signing.pfx"))
```

Add that output as `BITCHORD_PFX_BASE64` and the PFX password as
`BITCHORD_PFX_PASSWORD` under **Settings > Secrets and variables > Actions**.
If the secrets are absent, pull requests use a temporary self-signed
certificate instead.

The package project links the existing repository `Logo.png` instead of
duplicating the Android artwork. No credentials or service keys are included.

The workflow also creates an unpackaged, self-contained installer named
`BitChord-Setup.exe`. This is a traditional EXE installer generated with Inno
Setup; it installs the published WinUI executable and creates Start Menu and
optional Desktop shortcuts. It is separate from the signed MSIX and does not
require MSIX certificate trust.

The Windows project is configured as a self-contained MSIX app. This packages
the Windows App SDK runtime with BitChord, so the installed app does not depend
on a separately registered Windows App Runtime version and does not need a
bootstrapper call before `Application.Start`.

## Build automatically with GitHub Actions

The `Windows companion` workflow runs on pushes, pull requests, and manual
dispatches. It restores, builds, and signs the MSIX bundle on `windows-2022`,
then compresses and uploads the package and public `.cer` file as
`BitChord-MSIX.zip` in a 14-day artifact. Open the workflow run in GitHub and
download `bitchord-windows-msix-<run-number>` from the **Artifacts** section.

Download `bitchord-windows-exe-<run-number>` for `BitChord-EXE.zip`, which
contains the traditional `BitChord-Setup.exe` installer.

A self-signed certificate is included in the package signature, but Windows
still requires that certificate's issuing certificate be trusted before
installing an MSIX from outside the Microsoft Store. Install the uploaded
`.cer` into **Trusted People** on test machines, or distribute through an
enterprise trust policy. A Store-signed package does not require this manual
trust step.
