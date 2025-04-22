package fctreddit.impl.server.rest;

import java.util.logging.Logger;
import java.net.URI;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import fctreddit.impl.server.discovery.Discovery;

public class ContentServer {
    private static Logger Log = Logger.getLogger(ContentServer.class.getName());
    static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266); 
    static final String SERVER_URI_FMT = "http://%s:%s/rest";

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8081;
    public static final String SERVICE = "Posts";

    public static void main(String[] args) {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();

            ResourceConfig config = new ResourceConfig();
            config.register(new ContentResources());

            String serverURI = String.format(SERVER_URI_FMT, ip, PORT);
            URI uri = URI.create(serverURI);
            JdkHttpServerFactory.createHttpServer(uri, config);

            Log.info(String.format("%s Server ready @ %s\n", SERVICE, serverURI));

            Discovery.getInstance().start(DISCOVERY_ADDR, SERVICE, serverURI);
        } catch (Exception e) {
            Log.severe(e.getMessage());
        }
    }
}
