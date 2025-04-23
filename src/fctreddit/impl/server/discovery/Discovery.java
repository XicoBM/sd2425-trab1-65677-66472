package fctreddit.impl.server.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.*;
import java.util.logging.Logger;

public class Discovery {
    private static final Logger Log = Logger.getLogger(Discovery.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }

    private static final String DELIMITER = "\t";
    private static final int DISCOVERY_PERIOD = 1000;

    private final Map<String, Set<String>> uris = new HashMap<>();
    private boolean started = false;

    private static Discovery instance;

    public static synchronized Discovery getInstance() {
        if (instance == null) {
            instance = new Discovery();
        }
        return instance;
    }

    private Discovery() {}

    public synchronized void start(InetSocketAddress addr, String serviceName, String serviceURI) {
        if (started) return;
        started = true;

        Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s", addr, serviceName, serviceURI));

        byte[] announceBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
        DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

        try {
            MulticastSocket ms = new MulticastSocket(addr.getPort());

            // Solução robusta para interfaces de rede
            NetworkInterface nif = NetworkInterface.networkInterfaces()
                .filter(ni -> {
                    try {
                        return ni.supportsMulticast() && ni.isUp();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new IOException("No suitable network interface found"));

            ms.joinGroup(addr, nif);

            // Announcement thread
            new Thread(() -> {
                while (true) {
                    try {
                        ms.send(announcePkt);
                        Thread.sleep(DISCOVERY_PERIOD);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            // Listener thread
            new Thread(() -> {
                DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
                while (true) {
                    try {
                        pkt.setLength(1024);
                        ms.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength());
                        String[] parts = msg.split(DELIMITER);

                        if (parts.length == 2) {
                            String name = parts[0];
                            String uri = parts[1];

                            synchronized (uris) {
                                uris.computeIfAbsent(name, k -> new HashSet<>()).add(uri);
                            }

                            Log.info(String.format("Received announcement: %s -> %s", name, uri));
                        }
                    } catch (IOException e) {
                        // Silently ignore
                    }
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> knownUrisOf(String serviceName) {
        synchronized (uris) {
            return new ArrayList<>(uris.getOrDefault(serviceName, Collections.emptySet()));
        }
    }
}
