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

    static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("230.0.0.1", 4446); // IP e porta mais comuns para multicast
    static final int DISCOVERY_PERIOD = 1000;
    static final int DISCOVERY_TIMEOUT = 5000;
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
            
            // Exibe as interfaces de rede disponíveis para multicast
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                System.out.println("Interface: " + ni.getName() + " up=" + ni.isUp() + " multicast=" + ni.supportsMulticast());
                if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                    try {
                        ms.joinGroup(new InetSocketAddress(addr.getAddress(), 0), ni);
                    } catch (Exception e) {
                        Log.warning("Could not join group on interface " + ni.getName() + ": " + e.getMessage());
                    }
                }
            }

            // Thread para enviar anúncios
            new Thread(() -> {
                while (running) {
                    try {
                        ms.send(announcePkt);
                        Thread.sleep(DISCOVERY_PERIOD);
                    } catch (Exception e) {
                        Log.severe("Error sending announcement: " + e.getMessage());
                    }
                }
            }).start();

            // Thread para receber anúncios
            new Thread(() -> {
                DatagramPacket pkt = new DatagramPacket(new byte[1024], 1024);
                while (running) {
                    try {
                        pkt.setLength(1024);
                        ms.receive(pkt);
                        String msg = new String(pkt.getData(), 0, pkt.getLength());
                        String[] msgElems = msg.split(DELIMITER);
                        if (msgElems.length == 2) {
                            Log.info(String.format("Received announcement: %s", msg));
                            uris.computeIfAbsent(msgElems[0], k -> new HashSet<>()).add(msgElems[1]);
                        }
                    } catch (IOException e) {
                        Log.warning("Error receiving announcement: " + e.getMessage());
                    }
                }
            }).start();

            ms.setSoTimeout(DISCOVERY_TIMEOUT);
        } catch (Exception e) {
            Log.severe("Error starting Discovery: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }

    public List<String> knownUrisOf(String serviceName) {
        return new ArrayList<>(uris.getOrDefault(serviceName, Collections.emptySet()));
    }

    public static void main(String[] args) {
        // Teste simples
        Discovery discovery = Discovery.getInstance();
        discovery.start(DISCOVERY_ADDR, "TestService", "http://localhost/test");

        // Simula um tempo de execução para enviar e receber pacotes
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Exibe as URIs conhecidas
        System.out.println("Known URIs for 'TestService': " + discovery.knownUrisOf("TestService"));

        discovery.stop();
    }
}
