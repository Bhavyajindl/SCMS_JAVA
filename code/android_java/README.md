# SCMS native Android Java source preview

Java 17 source, XML layout, Android SDK, Gradle, ZXing QR generation.
This is a separate implementation, not the source of the original Flutter APK.

MainActivity creates student and staff screens and calls ScmsApiClient.
It uses existing SCMS API routes and can be configured for either API server.
ScmsSession keeps the login token in memory. The application ID is separate from Flutter.

No build, installation or tests have been performed on this new client.
Camera scanning, exact visual parity, and complete feature parity are not implemented/verified.
QR display and manual identifier validation are included in source.

Open this folder in Android Studio with JDK 17 and Android SDK 36.
Run build.ps1 for a future debug build; no built APK is included in this submission.
