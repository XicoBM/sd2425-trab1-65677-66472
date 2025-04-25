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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * A class to perform service discovery, based on periodic service contact
 * endpoint announcements over multicast communication.
 * 
 * Servers announce their *name* and contact *uri* at regular intervals. The
 * server actively collects received announcements.
 * 
 * Service announcements have the following format:
 * <service-name-string><delimiter-char><service-uri-string>
 */
public class Discovery {
    private static Logger Log = Logger.getLogger(Discovery.class.getName());

    static {
        // Addresses some multicast issues on some TCP/IP stacks
        System.setProperty("java.net.preferIPv4Stack", "true");
        // Summarizes the logging format
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
    }

    // Map responsible for storing the received announcements, per service name
    private Map<String, Map<URI, Instant>> receivedAnnouncements = new ConcurrentHashMap<>();
    // Map for the locks to synchronize access to the received announcements
    private Map<String, Object> locks = new ConcurrentHashMap<>();

    // The pre-agreed multicast endpoint assigned to perform discovery.
    static final public InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
    static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;
    static final int DISCOVERY_RETRY_TIMEOUT = 5000;
    static final int MAX_DATAGRAM_SIZE = 65536;

    // Used to separate the two fields that make up a service announcement.
    private static final String DELIMITER = "\t";

    private static Discovery instance;
    private final InetSocketAddress addr;
    private final String serviceName;
    private final String serviceURI;
    private final MulticastSocket ms;
    private final ConcurrentMap<String, List<URI>> knownServices;

    /**
     * @param serviceName the name of the service to announce
     * @param serviceURI  an uri string - representing the contact endpoint of the
     *                    service being announced
     * @throws IOException
     * @throws UnknownHostException
     * @throws SocketException
     */
    public Discovery(InetSocketAddress addr, String serviceName, String serviceURI)
            throws SocketException, UnknownHostException, IOException {
        this.addr = addr;
        this.serviceName = serviceName;
        this.serviceURI = serviceURI;
        this.knownServices = new ConcurrentHashMap<>();

        if (this.addr == null) {
            throw new RuntimeException("A multicast address has to be provided.");
        }

        this.ms = new MulticastSocket(addr.getPort());
        NetworkInterface netIf = NetworkInterface.getNetworkInterfaces().nextElement(); // Ou escolhe explicitamente
                                                                                        // "eth0"
        ms.joinGroup(addr, netIf);

    }

    public Discovery(InetSocketAddress addr) throws SocketException, UnknownHostException, IOException {
        this(addr, null, null);
    }

    public static Discovery getInstance(InetSocketAddress addr, String serviceName, String serviceURI)
            throws IOException {
        if (instance == null) {
            synchronized (Discovery.class) {
                instance = new Discovery(addr);
            }
        }
        return instance;
    }

    /**
     * Starts sending service announcements at regular intervals...
     */
    public void start() {
        // If this discovery instance was initialized with information about a service,
        // start the thread that makes the periodic announcement to the multicast
        // address.
        if (this.serviceName != null && this.serviceURI != null) {
            Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s", addr, serviceName,
                    serviceURI));

            byte[] announceBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
            DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

            try {
                // Start thread to send periodic announcements
                new Thread(() -> {
                    for (;;) {
                        try {
                            ms.send(announcePkt);
                            Thread.sleep(DISCOVERY_ANNOUNCE_PERIOD);
                        } catch (Exception e) {
                            e.printStackTrace();
                            // Continue on error
                        }
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Start thread to collect announcements received from the network.
        new Thread(() -> {
            DatagramPacket pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
            for (;;) {
                try {
                    pkt.setLength(MAX_DATAGRAM_SIZE);
                    ms.receive(pkt);
                    String msg = new String(pkt.getData(), 0, pkt.getLength());
                    String[] msgElems = msg.split(DELIMITER);
                    if (msgElems.length == 2) { // Valid periodic announcement
                        String receivedServiceName = msgElems[0];
                        String receivedServiceURI = msgElems[1];

                        // Record the announcement
                        try {
                            URI uri = URI.create(receivedServiceURI);
                            receivedAnnouncements.computeIfAbsent(receivedServiceName, k -> new ConcurrentHashMap<>())
                                    .put(uri, Instant.now());
                            Object lock = locks.computeIfAbsent(receivedServiceName, k -> new Object());
                            synchronized (lock) {
                                lock.notifyAll();
                            }
                        } catch (Exception e) {
                            Log.warning("Invalid URI received: " + receivedServiceURI);
                        }
                    }
                } catch (IOException e) {
                    // Continue on error
                }
            }
        }).start();
    }

    /**
     * Returns the known services as a list of strings.
     * 
     * @param serviceName the name of the service being discovered
     * @param minReplies  minimum number of requested URIs. Blocks until the
     *                    number is satisfied or timeout occurs.
     * @return a list of strings with the service instances discovered.
     */
    public List<String> knownUrisAsStringsOf(String serviceName, int minReplies) {
        long startTime = System.currentTimeMillis();
        while (true) {
            List<URI> uris = knownServices.getOrDefault(serviceName, new ArrayList<>());
            if (uris.size() >= minReplies) {
                List<String> uriStrings = new ArrayList<>();
                for (URI uri : uris) {
                    uriStrings.add(uri.toString());
                }
                return uriStrings;
            }

            // Check for timeout
            if (System.currentTimeMillis() - startTime >= DISCOVERY_RETRY_TIMEOUT) {
                List<String> uriStrings = new ArrayList<>();
                for (URI uri : uris) {
                    uriStrings.add(uri.toString());
                }
                return uriStrings; // Return whatever is available
            }

            // Wait briefly before checking again
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                List<String> uriStrings = new ArrayList<>();
                for (URI uri : uris) {
                    uriStrings.add(uri.toString());
                }
                return uriStrings;
            }
        }
    }

    public URI[] knowUrisOf(String serviceName, int minReplies) throws InterruptedException {
        if (minReplies < 0) {
            throw new IllegalArgumentException("minReplies must be non-negative");
        }

        Object lock = locks.computeIfAbsent(serviceName, k -> new Object());
        synchronized (lock) {
            for (;;) {
                var urls = receivedAnnouncements.get(serviceName);
                if (urls != null && urls.size() >= minReplies) {
                    return urls.keySet().toArray(new URI[0]);
                }
                // Espera ser notificado quando um novo anúncio chegar, ou timeout
                lock.wait(DISCOVERY_RETRY_TIMEOUT);
            }
        }
    }

    // Main just for testing purposes
    public static void main(String[] args) throws Exception {
        Discovery discovery = new Discovery(DISCOVERY_ADDR, "test",
                "http://" + InetAddress.getLocalHost().getHostAddress());
        discovery.start();
    }
}