## WURFL Microservice client for Kotlin - Changelog

### [1.0.1] - 2026-11-03

- Fixed a NullPointerException on lookups when the server omits the `error` field from the JSON response (WURFL Microservice 2.18.0 and later).
- Data models now have safe default values for fields missing from the server response.
- Fixed the server error message, which printed the whole device object instead of the error text.

### [1.0.0] - 2022-03-10

- First stable version. WmClient provides `lookup` methods using User-Agent string, header Maps, HttpServletRequest and Ktor's ApplicationRequest.
- Added enumeration functions like `getAllOSes`, `getAllVersionsForOS`, `getAllDeviceMakes`, `getAllDevicesForMake`
- Added methods to set up cache and handle capability/virtual capability usage 