package fctreddit.impl.server.discovery;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Discovery class for service announcements and discovery over multicast.
 */
public class Discovery {
	private static Logger Log = Logger.getLogger(Discovery.class.getName());

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	static final public InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2262);
	static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;
	static final int DISCOVERY_RETRY_TIMEOUT = 5000;
	static final int MAX_DATAGRAM_SIZE = 65536;

	private static final String DELIMITER = "\t";

	private final InetSocketAddress addr;
	private final String serviceName;
	private final String serviceURI;
	private final MulticastSocket ms;

	// Map para armazenar os serviços conhecidos (Nome -> (URI, Timestamp))
	private final Map<String, Map<URI, Long>> knownServices = new ConcurrentHashMap<>();

	public Discovery(InetSocketAddress addr, String serviceName, String serviceURI) throws IOException {
		this.addr = addr;
		this.serviceName = serviceName;
		this.serviceURI = serviceURI;

		if (this.addr == null) {
			throw new RuntimeException("A multicast address must be provided.");
		}

		this.ms = new MulticastSocket(addr.getPort());
		this.ms.joinGroup(addr, NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
	}

	public Discovery(InetSocketAddress addr) throws IOException {
		this(addr, null, null);
	}

	public void start() {
		if (this.serviceName != null && this.serviceURI != null) {
			Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s", addr, serviceName,
					serviceURI));

			byte[] announceBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
			DatagramPacket announcePkt = new DatagramPacket(announceBytes, announceBytes.length, addr);

			new Thread(() -> {
				while (true) {
					try {
						ms.send(announcePkt);
						Thread.sleep(DISCOVERY_ANNOUNCE_PERIOD);
					} catch (Exception e) {
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
					String[] msgElems = msg.split(DELIMITER);

					if (msgElems.length == 2) {
						String receivedServiceName = msgElems[0];
						URI receivedURI = URI.create(msgElems[1]);

						knownServices.computeIfAbsent(receivedServiceName, k -> new ConcurrentHashMap<>())
								.put(receivedURI, System.currentTimeMillis());

						System.out.printf("FROM %s (%s) : %s\n", pkt.getAddress().getHostName(),
								pkt.getAddress().getHostAddress(), msg);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public URI[] knownUrisOf(String serviceName, int minReplies) {
		long now = System.currentTimeMillis();
		Map<URI, Long> services = knownServices.getOrDefault(serviceName, Collections.emptyMap());

		List<URI> activeURIs = new ArrayList<>();
		for (Map.Entry<URI, Long> entry : services.entrySet()) {
			if (now - entry.getValue() < DISCOVERY_RETRY_TIMEOUT) {
				activeURIs.add(entry.getKey());
			}
		}

		while (activeURIs.size() < minReplies) {
			try {
				Thread.sleep(500);
				now = System.currentTimeMillis(); // Atualizar o timestamp
				services = knownServices.getOrDefault(serviceName, Collections.emptyMap());
				activeURIs.clear();
		
				for (Map.Entry<URI, Long> entry : services.entrySet()) {
					if (now - entry.getValue() < DISCOVERY_RETRY_TIMEOUT) {
						activeURIs.add(entry.getKey());
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		return activeURIs.toArray(new URI[0]);
	}

	public static void main(String[] args) throws Exception {
		Discovery discovery = new Discovery(DISCOVERY_ADDR, "test",
				"http://" + InetAddress.getLocalHost().getHostAddress());
		discovery.start();
	}
}
