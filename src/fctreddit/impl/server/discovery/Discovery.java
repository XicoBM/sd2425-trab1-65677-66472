package fctreddit.impl.server.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class Discovery {
    private static Logger Log = Logger.getLogger(Discovery.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
    public static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;
    public static final int DISCOVERY_RETRY_TIMEOUT = 10000; // Aumentado para 10s
    public static final int MAX_DATAGRAM_SIZE = 65536;

    private static final String DELIMITER = "\t";

    private final InetSocketAddress addr;
    private final String serviceName;
    private final String serviceURI;
    private final MulticastSocket ms;
    private final Map<String, Map<URI, Long>> knownServices = new ConcurrentHashMap<>();
    private final Set<String> seenServices = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Lock lock = new ReentrantLock();
    private final Condition serviceDiscovered = lock.newCondition();

    public Discovery(InetSocketAddress addr, String serviceName, String serviceURI)
            throws SocketException, UnknownHostException, IOException {
        this.addr = addr;
        this.serviceName = serviceName;
        this.serviceURI = serviceURI;

        if (this.addr == null) {
            throw new RuntimeException("A multicast address has to be provided.");
        }

        this.ms = new MulticastSocket(addr.getPort());
        this.ms.joinGroup(addr, NetworkInterface.getByInetAddress(getSiteLocalAddress()));
    }

    public Discovery(InetSocketAddress addr) throws SocketException, UnknownHostException, IOException {
        this(addr, null, null);
    }

    public void start() {
        if (this.serviceName != null && this.serviceURI != null) {
            Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s", addr, serviceName, serviceURI));

            byte[] announceBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
            DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

            new Thread(() -> {
                while (true) {
                    try {
                        ms.send(announcePkt);
                        Log.info("Sent announcement: " + new String(announceBytes));
                        Thread.sleep(DISCOVERY_ANNOUNCE_PERIOD);
                    } catch (Exception e) {
                        Log.severe("Error sending announcement: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        new Thread(() -> {
            DatagramPacket pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
            while (true) {
                try {
                    pkt.setLength(MAX_DATAGRAM_SIZE);
                    ms.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength());
                    Log.info("Received: " + msg + " from " + pkt.getAddress().getHostAddress());

                    String[] msgElems = msg.split(DELIMITER);
                    if (msgElems.length == 2) {
                        String receivedServiceName = msgElems[0];
                        URI receivedServiceURI = URI.create(msgElems[1]);
                        String serviceKey = receivedServiceName + " -> " + receivedServiceURI;

                        knownServices.computeIfAbsent(receivedServiceName, k -> new ConcurrentHashMap<>())
                                .put(receivedServiceURI, System.currentTimeMillis());

                        if (seenServices.add(serviceKey)) {
                            Log.info(String.format("Discovered %s (%s) : %s", pkt.getAddress().getHostName(),
                                    pkt.getAddress().getHostAddress(), msg));

                            lock.lock();
                            try {
                                serviceDiscovered.signalAll();
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.severe("Error receiving packet: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public URI[] knownUrisOf(String serviceName, int minReplies) {
        lock.lock();
        try {
            long deadline = System.currentTimeMillis() + DISCOVERY_RETRY_TIMEOUT;
            List<URI> activeURIs = new ArrayList<>();

            while (true) {
                activeURIs.clear();
                Map<URI, Long> services = knownServices.getOrDefault(serviceName, Collections.emptyMap());
                long now = System.currentTimeMillis();

                for (Map.Entry<URI, Long> entry : services.entrySet()) {
                    if (now - entry.getValue() < DISCOVERY_RETRY_TIMEOUT) {
                        activeURIs.add(entry.getKey());
                    }
                }

                if (activeURIs.size() >= minReplies || now >= deadline)
                    break;

                long remaining = deadline - now;
                if (remaining > 0) {
                    try {
                        Log.info("Waiting for service " + serviceName + ", found: " + activeURIs.size());
                        serviceDiscovered.awaitNanos(remaining * 1_000_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            }

            Log.info("Active URIs for " + serviceName + ": " + activeURIs);
            return activeURIs.toArray(new URI[0]);
        } finally {
            lock.unlock();
        }
    }

    public static InetAddress getSiteLocalAddress() {
        try {
            for (var ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (var a : Collections.list(ni.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a.isSiteLocalAddress()) {
                        Log.info("Selected site-local address: " + a.getHostAddress());
                        return a;
                    }
                }
            }
        } catch (Exception e) {
            Log.severe("Error finding site-local address: " + e.getMessage());
            e.printStackTrace();
        }
        throw new RuntimeException("No site-local address found.");
    }

    public static void main(String[] args) throws Exception {
        String hostAddress = getSiteLocalAddress().getHostAddress();
        Discovery discovery = new Discovery(DISCOVERY_ADDR, "test", "http://" + hostAddress + ":8080");
        discovery.start();
    }
}