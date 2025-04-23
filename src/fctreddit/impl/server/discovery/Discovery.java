package fctreddit.impl.server.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class Discovery {
    private static Logger Log = Logger.getLogger(Discovery.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }

    static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
    static final int DISCOVERY_PERIOD = 1000;
    static final int DISCOVERY_TIMEOUT = 15000; // 15 seconds
    private static final String DELIMITER = "\t";

    private final Map<String, Set<String>> uris = new ConcurrentHashMap<>();
    private static Discovery instance;
    private volatile boolean running = true;

    public static Discovery getInstance() {
        if (instance == null) {
            instance = new Discovery();
        }
        return instance;
    }

    public Discovery() {}

    public void start(InetSocketAddress addr, String serviceName, String serviceURI) {
        Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s", addr, serviceName, serviceURI));

        byte[] announceBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
        DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

        try {
            MulticastSocket ms = new MulticastSocket(addr.getPort());
            
            // Log available network interfaces
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                Log.info("Interface: " + ni.getName() + " up=" + ni.isUp() + " multicast=" + ni.supportsMulticast());
                if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                    try {
                        ms.joinGroup(new InetSocketAddress(addr.getAddress(), 0), ni);
                        Log.info("Joined multicast group on interface: " + ni.getName());
                    } catch (Exception e) {
                        Log.warning("Could not join group on interface " + ni.getName() + ": " + e.getMessage());
                    }
                }
            }

            // Thread for sending announcements
            new Thread(() -> {
                while (running) {
                    try {
                        ms.send(announcePkt);
                        Log.fine("Sent announcement: " + serviceName + " -> " + serviceURI);
                        Thread.sleep(DISCOVERY_PERIOD);
                    } catch (Exception e) {
                        Log.severe("Error sending announcement for " + serviceName + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                ms.close();
            }, "Discovery-Send-" + serviceName).start();

            // Thread for receiving announcements
            new Thread(() -> {
                DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
                while (running) {
                    try {
                        pkt.setLength(1024);
                        ms.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength());
                        String[] msgElems = msg.split(DELIMITER);
                        if (msgElems.length == 2) {
                            Log.fine(String.format("Received announcement: %s -> %s", msgElems[0], msgElems[1]));
                            uris.computeIfAbsent(msgElems[0], k -> new HashSet<>()).add(msgElems[1]);
                        } else {
                            Log.warning("Invalid announcement format: " + msg);
                        }
                    } catch (IOException e) {
                        Log.warning("Error receiving announcement: " + e.getMessage());
                    }
                }
            }, "Discovery-Receive-" + serviceName).start();

            ms.setSoTimeout(DISCOVERY_TIMEOUT);
        } catch (Exception e) {
            Log.severe("Error starting Discovery for " + serviceName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
    }

    public List<String> knownUrisOf(String serviceName) {
        return new ArrayList<>(uris.getOrDefault(serviceName, Collections.emptySet()));
    }

    public static void main(String[] args) {
        Discovery discovery = Discovery.getInstance();
        discovery.start(DISCOVERY_ADDR, "TestService", "http://localhost/test");

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Known URIs for 'TestService': " + discovery.knownUrisOf("TestService"));
        discovery.stop();
    }
}