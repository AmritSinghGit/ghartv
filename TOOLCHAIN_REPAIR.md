# GharTV Nova 0.4.1 toolchain repair

The 0.4.0 build combined Media3 1.10.1 (which requires compileSdk 36) with compileSdk 35 and Android Gradle Plugin 8.7.3. Version 0.4.1 aligns the build as follows:

- Android Gradle Plugin: 8.10.1
- Gradle: 8.11.1
- JDK: 17
- compileSdk: 36
- targetSdk: 35
- Media3: 1.10.1

The application ID is now `in.ghartv.nova`. The previous source-only 0.4.0 package had not produced a successful APK, so this establishes the stable Nova package identity before physical-TV distribution.


Version 0.4.2 also replaces three `JSONObject.keySet()` calls with Android's supported `JSONObject.keys()` iterator. The previous Java parser-only validation could not detect this Android API mismatch; the validator now checks it explicitly.
