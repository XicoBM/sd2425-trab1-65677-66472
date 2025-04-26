package fctreddit.clients.rest;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import fctreddit.api.User;
import fctreddit.api.java.Result;
import fctreddit.api.rest.RestUsers;
import fctreddit.clients.java.UsersClient;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import fctreddit.api.java.Result.ErrorCode;


public class RestUsersClient extends UsersClient {
    private static final Logger Log = Logger.getLogger(RestUsersClient.class.getName());

    protected static final int READ_TIMEOUT = 5000;  
    protected static final int CONNECT_TIMEOUT = 5000; 

    protected static final int MAX_RETRIES = 10;     
    protected static final int RETRY_SLEEP = 5000;   

    final URI serverURI;
    final Client client;
    final ClientConfig config;
    final WebTarget target;

    public RestUsersClient(URI serverURI) {
        this.serverURI = serverURI;

        this.config = new ClientConfig();
        config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT);

        this.client = ClientBuilder.newClient(config);
        this.target = client.target(this.serverURI).path(RestUsers.PATH);
}

    public Result<String> createUser(User user) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.request()
                        .accept(MediaType.APPLICATION_JSON)
                        .post(Entity.entity(user, MediaType.APPLICATION_JSON));

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

    public Result<User> getUser(String userId, String password) {
        Response r = target.path(userId)
                .queryParam(RestUsers.PASSWORD, password)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();

        int status = r.getStatus();
        if (status != Status.OK.getStatusCode()) {
            return Result.error(getErrorCodeFrom(status));
        } else {
            return Result.ok(r.readEntity(User.class));
        }
    }

    public Result<User> updateUser(String userId, String password, User user) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(userId)
                        .queryParam(RestUsers.PASSWORD, password)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .put(Entity.entity(user, MediaType.APPLICATION_JSON));

                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                } else {
                    return Result.ok(r.readEntity(User.class));
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

    public Result<User> deleteUser(String userId, String password) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(userId)
                        .queryParam(RestUsers.PASSWORD, password)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .delete();

                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                } else {
                    return Result.ok(r.readEntity(User.class));
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

    public Result<List<User>> searchUsers(String pattern) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path("/")
                        .queryParam(RestUsers.QUERY, pattern)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get();

                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                } else {
                    return Result.ok(r.readEntity(new GenericType<List<User>>() {}));
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


    public Result<User> getUserAux(String userId) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(userId)
                        .path("aux")  
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get();

                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                } else {
                    return Result.ok(r.readEntity(User.class));
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
      case 200, 209 -> ErrorCode.OK;
      case 409 -> ErrorCode.CONFLICT;
      case 403 -> ErrorCode.FORBIDDEN;
      case 404 -> ErrorCode.NOT_FOUND;
      case 400 -> ErrorCode.BAD_REQUEST;
      case 500 -> ErrorCode.INTERNAL_ERROR;
      case 501 -> ErrorCode.NOT_IMPLEMENTED;
      default -> ErrorCode.INTERNAL_ERROR;
      };
    }
}
