package fctreddit.clients.rest;

import java.net.URI;
import java.util.List;
import java.util.logging.Logger;

import fctreddit.api.Post;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.api.rest.RestContent;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

public class RestPostsClient {

    private static final Logger Log = Logger.getLogger(RestPostsClient.class.getName());

    protected static final int READ_TIMEOUT = 5000;
    protected static final int CONNECT_TIMEOUT = 5000;
    protected static final int MAX_RETRIES = 10;
    protected static final int RETRY_SLEEP = 5000;

    final URI serverURI;
    final Client client;
    final WebTarget target;

    public RestPostsClient(URI serverURI) {
        this.serverURI = serverURI;
        var config = new ClientConfig();
        config.property(ClientProperties.READ_TIMEOUT, READ_TIMEOUT);
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECT_TIMEOUT);
        this.client = ClientBuilder.newClient(config);
        this.target = client.target(serverURI).path(RestContent.PATH);
    }

    public Result<String> createPost(Post post, String password) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.queryParam(RestContent.PASSWORD, password)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .post(Entity.entity(post, MediaType.APPLICATION_JSON));
                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                }
                return Result.ok(r.readEntity(String.class));
            } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<List<String>> getPosts(long timestamp, String sortOrder) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.queryParam(RestContent.TIMESTAMP, timestamp)
                        .queryParam(RestContent.SORTBY, sortOrder)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get();
                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                }
                return Result.ok(r.readEntity(new GenericType<List<String>>() {}));
              
              } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<Post> getPost(String postId) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(postId)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get();
                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                }
                return Result.ok(r.readEntity(Post.class));
            } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<List<String>> getPostAnswers(String postId, long timeout) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(postId).path(RestContent.REPLIES)
                        .queryParam(RestContent.TIMEOUT, timeout)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get();
                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                }
                return Result.ok(r.readEntity(new GenericType<List<String>>() {}));
              } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<Post> updatePost(String postId, String password, Post post) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(postId)
                        .queryParam(RestContent.PASSWORD, password)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .put(Entity.entity(post, MediaType.APPLICATION_JSON));
                int status = r.getStatus();
                if (status != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(status));
                }
                return Result.ok(r.readEntity(Post.class));
            } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<Void> deletePost(String postId, String password) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(postId)
                        .queryParam(RestContent.PASSWORD, password)
                        .request()
                        .delete();
                if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
                    return Result.ok(null);
                } else {
                    return Result.error(getErrorCodeFrom(r.getStatus()));
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

    public Result<Void> vote(String postId, String userId, String password, boolean upvote, boolean remove) {
        String action = upvote ? RestContent.UPVOTE : RestContent.DOWNVOTE;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                var voteTarget = target.path(postId).path(action).path(userId)
                        .queryParam(RestContent.PASSWORD, password)
                        .request()
                        .accept(MediaType.APPLICATION_JSON);
                Response r = remove ? voteTarget.delete() : voteTarget.post(null);
                if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
                    return Result.ok(null);
                } else {
                    return Result.error(getErrorCodeFrom(r.getStatus()));
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

    public Result<Integer> getVotes(String postId, boolean upvote) {
        String action = upvote ? RestContent.UPVOTE : RestContent.DOWNVOTE;
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path(postId).path(action)
                        .request()
                        .accept(MediaType.APPLICATION_JSON)
                        .get();
                if (r.getStatus() != Status.OK.getStatusCode()) {
                    return Result.error(getErrorCodeFrom(r.getStatus()));
                }
                return Result.ok(r.readEntity(Integer.class));
            } catch (ProcessingException e) {
                Log.info("ProcessingException: " + e.getMessage());
                retryWait();
            } catch (Exception e) {
                Log.severe("Exception: " + e.getMessage());
            }
        }
        return Result.error(ErrorCode.TIMEOUT);
    }

    public Result<Void> nullifyPostAuthors(String userId) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path("nullify-author").path(userId)
                        .request()
                        .post(null);
                if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
                    return Result.ok(null);
                } else {
                    return Result.error(getErrorCodeFrom(r.getStatus()));
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
    
    public Result<Void> deleteVotesFromUser(String userId) {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Response r = target.path("delete-votes").path(userId)
                        .request()
                        .delete();
                if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
                    return Result.ok(null);
                } else {
                    return Result.error(getErrorCodeFrom(r.getStatus()));
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
