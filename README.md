# SCMS Java version - source submission

This is a separate Java implementation that follows the existing SCMS use cases.
The original Flutter app and Python API remain preserved outside this package.
Adding these files does not change the technology of an existing installed APK.

## Source status

- The Vaadin website, Java business rules and JavaFX inventory client are included from the existing project.
- The Spring Boot/JDBC API and native Android Java/XML client are new source implementations.
- No builds, automated tests or runtime checks were performed on the new implementation, as requested.
- This source package is not a verified replacement release. No new Android APK is supplied.
- The native screens follow the same flows and brand colours, but are not a pixel-for-pixel Flutter conversion.
- The Android preview displays a student QR code and supports pasted QR identifiers; camera scanning is not implemented.
- Importing existing demo records and proving complete feature parity remain future work.

## Technology stack

| Component | Source technology |
|---|---|
| Browser website | Java with Vaadin Flow |
| Web framework and API | Spring Boot |
| Native Android client | Java, Android SDK, XML layout |
| Desktop inventory client | JavaFX |
| Business decisions | Java WorkflowRules and UsageRecencyPolicy |
| Persistence | Spring JDBC/JdbcTemplate and Xerial SQLite JDBC driver |
| Database engine | SQLite |
| Build | Maven for server/web/desktop; Gradle for Android |

XML and CSS are layout/style resources. SQLite is a database engine; Java accesses it through JDBC.

## Folder guide

- code/android_java/ - a separate native Android client, with its own application ID.
- code/java_backend/ - new Spring Boot API, JDBC persistence, authentication and workflow adapters.
- code/vaadin_dashboard/ - existing Java website source, including buttons, forms and tables.
- code/java-rules/ - existing shared Java booking checks and ranking algorithm.
- code/javafx_desktop/ - existing JavaFX desktop inventory client.
- scripts/ - launch scripts for the separate Java preview.
- SOURCE_MANIFEST.json - packaged source paths, byte counts and SHA-256 hashes.

## Files to open when explaining the project

1. Android screen layout: code/android_java/app/src/main/res/layout/activity_main.xml
2. Android screens and click handlers: code/android_java/app/src/main/java/edu/tiet/scms/mobile/MainActivity.java
3. Android API calls: code/android_java/app/src/main/java/edu/tiet/scms/mobile/ScmsApiClient.java
4. Website forms and tables: code/vaadin_dashboard/src/main/java/edu/tiet/scms/dashboard/views/
5. Website API connection: code/vaadin_dashboard/src/main/java/edu/tiet/scms/dashboard/service/ApiClient.java
6. Spring Boot entry point: code/java_backend/src/main/java/edu/tiet/scms/api/ScmsApiApplication.java
7. Login and registration: code/java_backend/src/main/java/edu/tiet/scms/api/AuthController.java
8. Password and token handling: code/java_backend/src/main/java/edu/tiet/scms/api/AuthService.java
9. Database queries: code/java_backend/src/main/java/edu/tiet/scms/api/JdbcStore.java
10. Database tables: code/java_backend/src/main/resources/schema.sql
11. Booking flow: code/java_backend/src/main/java/edu/tiet/scms/api/BookingService.java
12. Rule validation: code/java-rules/src/edu/tiet/scms/WorkflowRules.java
13. Ranking interface and implementation: AllocationPolicy.java and UsageRecencyPolicy.java in that same folder.
14. JavaFX window: code/javafx_desktop/src/main/java/edu/tiet/scms/desktop/ScmsDesktop.java

## Same use case, corresponding files

