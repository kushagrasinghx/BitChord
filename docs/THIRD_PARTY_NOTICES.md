# Third-party notices

## TDLib Android Core

This application includes `io.github.tdlib-android:core:0.1.1`, a community
Android AAR packaging TDLib and its JNI bindings. The package metadata declares
the Boost Software License 1.0. See the package project and license text:

- Project: <https://github.com/AkashPriyadarshii/tdlib-android>
- Boost Software License 1.0: <https://www.boost.org/LICENSE_1_0.txt>
- TDLib: <https://github.com/tdlib/td>

The application calls TDLib directly. It does not use the package's optional
Kotlin convenience wrapper. TDLib's generated Java API and native library must
remain on the same TDLib version when this dependency is updated.

## Morphe Telegram patch reference

The GPL-3.0-or-later Morphe patch was inspected as a technical reference. No
Morphe source code has been copied into this implementation. If future work
adapts any of its implementation, retain its copyright notices and comply with
the applicable GPL-3.0-or-later terms.
