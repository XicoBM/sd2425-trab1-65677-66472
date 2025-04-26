package fctreddit.impl.server.grpc;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

import fctreddit.impl.server.discovery.Discovery;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerCredentials;

public class UsersServer {
	public static final int PORT = 9000;
	public static final String SERVICE = "Users";

	private static final String GRPC_CTX = "/grpc";
	private static final String SERVER_BASE_URI = "grpc://%s:%s%s";

	private static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266); 


	private static Logger Log = Logger.getLogger(UsersServer.class.getName());

	public static void main(String[] args) throws Exception {
		System.out.println("Starting Users gRPC Server...");
		GrpcUsersServerStub stub = new GrpcUsersServerStub();
		ServerCredentials cred = InsecureServerCredentials.create();
		Server server = Grpc.newServerBuilderForPort(PORT, cred).addService(stub).build();
		String serverURI = String.format(SERVER_BASE_URI, InetAddress.getLocalHost().getHostAddress(), PORT, GRPC_CTX);

		Discovery discovery = Discovery.getInstance();
		Log.info(String.format("Preparing to announce %s with URI: %s", SERVICE, serverURI));
		discovery.start(DISCOVERY_ADDR, SERVICE, serverURI);

		Log.info(
				String.format("Service discovery started for %s @ %s at %s", SERVICE, serverURI, new java.util.Date()));
		Log.info(String.format("Users gRPC Server ready @ %s\n", serverURI));

		Log.fine("Users gRPC Server started successfully");
		System.out.println("Users gRPC Server started successfully");
		server.start().awaitTermination();
	}
}