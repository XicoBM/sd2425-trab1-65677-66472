package fctreddit.impl.server.grpc;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

import fctreddit.impl.server.discovery.Discovery;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerCredentials;

public class ImagesServer {
    public static final int PORT = 9001;
    private static final String GRPC_CTX = "/grpc";
    private static final String SERVER_BASE_URI = "grpc://%s:%s%s";
    private static final String SERVICE = "Image";
    private static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);
    private static Logger Log = Logger.getLogger(ImagesServer.class.getName());

    public static void main(String[] args) {
        try {
            GrpcUsersServerStub stub = new GrpcUsersServerStub();
            ServerCredentials cred = InsecureServerCredentials.create();
            Server server = Grpc.newServerBuilderForPort(PORT, cred).addService(stub).build();
    
            // Inicializar o servidor
            server.start();
            Log.info("Users gRPC Server started.");
    
            // Obter o URI do servidor
            String serverURI = String.format(SERVER_BASE_URI, InetAddress.getLocalHost().getHostAddress(), PORT, GRPC_CTX);
            Log.info(String.format("Users gRPC Server ready at: @ %s", serverURI));
    
            // Anunciar ao Discovery
            Discovery discovery = Discovery.getInstance();
            discovery.start(DISCOVERY_ADDR, SERVICE, serverURI);
            Log.info("Announced Users service to discovery at: " + DISCOVERY_ADDR);
    
            // Manter o servidor ativo
            server.awaitTermination();
        } catch (Exception e) {
            Log.severe("Failed to start Users gRPC Server: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}