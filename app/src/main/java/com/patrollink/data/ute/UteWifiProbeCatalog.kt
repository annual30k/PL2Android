package com.patrollink.data.ute

internal object UteWifiProbeCatalog {
    val DeviceApFallbackHosts = listOf(
        "192.168.4.1",
        "192.168.8.1",
        "192.168.10.1",
        "192.168.42.1",
        "192.168.43.1",
        "192.168.49.1",
        "192.168.100.1",
        "192.168.222.1",
        "192.168.222.254"
    )

    val LanGatewayFallbackHosts = listOf(
        "192.168.1.1",
        "192.168.0.1"
    )

    val DefaultHosts = DeviceApFallbackHosts + LanGatewayFallbackHosts

    val Ports = listOf(
        8000,
        80,
        8080,
        8088,
        8888,
        5000,
        2121,
        21
    )

    val Paths = listOf(
        "/media",
        "/media/list",
        "/photo",
        "/photo/",
        "/video",
        "/video/",
        "/DCIM",
        "/DCIM/",
        "/DCIM/100MEDIA",
        "/",
        "/files",
        "/api/files",
        "/api/file/list",
        "/api/filelist",
        "/filelist",
        "/list",
        "/api/media",
        "/api/media/list",
        "/cgi-bin/files",
        "/cgi-bin/media",
        "/storage",
        "/sdcard",
        "/mnt/sdcard",
        "/dcim",
        "/DCIM/Camera",
        "/mnt/sdcard/DCIM",
        "/mnt/sdcard/DCIM/100MEDIA",
        "/sdcard/DCIM",
        "/sdcard/DCIM/100MEDIA",
        "/audio",
        "/audio/",
        "/record",
        "/record/"
    )
}
