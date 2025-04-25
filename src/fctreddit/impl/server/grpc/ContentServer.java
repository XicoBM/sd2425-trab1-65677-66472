package fctreddit.impl.server.grpc;

import java.net.InetAddress;
import java.util.logging.Logger;

import fctreddit.impl.server.discovery.Discovery;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerCredentials;

public class ContentServer {
    public static final int PORT = 9002;
    private static final String GRPC_CTX = "/grpc";
    private static final String SERVER_BASE_URI = "grpc://%s:%s%s";
    private static final String SERVICE = "Content";
    private static Logger Log = Logger.getLogger(ContentServer.class.getName());

    public static void main(String[] args) throws Exception {
		System.out.println("Starting Content gRPC Server...");
		GrpcContentServerStub stub = new GrpcContentServerStub();
		ServerCredentials cred = InsecureServerCredentials.create();
		Server server = Grpc.newServerBuilderForPort(PORT, cred).addService(stub).build();
		String serverURI = String.format(SERVER_BASE_URI, InetAddress.getLocalHost().getHostAddress(), PORT, GRPC_CTX);

		// Start service discovery
		Discovery discovery = new Discovery(Discovery.DISCOVERY_ADDR, SERVICE, serverURI);
		Log.info(String.format("Preparing to announce %s with URI: %s", SERVICE, serverURI));
		discovery.start();

		Log.info(
				String.format("Service discovery started for %s @ %s at %s", SERVICE, serverURI, new java.util.Date()));
		Log.info(String.format("Content gRPC Server ready @ %s\n", serverURI));

		Log.fine("Content gRPC Server started successfully");
		System.out.println("Content gRPC Server started successfully");
		server.start().awaitTermination();
	}
}