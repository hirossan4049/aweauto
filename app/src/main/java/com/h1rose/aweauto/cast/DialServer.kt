package com.h1rose.aweauto.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.random.Random

private const val TAG = "AweDial"
private const val SSDP_ADDR = "239.255.255.250"
private const val SSDP_PORT = 1900
private const val DIAL_ST = "urn:dial-multiscreen-org:service:dial:1"

/**
 * DIAL (SSDP + HTTP) サーバー。同じ Wi-Fi / テザリングにいるスマホの YouTube アプリが
 * キャストボタンに aweauto を自動で出し、タップすると pairingCode を送ってくる。
 * それを Lounge に登録すれば、テレビコードの入力なしでつながる。
 * 参考: aykevl/plaincast (server/ssdp.go, server/http.go), DIAL 2.1 仕様
 */
object DialServer {
    @Volatile
    private var running = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private var httpPort = 0
    private lateinit var deviceUuid: String

    fun start(context: Context) {
        if (running) return
        running = true
        val sp = context.getSharedPreferences("lounge", Context.MODE_PRIVATE)
        deviceUuid = sp.getString("dial_uuid", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("dial_uuid", it).apply()
        }
        // Wi-Fi は既定でマルチキャストを捨てるので、受け取れるようにロックを取る
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        multicastLock = wifi?.createMulticastLock("aweauto-dial")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val server = ServerSocket(0)
        httpPort = server.localPort
        thread(name = "dial-http", isDaemon = true) { serveHttp(server) }
        thread(name = "dial-ssdp", isDaemon = true) { serveSsdp() }
        Log.i(TAG, "DIAL server on port $httpPort")
    }

    fun stop() {
        running = false
        multicastLock?.release()
        multicastLock = null
    }

    // ------------------------------------------------------------------
    // SSDP: M-SEARCH に応答する
    // ------------------------------------------------------------------

