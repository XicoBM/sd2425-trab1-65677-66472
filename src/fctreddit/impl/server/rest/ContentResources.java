package fctreddit.impl.server.rest;

import java.util.List;
import java.util.logging.Logger;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import fctreddit.api.rest.RestContent;
import fctreddit.api.Post;
import fctreddit.api.java.Result;
import fctreddit.api.java.Content;
import fctreddit.impl.server.java.JavaContent;

public class ContentResources implements RestContent {

    private static final Logger Log = Logger.getLogger(ContentResources.class.getName());
    private final Content impl;

    public ContentResources() {
        impl = new JavaContent();
    }

    @Override
    public String createPost(Post post, String userPassword) {
        Log.info("createPost called with post: " + post + " and userPassword: [PROTECTED]");
        return handleResult(impl.createPost(post, userPassword), "Failed to create post");
    }

    @Override
    public List<String> getPosts(long timestamp, String sortOrder) {
        Log.info("getPosts called with timestamp: " + timestamp + " and sortOrder: " + sortOrder);
        return handleResult(impl.getPosts(timestamp, sortOrder), "Failed to retrieve posts");
    }

    @Override
    public Post getPost(String postId) {
        Log.info("getPost called with postId: " + postId);
        return handleResult(impl.getPost(postId), "Failed to retrieve post with ID: " + postId);
    }

    @Override
    public List<String> getPostAnswers(String postId, long maxTimeout) {
        Log.info("getPostAnswers called with postId: " + postId);
        return handleResult(impl.getPostAnswers(postId, maxTimeout), "Failed to retrieve answers for post with ID: " + postId);
    }

    @Override
    public Post updatePost(String postId, String userPassword, Post post) {
        Log.info("updatePost called with postId: " + postId + ", userPassword: [PROTECTED], and post: " + post);
        return handleResult(impl.updatePost(postId, userPassword, post), "Failed to update post with ID: " + postId);
    }

    @Override
    public void deletePost(String postId, String userPassword) {
        Log.info("deletePost called with postId: " + postId + " and userPassword: [PROTECTED]");
        handleResult(impl.deletePost(postId, userPassword), "Failed to delete post with ID: " + postId);
    }

    @Override
    public void upVotePost(String postId, String userId, String userPassword) {
        Log.info("upVotePost called with postId: " + postId + " by userId: " + userId);
        handleResult(impl.upVotePost(postId, userId, userPassword), "Failed to upvote post with ID: " + postId);
    }

    @Override
    public void removeUpVotePost(String postId, String userId, String userPassword) {
        Log.info("removeUpVotePost called with postId: " + postId + " by userId: " + userId);
        handleResult(impl.removeUpVotePost(postId, userId, userPassword), "Failed to remove upvote from post with ID: " + postId);
    }

    @Override
    public void downVotePost(String postId, String userId, String userPassword) {
        Log.info("downVotePost called with postId: " + postId + " by userId: " + userId);
        handleResult(impl.downVotePost(postId, userId, userPassword), "Failed to downvote post with ID: " + postId);
    }

    @Override
    public void removeDownVotePost(String postId, String userId, String userPassword) {
        Log.info("removeDownVotePost called with postId: " + postId + " by userId: " + userId);
        handleResult(impl.removeDownVotePost(postId, userId, userPassword), "Failed to remove downvote from post with ID: " + postId);
    }

    @Override
    public Integer getupVotes(String postId) {
        Log.info("getupVotes called with postId: " + postId);
        return handleResult(impl.getupVotes(postId), "Failed to get upvotes for post with ID: " + postId);
    }

    @Override
    public Integer getDownVotes(String postId) {
        Log.info("getDownVotes called with postId: " + postId);
        return handleResult(impl.getDownVotes(postId), "Failed to get downvotes for post with ID: " + postId);
    }

    @Override
    public void nullifyPostAuthors(String userId) {
        Log.info("nullifyPostAuthors called with userId: " + userId);
        handleResult(impl.nullifyPostAuthors(userId), "Failed to nullify authors for posts by user with ID: " + userId);
    }
   
    @Override
    public void deleteVotesFromUser(String userId) {
        Log.info("deleteVotesFromUser called with userId: " + userId);
        handleResult(impl.deleteVotesFromUser(userId), "Failed to delete votes for user with ID: " + userId);
    }


    private <T> T handleResult(Result<T> result, String errorMessage) {
        if (!result.isOK()) {
            Log.severe(errorMessage + ": " + result.error());
            throw new WebApplicationException(errorMessage, errorCodeToStatus(result.error()));
        }
        return result.value();
    }

    protected static Status errorCodeToStatus(Result.ErrorCode errorCode) {
        return switch (errorCode) {
            case NOT_FOUND -> Status.NOT_FOUND;
            case CONFLICT -> Status.CONFLICT;
            case FORBIDDEN -> Status.FORBIDDEN;
            case NOT_IMPLEMENTED -> Status.NOT_IMPLEMENTED;
            case BAD_REQUEST -> Status.BAD_REQUEST;
            default -> Status.INTERNAL_SERVER_ERROR;
        };
    }
}