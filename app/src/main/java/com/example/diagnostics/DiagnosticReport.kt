package com.example.diagnostics

import android.os.Build
import com.example.BuildConfig
import com.example.usb.UsbDeviceInfo
import com.example.usb.UsbTraceLogger
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticReport {

    fun generateJson(
        activeDevice: UsbDeviceInfo?,
        allDevices: List<UsbDeviceInfo>
    ): String {
        val root = JSONObject()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        root.put("reportTimestamp", sdf.format(Date()))
        root.put("appName", "LBP OTG Print")
        root.put("appVersion", BuildConfig.VERSION_NAME)
        root.put("appVersionCode", BuildConfig.VERSION_CODE)

        // Environment
        val env = JSONObject()
        env.put("androidVersion", Build.VERSION.RELEASE)
        env.put("sdkInt", Build.VERSION.SDK_INT)
        env.put("deviceManufacturer", Build.MANUFACTURER)
        env.put("deviceModel", Build.MODEL)
        env.put("deviceProduct", Build.PRODUCT)
        root.put("environment", env)

        // Active Device
        if (activeDevice != null) {
            val devJson = deviceToJson(activeDevice)
            root.put("activeDevice", devJson)
        } else {
            root.put("activeDevice", JSONObject.NULL)
        }

        // All Connected USB Devices
        val devicesArray = JSONArray()
        for (dev in allDevices) {
            devicesArray.put(deviceToJson(dev))
        }
        root.put("allConnectedDevices", devicesArray)

        // Trace Logs
        val logsArray = JSONArray()
        for (log in UsbTraceLogger.getLogs().takeLast(100)) {
            val logObj = JSONObject()
            logObj.put("time", sdf.format(Date(log.timestamp)))
            logObj.put("tag", log.tag)
            logObj.put("message", log.message)
            logObj.put("isError", log.isError)
            if (log.hexDump != null) {
                logObj.put("hexDump", log.hexDump)
            }
            logsArray.put(logObj)
        }
        root.put("recentTraceEvents", logsArray)

        return root.toString(2)
    }

    fun generatePlainText(
        activeDevice: UsbDeviceInfo?,
        allDevices: List<UsbDeviceInfo>
    ): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sb.append("====================================================\n")
        sb.append("         LBP OTG PRINT — USB DIAGNOSTIC REPORT      \n")
        sb.append("====================================================\n")
        sb.append("Timestamp: ${sdf.format(Date())}\n")
        sb.append("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n\n")

        sb.append("----------------------------------------------------\n")
        sb.append("TARGET CANON PRINTER STATUS\n")
        sb.append("----------------------------------------------------\n")
        if (activeDevice != null) {
            sb.append("Name: ${activeDevice.productName ?: "Unknown"}\n")
            sb.append("Manufacturer: ${activeDevice.manufacturerName ?: "Unknown"}\n")
            sb.append("VID: ${activeDevice.vendorIdHex} (${activeDevice.vendorId})\n")
            sb.append("PID: ${activeDevice.productIdHex} (${activeDevice.productId})\n")
            sb.append("Serial: ${activeDevice.serialNumber ?: "Unknown"}\n")
            sb.append("Device Class: ${activeDevice.deviceClass} | Subclass: ${activeDevice.deviceSubclass} | Protocol: ${activeDevice.deviceProtocol}\n")
            sb.append("Permission Granted: ${if (activeDevice.permissionGranted) "YES" else "NO"}\n")
            sb.append("Interface Claimed: ${if (activeDevice.interfaceClaimed) "YES" else "NO"}\n\n")

            if (activeDevice.ieee1284 != null) {
                sb.append("IEEE-1284 Device ID:\n")
                sb.append("  Raw: ${activeDevice.ieee1284.rawString}\n")
                sb.append("  MFG: ${activeDevice.ieee1284.manufacturer}\n")
                sb.append("  MDL: ${activeDevice.ieee1284.model}\n")
                sb.append("  CMD: ${activeDevice.ieee1284.commandSet.joinToString(", ")}\n")
                sb.append("  CID: ${activeDevice.ieee1284.compatibilityId}\n")
                if (activeDevice.ieee1284.identifiesCanonUfriiLt) {
                    sb.append("  Backend: Canon UFRII LT / NCAP + CPCA over USB MLP flow control. CPCA/NCAP acceptance and hardware output require verification.\n")
                }
                sb.append("  DES: ${activeDevice.ieee1284.description}\n\n")
            }

            if (activeDevice.portStatus != null) {
                sb.append("Port Status: ${activeDevice.portStatus.toDisplayString()} (Byte: 0x${String.format("%02X", activeDevice.portStatus.rawByte)})\n\n")
            }

            sb.append("Interfaces & Endpoints (${activeDevice.interfaces.size}):\n")
            for (uif in activeDevice.interfaces) {
                sb.append("  Interface #${uif.id} (Class: ${uif.interfaceClass}, Subclass: ${uif.interfaceSubclass}, Protocol: ${uif.interfaceProtocol})\n")
                for (ep in uif.endpoints) {
                    sb.append("    • Endpoint 0x${String.format("%02X", ep.address)}: ${ep.direction} ${ep.type} (maxPacketSize: ${ep.maxPacketSize})\n")
                }
            }
        } else {
            sb.append("NO ACTIVE USB PRINTER CONNECTED\n")
        }

        sb.append("\n----------------------------------------------------\n")
        sb.append("ALL CONNECTED USB DEVICES (${allDevices.size})\n")
        sb.append("----------------------------------------------------\n")
        for (dev in allDevices) {
            sb.append("• ${dev.deviceName}: ${dev.manufacturerName ?: ""} ${dev.productName ?: ""} (VID: ${dev.vendorIdHex}, PID: ${dev.productIdHex})\n")
        }

        sb.append("\n----------------------------------------------------\n")
        sb.append("RECENT USB TRACE LOGS\n")
        sb.append("----------------------------------------------------\n")
        for (event in UsbTraceLogger.getLogs().takeLast(100)) {
            sb.append(event.toFormattedString()).append("\n")
        }

        return sb.toString()
    }

    private fun deviceToJson(dev: UsbDeviceInfo): JSONObject {
        val obj = JSONObject()
        obj.put("deviceName", dev.deviceName)
        obj.put("vendorIdHex", dev.vendorIdHex)
        obj.put("productIdHex", dev.productIdHex)
        obj.put("manufacturerName", dev.manufacturerName ?: JSONObject.NULL)
        obj.put("productName", dev.productName ?: JSONObject.NULL)
        obj.put("serialNumber", dev.serialNumber ?: JSONObject.NULL)
        obj.put("deviceClass", dev.deviceClass)
        obj.put("deviceSubclass", dev.deviceSubclass)
        obj.put("deviceProtocol", dev.deviceProtocol)
        obj.put("permissionGranted", dev.permissionGranted)
        obj.put("interfaceClaimed", dev.interfaceClaimed)
        obj.put("isCanonFamily", dev.isCanonFamily)
        obj.put("isLbp6030Family", dev.isLbp6030Family)

        if (dev.ieee1284 != null) {
            val ieee = JSONObject()
            ieee.put("raw", dev.ieee1284.rawString)
            ieee.put("manufacturer", dev.ieee1284.manufacturer)
            ieee.put("model", dev.ieee1284.model)
            ieee.put("compatibilityId", dev.ieee1284.compatibilityId)
            ieee.put("identifiesCanonUfriiLt", dev.ieee1284.identifiesCanonUfriiLt)
            ieee.put("advertisesCpca", dev.ieee1284.advertisesCpca)
            ieee.put("directUsbDriverAvailable", dev.ieee1284.supportsPcl5 || com.example.driver.DriverRegistry.supportsCanon(dev))
            val cmds = JSONArray()
            dev.ieee1284.commandSet.forEach { cmds.put(it) }
            ieee.put("commandSet", cmds)
            obj.put("ieee1284", ieee)
        }

        if (dev.portStatus != null) {
            val ps = JSONObject()
            ps.put("rawHex", String.format("0x%02X", dev.portStatus.rawByte))
            ps.put("paperEmpty", dev.portStatus.paperEmpty)
            ps.put("selected", dev.portStatus.selected)
            ps.put("notError", dev.portStatus.notError)
            obj.put("portStatus", ps)
        }

        val ifs = JSONArray()
        for (uif in dev.interfaces) {
            val ifObj = JSONObject()
            ifObj.put("id", uif.id)
            ifObj.put("class", uif.interfaceClass)
            ifObj.put("subclass", uif.interfaceSubclass)
            ifObj.put("protocol", uif.interfaceProtocol)
            val eps = JSONArray()
            for (ep in uif.endpoints) {
                val epObj = JSONObject()
                epObj.put("addressHex", String.format("0x%02X", ep.address))
                epObj.put("direction", ep.direction)
                epObj.put("type", ep.type)
                epObj.put("maxPacketSize", ep.maxPacketSize)
                eps.put(epObj)
            }
            ifObj.put("endpoints", eps)
            ifs.put(ifObj)
        }
        obj.put("interfaces", ifs)
        return obj
    }
}
