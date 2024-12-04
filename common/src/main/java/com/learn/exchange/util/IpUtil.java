package com.learn.exchange.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class IpUtil {
    static final Logger logger = LoggerFactory.getLogger(IpUtil.class);

    static final String IP;

    static final String HOST_ID;

    static {
        IP = doGetIpAddress();
        HOST_ID = doGetHostId();
        logger.info("get ip address: {}, host id: {}", IP, HOST_ID);
    }

    public static String getIpAddress() { return IP;}
    public static String getHostId() { return HOST_ID;}

    public static String doGetHostId() {
        String id = getIpAddress();
        return id.replace(".", "_").replace("-", "_");
    }

    public static String doGetIpAddress() {
        List<InetAddress> ipList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if(network.isLoopback() || !network.isUp())
                    continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while(addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if(!addr.isLoopbackAddress() && (addr instanceof Inet4Address)) {
                        ipList.add(addr);
                    }
                }
            }
        }catch (SocketException e) {
            logger.warn("Get IP address by NetworkInterface.getNetworkInterfaces() failed: {}", e.getMessage());
        }
        ipList.forEach(ip -> logger.debug("Found ip address: {}", ip));
        if(ipList.isEmpty())
            return "127.0.0.1";
        return ipList.get(0).getHostAddress();
    }
}
