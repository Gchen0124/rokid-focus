package com.chenniuniu.rokidfocus.glass

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface

object NetHosts {
    const val BEACON_PORT = 18791

    fun discoverPhone(timeoutMs: Int = 1600): String? {
        return runCatching {
            DatagramSocket(null).use { sock ->
                sock.reuseAddress = true
                sock.soTimeout = timeoutMs
                sock.bind(InetSocketAddress(BEACON_PORT))
                val buf = ByteArray(128)
                val pkt = DatagramPacket(buf, buf.size)
                sock.receive(pkt)
                val msg = String(pkt.data, 0, pkt.length).trim()
                // rokid-listen 192.168.1.10 8791
                val parts = msg.split(Regex("\\s+"))
                if (parts.size >= 2 && parts[0] == "rokid-listen") parts[1] else pkt.address.hostAddress
            }
        }.getOrNull()
    }

    fun gateways(context: Context): List<String> {
        val out = linkedSetOf<String>()
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val lp = cm.getLinkProperties(net)
            lp?.routes?.forEach { r ->
                if (r.isDefaultRoute) r.gateway?.hostAddress?.let { out.add(it) }
            }
        }
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val ni = ifaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address) {
                        val p = a.address
                        if (p.size == 4) {
                            out.add("${p[0].toInt() and 255}.${p[1].toInt() and 255}.${p[2].toInt() and 255}.1")
                        }
                    }
                }
            }
        }
        return out.toList()
    }

    fun withMulticast(context: Context, block: () -> Unit) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("rokid-listen")
        lock?.setReferenceCounted(false)
        runCatching { lock?.acquire() }
        try {
            block()
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }
}
