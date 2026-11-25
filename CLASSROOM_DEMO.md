# Classroom Java demo

The website branch has a real Vaadin UI, Spring Boot API, existing Java rules, and JDBC/SQLite persistence.

## Present the scenario

1. Sign in with the separately provided classroom administrator credentials.
2. Open /admin/classroom or choose Classroom demo in the sidebar.
3. Keep Student A at 0 recent sessions and Student B at 4.
4. Click Create two booking requests. Both records start as REQUESTED.
5. Click Run Java allocation.
6. Show score 0.00 for A and 1.00 for B, with one available place.
7. Explain that the lower score gets priority; A is CONFIRMED and B is WAITLISTED.
8. Open Inventory, Slots and Bookings to show the saved records.

The history is synthetic classroom data, recorded four days ago so the default recency penalty is zero.
Equal scores are sorted by request time.
Create a fresh scenario to repeat the demonstration or change the usage values.

## Hosting

The temporary HTTPS URL proxies to the actual Java website on the laptop.
The laptop, Java processes and Cloudflare tunnel must remain running.
The URL changes after restarting the quick tunnel.
Use the local URL http://127.0.0.1:18181 if Internet access is unavailable.
No permanent cloud hosting has been configured.

## Server configuration

The classroom endpoint is disabled by default.
Enable it only on the isolated classroom database with SCMS_CLASSROOM_ENABLED=true.
It creates synthetic students, returned-use history, a racket and a one-place slot.
It resets the demonstration fairness settings to daily cap 2, recency threshold 2 days and weight 0.5.
The original Flutter/Python database is not used.

API_PORT=18101 and SCMS_WEB_PORT=18181 were used for the isolated local deployment.
The website uses SCMS_API_URL=http://127.0.0.1:18101/api.
HTTPS reverse-proxy mode uses SCMS_HTTPS=true and SERVER_FORWARD_HEADERS_STRATEGY=framework.

## Verification limit

The Java API and website compiled and started. HTTPS health and sign-in pages returned 200.
Automated tests and complete workflow/browser verification were not run, in line with the earlier request.
The Android Java app has not been built. This demo is a Java website/API demonstration.
