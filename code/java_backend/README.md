# SCMS Java API source preview

Spring Boot 3.5, Java 17, Spring JDBC and SQLite.
This module is a new, unbuilt and untested implementation. The original FastAPI API is unchanged.
It reuses the Java rules from ../java-rules/src through the Maven build-helper plugin.

Source covers authentication, equipment, slots, bookings, fair allocation, issue/return,
QR lookup, notifications, staff management, defaulters and analytics.
Scheduled jobs detect overdue equipment and allocate slots at the cutoff.

Use the separate scripts/Start-JavaVersion.ps1 launcher from the repository root.
It selects a separate database and ports. Do not use the original demo database.
The full source submission README explains scope and remaining validation.
