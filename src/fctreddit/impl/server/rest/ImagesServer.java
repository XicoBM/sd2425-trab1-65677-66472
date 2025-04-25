package fctreddit.impl.server.rest;

import java.util.logging.Logger;
import java.net.URI;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import fctreddit.impl.server.discovery.Discovery;

public class ImagesServer {
    private static Logger Log = Logger.getLogger(ImagesServer.class.getName());
    static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8082;
    public static final String SERVICE = "Image";
    private static final String SERVER_URI_FMT = "http://%s:%s/rest";

    public static void main(String[] args) {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();

            ResourceConfig config = new ResourceConfig();
            config.register(ImagesResources.class);

            String serverURI = String.format(SERVER_URI_FMT, ip, PORT);
            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

            Log.info(String.format("%s Server ready @ %s\n", SERVICE, serverURI));

            Discovery discovery = new Discovery(DISCOVERY_ADDR, SERVICE, serverURI);
            discovery.start();
            Thread.currentThread().join();
        } catch (Exception e) {
            Log.severe(e.getMessage());
        }
    }
}
