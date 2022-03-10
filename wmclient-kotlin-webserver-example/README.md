# WURFL Microservice client - KTOR webserver example

A sample application that shows how to use the WURFL Microservice client kotlin into a simple Ktor webserver.

The application runs on port 18080 and exposes two endpoints:

- `/` the root endpoint uses the HTTP request headers to detect the caller device and print its brand, model, os name, browser name
and form factor.
- `/info` prints some information about WURFL Microservice server and client API.

Both endpoints return HTML content.

In order to make your WM client access your WURFL Microservice server instance, be it a docker image or an AWS, Azure or GCP virtual machine,
you need to change this line in cless `Main.kt`:

```kotlin
val wmClient = WmClient.create("http", "localhost", "8080", "")
```

using the proper configuration for host and port values 
