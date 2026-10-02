package com.proxy.wireopen.model;

/**
 * State of the VPN connection.
 */
public enum ConnectionState {
    DISCONNECTED("未连接"),
    CONNECTING("正在连接..."),
    CONNECTED("已连接"),
    DISCONNECTING("正在断开..."),
    ERROR("连接错误");

    private final String description;

    ConnectionState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
