package com.aemerse.pager

import java.net.Socket

object SocketHandler {
    @JvmStatic
    @get:Synchronized
    @set:Synchronized
    var socket: Socket? = null
}