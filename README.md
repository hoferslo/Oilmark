# Oilmark

Android app (Kotlin, Jetpack Compose) that uses a [LubeLogger](https://lubelogger.com) server as its backend.

## Run it

1. Start the backend: `docker compose up -d` in the parent folder (LubeLogger on port 8080).
2. Open this folder in Android Studio and press Run.
3. On the connect screen enter the server address:
   - Android emulator: `http://10.0.2.2:8080` (that is how the emulator reaches your PC)
   - Real phone on the same Wi-Fi: `http://<your PC's IP>:8080`

## Where things are

```
app/src/main/java/com/storagecloud/oilmark/
  data/
    LubeLoggerApi.kt   every server call, one function each
    Models.kt          Vehicle, Record, ... as the server sends them
    RecordType.kt      the 11 kinds of records and the form fields each one has
    Units.kt           km/mi, L/gal and all number/date formatting
    Stats.kt           turns records into chart data (per month / year)
    FuelPrices.kt      average fuel price by country, from the internet (optional)
    SettingsStore.kt   saved server address, login, units
  ui/
    Navigation.kt      list of screens and how they connect
    common/            shared pieces: loading/error handling, form inputs, top bar
    connect/ garage/ vehicle/ records/ fuel/ stats/ settings/   one folder per screen
```

Fuel, service, repairs, reminders, notes and the rest all share **one** list screen and **one**
edit screen. To add a field to a form, add it to that type's list in `RecordType.kt` (and to
`Record` in `Models.kt` if it is new) - no UI code needed.

## Tests

`./gradlew testDebugUnitTest` runs `LubeLoggerApiTest` against the real server at
`http://localhost:8080` (change with `-Plubelogger.url=...`). It creates a throwaway vehicle, adds,
edits and deletes one record of every type, then removes the vehicle again.

## Good to know

- Charts (`ui/stats`, numbers from `data/Stats.kt`) open by tapping Odometer or Total spent.
- New fuel-ups use the quick slider screen (`ui/fuel`); editing one uses the regular form.
- Photos: `ui/common/Photos.kt` (camera + gallery, shrunk before upload) and
  `ui/records/Attachments.kt` (thumbnails, viewer, add/remove).
- Changing a vehicle photo uses the LubeLogger *website's* endpoints, because the REST API
  has none for it (see `LubeLoggerApi.setVehiclePhoto`). A LubeLogger update could break it.
- Starred vehicle, hidden record types, currency symbol and default fuel price are stored on
  the phone only (`SettingsStore`).
- Online fuel prices (`data/FuelPrices.kt`, off by default) come from OpenVan.camp's free API.
  It is fetched at app start in `ui/common/Session.kt`, at most once every 10 minutes, and only
  the country code is sent. The data is CC BY 4.0: keep the "Data: OpenVan.camp" credit visible
  wherever the price is shown.

- Reminder notifications: `notify/ReminderNotifications.kt`, a WorkManager job every 6 hours
  that also refreshes the home-screen widget (`widget/OilmarkWidget.kt`, Jetpack Glance).
- The widget and notifications open screens through `Launch.kt` (extras on MainActivity's intent).
- Fuel-up station name and location are saved as LubeLogger "extra fields" named `Station` and
  `Location` ("lat,lon"). LubeLogger keeps them without any setup; add the same names under
  Settings -> Extra Fields on the website to see them there too.
- Comparisons (`ui/stats/CompareScreen.kt`) load each record type once for all vehicles via the
  `/all` endpoints; the numbers come from `Stats.totals`.

## Not in the app yet

- Editing LubeLogger "extra fields" (existing values are kept)
- Marking a plan as done (the LubeLogger API does not allow it; do it on the website)
