# Third-party dependencies

Runtime dependencies are AndroidX Core, Activity, Lifecycle and Jetpack Compose (including Material 3/icons), Kotlin, and kotlinx.coroutines. These projects use Apache License 2.0; the Android Gradle Plugin includes dependency metadata in the App Bundle. Review the resolved dependency report before changing dependencies.

Test/build-only dependencies include JUnit (EPL 1.0), Robolectric (MIT), AndroidX Test (Apache 2.0), Gradle and the Android build/Kotlin plugins. They are not bundled as app runtime code.

No Canon proprietary driver libraries, CUPS filters, CARPS/CAPT project code, Firebase, ad SDK, or analytics SDK are bundled. Driver references in DRIVER_RESEARCH.md were inspected for evidence, not copied.

Canon's official Linux archives are downloaded into external scratch storage by the build/test-only reference oracle. Their licences remain in those packages and they are not redistributed as Android dependencies. The Kotlin SLIM literal codec and local path-mapping harness are independently implemented. https://github.com/agalakhov/captdriver (GPL-3.0-or-later) was consulted as a HISCOA protocol reference; its source is not copied or linked into this app. Oracle reports and the generated print-data sample do not contain the Canon programs, libraries, ICC profiles or configuration tables.

Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
AndroidX: https://android.googlesource.com/platform/frameworks/support/
Kotlin: https://github.com/JetBrains/kotlin
Coroutines: https://github.com/Kotlin/kotlinx.coroutines
