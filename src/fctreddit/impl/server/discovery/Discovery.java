package fctreddit.impl.server.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
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
    static final int DISCOVERY_TIMEOUT = 20000; // Increased to 20 seconds
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
            ms.setTimeToLive(255); // Ensure packets can reach the network

            // Log available network interfaces
            boolean joinedGroup = false;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                    try {
                        ms.joinGroup(new InetSocketAddress(addr.getAddress(), 0), ni);
                        Log.info("Joined multicast group on interface: " + ni.getName());
                        joinedGroup = true;
                    } catch (Exception e) {
                        Log.warning("Could not join group on interface " + ni.getName() + ": " + e.getMessage());
                    }
                }
            }
            if (!joinedGroup) {
                Log.severe("No suitable network interface found for multicast. Announcements may fail.");
            }

            // Thread for sending announcements
            new Thread(() -> {
                int attempt = 0;
                while (running && attempt < 20) { // Limit attempts to avoid infinite loops
                    try {
                        ms.send(announcePkt);
                        Log.fine("Sent announcement: " + serviceName + " -> " + serviceURI);
                        attempt = 0; // Reset on success
                    } catch (IOException e) {
                        Log.severe("Error sending announcement for " + serviceName + ": " + e.getMessage());
                        e.printStackTrace();
                        attempt++;
                        try {
                            Thread.sleep(100); // Backoff before retry
                        } catch (InterruptedException ie) {
                            Log.warning("Interrupted while retrying announcement for " + serviceName);
                        }
                    }
                    try {
                        Thread.sleep(DISCOVERY_PERIOD);
                    } catch (InterruptedException e) {
                        Log.warning("Interrupted while sleeping for " + serviceName);
                    }
                }
                ms.close();
                Log.info("Stopped sending announcements for " + serviceName);
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
                        if (running) {
                            Log.warning("Error receiving announcement: " + e.getMessage());
                        }
                    }
                }
                Log.info("Stopped receiving announcements for " + serviceName);
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