    private fun serveSsdp() {
        val group = InetAddress.getByName(SSDP_ADDR)
        while (running) {
            runCatching {
                MulticastSocket(null).use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(SSDP_PORT))
                    // テザリングなど後から増えたネットワークにも参加するため、定期的に入り直す
                    val joined = joinAll(socket, group)
                    Log.i(TAG, "SSDP listening on ${joined.joinToString { it.name }}")
                    socket.soTimeout = 1_000
                    val buf = ByteArray(1500)
                    val until = System.currentTimeMillis() + 30_000
                    while (running && System.currentTimeMillis() < until) {
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(packet)
                        } catch (e: SocketTimeoutException) {
                            continue
                        }
                        val text = String(packet.data, 0, packet.length)
                        if (!text.startsWith("M-SEARCH")) continue
                        val headers = parseHeaders(text)
                        val st = headers["st"].orEmpty()
                        if (!st.startsWith("urn:dial-multiscreen-org:service:dial:") && st != "ssdp:all") continue
                        val mx = headers["mx"]?.toIntOrNull()?.coerceIn(1, 3) ?: 1
                        val target = InetSocketAddress(packet.address, packet.port)
                        thread(isDaemon = true) { respond(target, mx) }
                    }
                }
            }.onFailure {
                Log.w(TAG, "SSDP error", it)
                Thread.sleep(5_000)
            }
        }
    }

    private fun joinAll(socket: MulticastSocket, group: InetAddress): List<NetworkInterface> =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() && it.inetAddresses.toList().any { a -> a is Inet4Address } }
            .filter { runCatching { socket.joinGroup(InetSocketAddress(group, SSDP_PORT), it) }.isSuccess }

    private fun respond(target: InetSocketAddress, mx: Int) {
        Thread.sleep(Random.nextLong(mx * 1000L))
        DatagramSocket().use { socket ->
            // 相手に届く経路の自分側アドレスを LOCATION に載せる
            socket.connect(target)
            val local = socket.localAddress.hostAddress
            val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("GMT") }.format(Date())
            val body = "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "DATE: $date\r\n" +
                "EXT: \r\n" +
                "LOCATION: http://$local:$httpPort/dd.xml\r\n" +
                "SERVER: Android/9 UPnP/1.1 aweauto/1.0\r\n" +
                "ST: $DIAL_ST\r\n" +
                "USN: uuid:$deviceUuid::$DIAL_ST\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "CONFIGID.UPNP.ORG: 1\r\n" +
                "\r\n"
            val bytes = body.toByteArray()
            socket.send(DatagramPacket(bytes, bytes.size))
        }
    }

    // ------------------------------------------------------------------
    // HTTP: 機器の説明と YouTube アプリの起動
    // ------------------------------------------------------------------

    private fun serveHttp(server: ServerSocket) {
        while (running) {
            val client = runCatching { server.accept() }.getOrNull() ?: continue
            thread(isDaemon = true) { client.use { handle(it) } }
        }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 10_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val (method, path) = requestLine.split(' ').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "/" } }
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) String(readBytes(input, length)) else ""
        val host = socket.localAddress.hostAddress
        val appUrl = "http://$host:$httpPort/apps/"
        Log.i(TAG, "$method $path")

        val (status, extraHeaders, content) = when {
            path == "/dd.xml" -> Triple("200 OK", mapOf("Application-URL" to appUrl), deviceDescription())
            path == "/apps/YouTube" && method == "GET" -> Triple("200 OK", emptyMap(), appDescription())
            path == "/apps/YouTube" && method == "POST" -> {
                val params = body.split('&').mapNotNull {
                    val kv = it.split('=', limit = 2)
                    if (kv.size == 2) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
                }.toMap()
                params["pairingCode"]?.let { LoungeReceiver.registerPairingCode(it) }
                Triple("201 Created", mapOf("Location" to "${appUrl}YouTube/run"), "")
            }
            path == "/apps/YouTube/run" && method == "DELETE" -> Triple("200 OK", emptyMap(), "")
            else -> Triple("404 Not Found", emptyMap(), "")
        }
        val bytes = content.toByteArray()
        val out = buildString {
            append("HTTP/1.1 $status\r\n")
            if (bytes.isNotEmpty()) append("Content-Type: text/xml; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply {
            write(out.toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun deviceDescription() = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:dial-multiscreen-org:device:dial:1</deviceType>
            <friendlyName>${xml(LoungeReceiver.screenName)}</friendlyName>
            <manufacturer>aweauto</manufacturer>
            <modelName>aweauto</modelName>
            <UDN>uuid:$deviceUuid</UDN>
            <serviceList>
              <service>
                <serviceType>urn:dial-multiscreen-org:service:dial:1</serviceType>
                <serviceId>urn:dial-multiscreen-org:serviceId:dial</serviceId>
                <SCPDURL>/upnp/notfound</SCPDURL>
                <controlURL>/upnp/notfound</controlURL>
                <eventSubURL></eventSubURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private fun appDescription(): String {
        val state = if (LoungeReceiver.status.value.online) "running" else "stopped"
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" dialVer="2.1">
              <name>YouTube</name>
              <options allowStop="true"/>
              <state>$state</state>
              ${if (state == "running") "<link rel=\"run\" href=\"run\"/>" else ""}
              <additionalData>
                <screenId>${xml(LoungeReceiver.currentScreenId)}</screenId>
                <theme>cl</theme>
              </additionalData>
            </service>
        """.trimIndent()
    }

    private fun parseHeaders(text: String): Map<String, String> = text.split("\r\n").drop(1).mapNotNull {
        val i = it.indexOf(':')
        if (i > 0) it.substring(0, i).trim().lowercase() to it.substring(i + 1).trim() else null
    }.toMap()

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    // InputStream.readNBytes は Android 13 からなので自前で読む
    private fun readBytes(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = input.read(buf, off, length - off)
            if (n < 0) break
            off += n
        }
        return buf.copyOf(off)
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
