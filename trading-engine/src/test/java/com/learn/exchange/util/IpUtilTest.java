package com.learn.exchange.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class IpUtilTest {
    @Test
    void testIpUtil() {
        System.out.println(IpUtil.getIpAddress());
    }

    @Test
    void printHostIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                System.out.println("Interface: " + network.getName());
                System.out.println("Is Up: " + network.isUp());
                System.out.println("Is Loopback: " + network.isLoopback());
                System.out.println("Addresses: ");
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    System.out.println(" - " + address.getHostAddress());
                }
                System.out.println();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
