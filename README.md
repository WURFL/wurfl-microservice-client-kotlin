# wurfl-microservice-client-kotlin

## Requisites

- Gradle 7.2
- Java 11
- Kotlin 1.6.10

### Ktor client library - HTTP Client Engines
WURFL Microservice kotlin uses Ktor, a library that allows using different HTTP client implementations (aka "engines").
Initial implementation of WURFL Microservice client uses Apache engine but it would be nice to compare its performance
in device detection use case with other engines (CIO and Java 11 new HTTP client in particular).

