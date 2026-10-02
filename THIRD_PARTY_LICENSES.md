# Third-Party Licenses & Attributions

This project references and complies with the following open-source specifications, libraries, and reference drivers:

---

## 1. Android Jetpack & Android Open Source Project (AOSP)
- **Components:** Android USB Host API, Jetpack Compose, AndroidX Core, AndroidX Lifecycle, Android Print Framework (`android.printservice.PrintService`).
- **License:** Apache License, Version 2.0
- **Website:** https://source.android.com

---

## 2. ITU-T Recommendation T.6 (CCITT Group 4 2D Compression)
- **Standard:** International Telecommunication Union (ITU-T) Standard T.6: "Facsimile coding schemes and coding control functions for Group 4 facsimile apparatus".
- **Implementation:** Clean-room pure Kotlin implementation written specifically for this project without copying third-party code.
- **Status:** Public international telecommunication standard.

---

## 3. Reference Driver Research Attributions
The following public open-source projects were consulted during protocol research:
- **`carps-cups`** by Ondrej Zary (GNU General Public License v3.0)
  https://github.com/ondrej-zary/carps-cups
- **`captdriver`** and **`studycapt`** by mounaiban / agalakhov (GNU General Public License v2.0)
  https://github.com/mounaiban/captdriver
  https://github.com/mounaiban/studycapt

*Note: No proprietary binaries or decompiled code from Canon Inc. are included in this project. All driver encoders are original clean-room implementations adhering to standard documented protocols.*
