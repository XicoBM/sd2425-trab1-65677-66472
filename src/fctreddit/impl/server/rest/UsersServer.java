package fctreddit.impl.server.rest;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import fctreddit.impl.server.discovery.Discovery;

public class UsersServer {
    private static Logger Log = Logger.getLogger(UsersServer.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
    }

    public static final int PORT = 8080;
    public static final String SERVICE = "Users";
    private static final String SERVER_URI_FMT = "http://%s:%s/rest";

    public static void main(String[] args) {
        try {
            // Configuração do servidor REST
            ResourceConfig config = new ResourceConfig();
            config.register(UsersResources.class);

            // Inicialização do servidor
            String ip = InetAddress.getLocalHost().getHostAddress();
            String serverURI = String.format(SERVER_URI_FMT, ip, PORT);
            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

            // Registro no serviço de descoberta
            Discovery discovery = new Discovery(Discovery.DISCOVERY_ADDR, SERVICE, serverURI);
            discovery.start();

            Log.info(String.format("%s Server ready @ %s\n", SERVICE, serverURI));

            // Manter o servidor ativo
            Thread.currentThread().join();
        } catch (Exception e) {
            Log.severe("Error starting UsersServer: " + e.getMessage());
        }
    }
}