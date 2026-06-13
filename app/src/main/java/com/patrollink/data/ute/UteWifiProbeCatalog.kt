package com.patrollink.data.ute

internal object UteWifiProbeCatalog {
    val DefaultHosts = listOf(
        "192.168.4.1",
        "192.168.8.1",
        "192.168.10.1",
        "192.168.42.1",
        "192.168.43.1",
        "192.168.49.1",
        "192.168.100.1",
        "192.168.1.1",
        "192.168.0.1"
    )

    val Ports = listOf(
        80,
        8000,
        8080,
        8088,
        8888,
        5000,
        2121,
        21
    )

    val Paths = listOf(
        "/",
        "/files",
        "/api/files",
        "/api/file/list",
        "/api/filelist",
        "/filelist",
        "/list",
        "/media",
        "/media/list",
        "/api/media",
        "/api/media/list",
        "/cgi-bin/files",
        "/cgi-bin/media",
        "/storage",
        "/sdcard",
        "/mnt/sdcard",
        "/DCIM",
        "/dcim",
        "/DCIM/",
        "/DCIM/100MEDIA",
        "/DCIM/Camera",
        "/mnt/sdcard/DCIM",
        "/mnt/sdcard/DCIM/100MEDIA",
        "/sdcard/DCIM",
        "/sdcard/DCIM/100MEDIA",
        "/photo",
        "/photo/",
        "/video",
        "/video/",
        "/audio",
        "/audio/",
        "/record",
        "/record/"
    )
}