| Feature | Java Android client | Java API | Existing website |
|---|---|---|---|
| Sign in | MainActivity.login, ScmsApiClient.login | AuthController, AuthService | LoginView, BackendAuthenticationProvider |
| Equipment list | MainActivity.equipment | EquipmentController | InventoryView, RecordsView |
| Slot availability | MainActivity.availability | EquipmentController | SlotsView |
| Booking request | MainActivity.availability, ScmsApiClient.book | BookingController, BookingService | BookingsView for administration |
| Cancellation | MainActivity.bookings, ScmsApiClient.cancel | BookingService.cancel | BookingsView |
| Fair ranking | Backend operation | BookingService.allocate, UsageRecencyPolicy | SlotsView allocation, FairnessView |
| Issue and return | MainActivity.queue, returnForm | OperationsController | OperationsView |
| QR lookup | MainActivity.studentQr, validateQr | AuthController, OperationsController | QrView |
| Notifications | MainActivity.notifications | OperationsController | Original web dashboard has no student notification screen |
| Staff and configuration | Profile identifies staff role | AdminController | StaffView, FairnessView |
| Charts and metrics | Not part of Android preview | AdminController.analytics | DashboardView, AnalyticsView |

MainActivity methods call the same /api/auth, /api/equipment, /api/bookings,
 /api/transactions and /api/notifications contracts used by the original Dart remote sources.
This is a matching contract and domain design, not proof that the original APK was built from Java.

## Request flow in the Java version

Android Java screen or Vaadin Java view
 -> HTTP API request
 -> Spring Boot controller
 -> authentication and Java business rules
 -> JdbcStore prepared SQL statements
 -> SQLite database
 -> JSON response
 -> client updates the screen.

## Preparing a local demo later

These commands are supplied for later use; they were not executed for this submission.

Requirements: JDK 17, internet access for dependencies, Android SDK 36 for the mobile build.

1. On Windows, run RUN_JAVA_PREVIEW.bat. It builds the API and website with tests skipped,
   prompts for a demo password, and creates a separate .local/java-version/java-preview.db.
2. The preview uses API port 18001 and website port 18081. The original ports are left alone.
3. Demo accounts are admin@scms.local, staff@scms.local and student@scms.local.
   Their passwords are chosen interactively. Existing accounts are not reset by the seeder.
4. In Android Studio, open code/android_java and configure the Android SDK. Alternatively run
   code/android_java/build.ps1 after installing JDK 17 and Android SDK.
5. Debug APK output, after a successful build:
   code/android_java/app/build/outputs/apk/debug/app-debug.apk.
6. Install that separate preview APK to demonstrate the Java mobile source.
   Its application ID differs from the Flutter app, allowing both to stay installed.
7. In the Java mobile login screen, use http://LAPTOP_WIFI_IP:18001.
   An Android emulator can use http://10.0.2.2:18001.
8. Staff issue queues are selected by UTC slot date. Demo slots are seeded for the next day.
9. The JavaFX client can connect to the new API using:
   scripts/Start-JavaFX.ps1 -ApiUrl http://127.0.0.1:18001
10. Demo HTTP is permitted only in the Android debug variant. A release deployment needs HTTPS.
    Tokens are kept in client memory; restarting the API launcher creates a new signing key,
    so clients must sign in again.

Do not point SCMS_JAVA_DB at the original database to experiment with migration.
The launch script deliberately uses a separate preview database.

## GitHub upload

Extract the source ZIP and upload the folder contents so code/ and README.md appear at the repository root.
Do not upload only the ZIP if you want the Java files to be readable on GitHub.
Do not add local.properties, .env files, database files, passwords, build directories or private keys.

Suggested branches: main for the complete source snapshot, website for website development,
app for the native Android source, and javafx-desktop for desktop work.
Keep a complete source snapshot on each branch if the team needs independent builds.

## Short presentation wording

"This repository contains our Java version of SCMS: a Vaadin website, Spring Boot API,
JDBC database access, Java business rules, a native Android Java client and a JavaFX client.
The new mobile and API source has not yet been built or tested. Our original running mobile
demo is Flutter and is preserved separately."

## Framework references

- Spring JDBC: https://docs.spring.io/spring-boot/3.5/reference/data/sql.html
- Android build requirements: https://developer.android.com/build/releases/agp-9-0-0-release-notes
- SQLite JDBC: https://github.com/xerial/sqlite-jdbc
- Android network configuration: https://developer.android.com/privacy-and-security/security-config
