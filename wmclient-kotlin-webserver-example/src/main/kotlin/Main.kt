import com.scientiamobile.wurfl.wmclient.kotlin.WmClient
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.html.*

fun main() {
    println("WURFL microservice web server sample kotlin started!")

    val wmClient = WmClient.create("http", "localhost", "8080", "")

    embeddedServer(Netty, port = 18080) {
        routing {
            get("/") {
                val device = wmClient.lookupRequest(call.request)
                val pageTitle = "Welcome to WURFL Microservice! "
                val head = pageTitle + "You are using a ${device.capabilities["brand_name"]} ${device.capabilities["model_name"]}"
                call.respondHtml(HttpStatusCode.OK) {
                    head {
                        title {
                            + pageTitle
                        }
                    }
                    body {
                        h1 {
                            + head
                        }
                        p {
                            b {
                                + "Your device OS is: "
                            }
                            + device.capabilities["advertised_device_os"]!!
                        }

                        p {
                            b {
                                + "Your browser is: "
                            }
                            val bv = device.capabilities["advertised_browser"]!! + " " + device.capabilities["advertised_browser_version"]!!
                            + bv
                        }
                        p {
                            b {
                                + "This device form factor is: "
                            }
                            + device.capabilities["form_factor"]!!
                        }
                    }
                }
            }
            get("/info") {
                val info = wmClient.getInfo()
                val pageTitle = "WURFL Microservice Information"
                call.respondHtml(HttpStatusCode.OK) {
                    head {
                        title {
                            + pageTitle
                        }
                    }
                    body {
                        h1 {
                            + pageTitle
                        }
                        p {
                            b {
                                + "WURFL API version:"
                            }
                            + info.wurflApiVersion
                        }
                        p {
                            b {
                                + "WM server API version:"
                            }
                            + info.wmVersion
                        }
                        p {
                            b {
                                + "Supported static capabilities:"
                            }
                            + info.staticCaps.joinToString()
                        }
                        p {
                            b {
                                + "Supported virtual capabilities:"
                            }
                            + info.virtualCaps.joinToString()
                        }
                    }
                }
            }
        }
    }.start(wait = true)

}