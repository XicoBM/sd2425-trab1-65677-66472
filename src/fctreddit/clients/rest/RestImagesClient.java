package fctreddit.clients.rest;

import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import fctreddit.api.java.Result;
import fctreddit.api.rest.RestImage;
import fctreddit.clients.java.ImageClient;
import fctreddit.api.java.Result.ErrorCode;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

public class RestImagesClient extends ImageClient {

    private static final Logger Log = Logger.getLogger(RestImagesClient.class.getName());

    protected static final int READ_TIMEOUT = 5000;
    protected static final int CONNECT_TIMEOUT = 5000;
    protected static final int MAX_RETRIES = 10;
    protected static final int RETRY_SLEEP = 5000;

    final URI serverURI;
    final Client client;
    final WebTarget target;

    public RestImagesClient(URI serverURI) {
        this.serverURI = serverURI;
        var config = new ClientConfig();
        config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT);
        this.client = ClientBuilder.newClient(config);
        this.target = client.target(this.serverURI).path(RestImage.PATH);
    }

    public Result<String> createImage(String userId, byte[] imageContent, String password) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(userId)
                        .queryParam(RestImage.PASSWORD, password)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .post(Entity.entity(imageContent, MediaType.APPLICATION_OCTET_STREAM));

                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                } else {
                    return Result.ok(r.readEntity(String.class));
                }

            } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<byte[]> getImage(String userId, String imageId) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(userId).path(imageId)
                        .request()
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .get();

                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                } else {
                    return Result.ok(r.readEntity(byte[].class));
                }

            } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<Void> deleteImage(String userId, String imageId, String password) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(userId).path(imageId)
                        .queryParam(RestImage.PASSWORD, password)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .delete();

                int status = r.getStatus();
                if (status == Status.NO_CONTENT.getStatusCode()) {
                    return Result.ok(null);
                } else {
                    return Result.error(getErrorCodeFrom(status));
                }

            } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    private void retryWait() {
        try {
            Thread.sleep(RETRY_SLEEP);
        } catch (InterruptedException e) {
            Log.warning("Retry wait interrupted: " + e.getMessage());
        }
    }

    public static ErrorCode getErrorCodeFrom(int status) {
        return switch (status) {
            case 200, 204 -> ErrorCode.OK;
            case 400 -> ErrorCode.BAD_REQUEST;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            case 500 -> ErrorCode.INTERNAL_ERROR;
            case 501 -> ErrorCode.NOT_IMPLEMENTED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
