package fctreddit.impl.server.rest;

import java.util.List;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import fctreddit.api.rest.RestContent;
import fctreddit.impl.server.persistence.Hibernate;
import fctreddit.api.Post;
import fctreddit.api.java.Result;
import fctreddit.api.User;
import fctreddit.api.java.Content;
import fctreddit.impl.server.java.JavaContent;

public class ContentResources implements RestContent {

    private static Logger Log = Logger.getLogger(ContentResources.class.getName());
    private static Hibernate hibernate;

    final Content impl;

    public ContentResources() {
        impl = new JavaContent();
    }

    @Override
    public String createPost(Post post, String userPassword) {
        Result<String> result = impl.createPost(post, userPassword);
        if (!result.isOK()) {
            throw new WebApplicationException(errorCodeToStatus(result.error()));
        } else {
            return result.value();
        }
    }

    @Override
    public List<String> getPosts(long timestamp, String sortOrder) {
        Result<List<String>> result = impl.getPosts(timestamp, sortOrder);
        if (!result.isOK()) {
            throw new WebApplicationException(errorCodeToStatus(result.error()));
        } else {
            return result.value();
        }
    }

    @Override
    public Post getPost(String postId) {
        try {
            Post post = hibernate.get(Post.class, postId);
            if (post == null) {
                Log.warning("getPost: Post not found with ID " + postId);
                throw new WebApplicationException(Status.NOT_FOUND);
            }
            Log.info(Status.OK + " : Post retrieved with ID " + postId);
            return post;
        } catch (Exception e) {
            Log.severe("Error retrieving post with ID " + postId + ": " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<String> getPostAnswers(String postId) {
        try {
            TypedQuery<Post> query = hibernate.jpql2("SELECT p FROM Post p WHERE p.parentId = :parentId", Post.class);
            query.setParameter("parentId", postId);
            List<Post> answers = query.getResultList();
            Log.info(Status.OK + " : Answers retrieved for post ID " + postId);
            return answers.stream()
                    .map(Post::getPostId)
                    .toList();
        } catch (Exception e) {
            Log.severe("Error retrieving post answers: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Post updatePost(String postId, String userPassword, Post post) {
        try {
            Post existingPost = hibernate.get(Post.class, postId);
            if (post.getContent() != null) {
                existingPost.setContent(post.getContent());
            }
            if (post.getMediaUrl() != null) {
                existingPost.setMediaUrl(post.getMediaUrl());
            }
            hibernate.update(existingPost);
            Log.info(Status.OK + " : Post updated with ID " + postId);
            return existingPost;
        } catch (Exception e) {
            Log.severe("Error updating post with ID " + postId + ": " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deletePost(String postId, String userPassword) {
        Log.info("deletePost called with postId: " + postId + " and userPassword: " + userPassword);

        try {
            Post post = hibernate.get(Post.class, postId);
            hibernate.delete(post);
            Log.info(Status.NO_CONTENT + " : Post deleted with ID " + postId);
        } catch (Exception e) {
            Log.severe("Error deleting post with ID " + postId + ": " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void upVotePost(String postId, String userId, String userPassword) {
        Log.info("upVotePost called with postId: " + postId + " by userId: " + userId);

        User user = hibernate.get(User.class, userId);
        Post post = hibernate.get(Post.class, postId);

        if (user == null || post == null) {
            Log.info("upVotePost: Invalid input.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("upVotePost: Invalid input.");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        if (post.getVoteByUser(userId) != null) {
            Log.info("upVotePost: User has already voted.");
            throw new WebApplicationException(Status.CONFLICT);
        }

        try {
            post.addVote(userId, "up");
            Log.info(Status.NO_CONTENT + " : Upvote declared " + postId);
        } catch (Exception e) {
            Log.severe("Error upvoting post: " + e.getMessage());
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
    }

    @Override
    public void removeUpVotePost(String postId, String userId, String userPassword) {
        Log.info("removeUpVotePost called with postId: " + postId + " by userId: " + userId);

        User user = hibernate.get(User.class, userId);
        Post post = hibernate.get(Post.class, postId);

        if (user == null || post == null) {
            Log.info("removeUpVotePost: Invalid input.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("removeUpVotePost: Invalid input.");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        if (post.getVoteByUser(userId) == null) {
            Log.info("removeUpVotePost: User has not voted.");
            throw new WebApplicationException(Status.CONFLICT);
        }

        try {
            removeVote(post, userId);
            Log.info(Status.NO_CONTENT + " : Upvote removed from post with ID " + postId);
        } catch (Exception e) {
            Log.severe("Error removing upvote from post: " + e.getMessage());
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
    }

    @Override
    public void downVotePost(String postId, String userId, String userPassword) {
        Log.info("downVotePost called with postId: " + postId + " by userId: " + userId);

        User user = hibernate.get(User.class, userId);
        Post post = hibernate.get(Post.class, postId);

        if (user == null || post == null) {
            Log.info("downVotePost: Invalid input.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("downVotePost: Invalid input.");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        if (post.getVoteByUser(userId) != null) {
            Log.info("downVotePost: User has already voted.");
            throw new WebApplicationException(Status.CONFLICT);
        }

        try {
            post.addVote(userId, "down");
            Log.info(Status.NO_CONTENT + " : Downvote declared " + postId);
        } catch (Exception e) {
            Log.severe("Error upvoting post: " + e.getMessage());
            throw new WebApplicationException(Status.BAD_REQUEST);
        }

    }

    @Override
    public void removeDownVotePost(String postId, String userId, String userPassword) {
        Log.info("removeDownVotePost called with postId: " + postId + " by userId: " + userId);
        User user = hibernate.get(User.class, userId);
        Post post = hibernate.get(Post.class, postId);

        if (user == null || post == null) {
            Log.info("removeDownVotePost: Invalid input.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("removeDownVotePost: Invalid input.");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        if (post.getVoteByUser(userId) == null) {
            Log.info("removeDownVotePost: User has not voted.");
            throw new WebApplicationException(Status.CONFLICT);
        }

        try {
            removeVote(post, userId);
            Log.info(Status.NO_CONTENT + " : Downvote removed from post with ID " + postId);
        } catch (Exception e) {
            Log.severe("Error removing downvote from post: " + e.getMessage());
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
    }

    @Override
    public Integer getupVotes(String postId) {
        Log.info("getupVotes called with postId: " + postId);
        Post post = hibernate.get(Post.class, postId);

        if (post == null) {
            Log.info("getupVotes: Invalid input.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        try {
            Integer votes = post.getUpVote();
            Log.info(Status.OK + " : upVotes returned by getUpVotes");
            return votes;
        } catch (Exception e) {
            Log.severe("Error retrieving upvotes: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Integer getDownVotes(String postId) {
        Log.info("getDownVotes called with postId: " + postId);
        Post post = hibernate.get(Post.class, postId);

        if (post == null) {
            Log.info("getDownVotes: Invalid input");
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        try {
            Integer votes = post.getDownVote();
            Log.info(Status.OK + " : downVotes return by getDownVotes");
            return votes;
        } catch (Exception e) {
            Log.severe("Error retrieving upvotes: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    /*
     * Generic code for the removing votes
     */
    private void removeVote(Post post, String userId) {
        post.removeVote(userId);
        hibernate.update(post);
    }

    protected static Status errorCodeToStatus(Result.ErrorCode errorCode) {
        Status status = switch (errorCode) {
            case NOT_FOUND -> Status.NOT_FOUND;
            case CONFLICT -> Status.CONFLICT;
            case FORBIDDEN -> Status.FORBIDDEN;
            case NOT_IMPLEMENTED -> Status.NOT_IMPLEMENTED;
            case BAD_REQUEST -> Status.BAD_REQUEST;
            default -> Status.INTERNAL_SERVER_ERROR;
        };
        return status;
    }
}
