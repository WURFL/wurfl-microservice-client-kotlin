import com.scientiamobile.wurfl.wmclient.kotlin.JSONModelMktName
import com.scientiamobile.wurfl.wmclient.kotlin.WmClient
import com.scientiamobile.wurfl.wmclient.kotlin.WmException


fun main() {

    try {
        // First we need to create a WM client instance, to connect to our WM server API at the specified host and port.
        val client = WmClient.create("http", "localhost", "8080", "")
        // We ask Wm server API for some Wm server info such as server API version and info about WURFL API and file used by WM server.
        val info = client.getInfo()
        println("Printing WM server information")
        println("WURFL API version: " + info.wurflApiVersion)
        println("WM server version:  " + info.wmVersion)
        println("Wurfl file info: " + info.wurflInfo)

        val ua = "Mozilla/5.0 (Linux; Android 7.1.1; ONEPLUS A5000 Build/NMF26X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Mobile Safari/537.36"

        // By setting the cache size we are also activating the caching option in WM client. In order to not use cache, you just to need to omit setCacheSize call
        client.setCacheSize(100000)

        // set the capabilities we want to receive from WM server
        client.setRequestedStaticCapabilities(arrayOf("brand_name", "model_name"))
        client.setRequestedVirtualCapabilities( arrayOf("is_smartphone", "form_factor"))

        println()
        println("Detecting device for user-agent: $ua")

        // Perform a device detection calling WM server API, using only the user agent
        // val device = client.lookupUseragent(ua)

        // Perform a device detection calling WM server API, using a full HTTP request header map (there's also a lookupRequest(req: ApplicationRequest)
        // and a lookupRequest(req: HttpServletRequest) that you can use when running a WM client inside a web application on an application server like tomcat/glassfish/jboss, etc.
        // or a web framework using an embedded server like Ktor
        val headers = HashMap<String, String>()
        headers["Accept-Encoding"] = "gzip, deflate"
        headers["Accept"] = "text/html, application/xml;q=0.9, application/xhtml+xml, image/png, image/webp, image/jpeg, image/gif, image/x-xbitmap, */*;q=0.1"
        headers["Accept-Language"] = "en"
        headers["Device-Stock-Ua"] = ua
        headers["Forwarded"] = "for=\"110.54.224.195:36350\""
        headers["Save-Data"] = "on"
        headers["Referer"] = "https://www.cram.com/flashcards/labor-and-delivery-questions-889210"
        headers["User-Agent"] = "Opera/9.80 (Android; Opera Mini/51.0.2254/184.121; U; en) Presto/2.12.423 Version/12.16"
        headers["X-Clacks-Overhead"] = "GNU ph"
        headers["X-Forwarded-For"] = "110.54.224.195, 82.145.210.235"
        headers["X-Operamini-Features"] = "advanced, camera, download, file_system, folding, httpping, pingback, routing, touch, viewport"
        headers["X-Operamini-Phone"] = "Android #"
        val device = client.lookupHeaders(headers)

        // Applicative error, ie: invalid input provided
        if (device.error.isNotEmpty()) {
            println("An error occurred:  $device.error")
        } else {
            // Let's get the device capabilities and print some of them
            val capabilities = device.capabilities
            println("Detected device WURFL ID: $capabilities[\"wurfl_id\"]")
            println("Device brand & model: $capabilities[\"brand_name\"] $capabilities[\"model_name\"]")
            println("Detected device form factor: $capabilities[\"form_factor\"]")
            if (capabilities["is_smartphone"].equals("true")) {
                println("This is a smartphone")
            }

            // Iterate over all the device capabilities and print them
            println("All received capabilities")
            capabilities.keys.forEach{ println("$it :  $capabilities[it]") }
        }

        // Get all the device manufacturers, and print the first twenty
        val limit = 20
        val deviceMakes = client.getAllDeviceMakes()
        println("------------ Print the first $limit Brand of ${deviceMakes.size} retrieved from server ------------")

        // Sort the device manufacturer names
        deviceMakes.sort()
        val firstItems = deviceMakes.copyOfRange(0, limit + 1)
        firstItems.forEach { println(it) }

        // Now call the WM server to get all device model and marketing names produced by Apple
        println("------------ Print all Model for the Apple Brand ------------")
        val devNames = client.getAllDevicesForMake("Apple")

        // Sort ModelMktName objects by their model name
        devNames.sortWith(ByModelNameComparer())

        for (modelMktName in devNames) {
            println(" - ${modelMktName.modelName} ${modelMktName.marketingName}")
        }

        // Now call the WM server to get all operative system names
        println("------------ Print the list of OSes ------------")
        val oses = client.getAllOSes()
        // Sort and print all OS names
        oses.sort()
        oses.forEach { println(" - $it")  }

        // Let's call the WM server to get all version of the Android OS
        println("------------ Print all versions for the Android OS ------------")
        val osVersions = client.getAllVersionsForOS("Android")
        // Sort all Android version numbers and print them.
        osVersions.sort()
        osVersions.forEach { println(" - $it") }

        // Cleans all client resources. Any call on client API methods after this one will throw a WmException
        client.destroy()
    } catch (e: WmException) {
        // problems such as network errors  or internal server problems
        println("An error has occurred: " + e.message)
        e.printStackTrace()
    }
    println("------------ End of WM Java client example ------------")
}

// Comparator used to sort JSONModelMktName objects according to their model name property, for which is used the String natural ordering.
internal class ByModelNameComparer : Comparator<JSONModelMktName> {
    override fun compare(o1: JSONModelMktName, o2: JSONModelMktName): Int {
        return o1.modelName.compareTo(o2.modelName)
    }
}