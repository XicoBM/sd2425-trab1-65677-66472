package fctreddit.impl.server.rest;

import java.util.List;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import fctreddit.api.rest.RestContent;
import fctreddit.impl.server.persistence.Hibernate;
import fctreddit.api.Post;
import fctreddit.api.java.Result;
import fctreddit.api.User;

public class ContestResources implements RestContent {

    private static Logger Log = Logger.getLogger(ContestResources.class.getName());
    private Hibernate hibernate;

    @Override
    public String createPost(Post post, String userPassword) {
        Log.info("createPost called with userId: " + post.getAuthorId());
        User user = hibernate.get(User.class, post.getAuthorId());

        if (post.getAuthorId() == null) {
            Log.info("createPost: Invalid input.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        if (user.getPassword() != userPassword) {
            Log.info("createPost: Invalid input.");
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        try {
            hibernate.persist(post);
            Log.info(Status.OK + " : Post created with ID " + post.getPostId());
            return post.getPostId();
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("createPost: Failed to write post.");
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
    }

    @Override
    public List<String> getPosts(long timestamp, String sortOrder) {
        Log.info("getPosts called with timestamp: " + timestamp + " and sortOrder: " + sortOrder);

        try {
            // Recupera todos os posts do banco de dados
            List<Post> posts = hibernate.jpql("SELECT p FROM Post p WHERE p.parentId IS NULL", Post.class);
            // Filtra os posts pelo timestamp, se fornecido
            if (timestamp > 0) {
                posts = posts.stream()
                        .filter(post -> post.getCreationTimestamp() >= timestamp)
                        .toList();
            }
            if (sortOrder != null) {
                switch (sortOrder) {
                    case "MOST_UP_VOTES":
                        posts.sort((p1, p2) -> Integer.compare(p2.getUpVote(), p1.getUpVote()));
                        break;
                    /*
                    case "MOST_REPLIES":
                        posts.sort((p1, p2) -> Integer.compare(p2.getReplies().size(), p1.getReplies().size()));
                        break;
                    */
                    default:
                        Log.warning("Invalid sortOrder: " + sortOrder);
                        break;
                }
            }
            return posts.stream()
                    .map(Post::getPostId)
                    .toList();
        } catch (Exception e) {
            Log.severe("Error retrieving posts: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Post getPost(String postId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPost'");
    }

    @Override
    public List<String> getPostAnswers(String postId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPostAnswers'");
    }

    @Override
    public Post updatePost(String postId, String userPassword, Post post) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updatePost'");
    }

    @Override
    public void deletePost(String postId, String userPassword) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deletePost'");
    }

    @Override
    public void upVotePost(String postId, String userId, String userPassword) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'upVotePost'");
    }

    @Override
    public void removeUpVotePost(String postId, String userId, String userPassword) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeUpVotePost'");
    }

    @Override
    public void downVotePost(String postId, String userId, String userPassword) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'downVotePost'");
    }

    @Override
    public void removeDownVotePost(String postId, String userId, String userPassword) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeDownVotePost'");
    }

    @Override
    public Integer getupVotes(String postId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getupVotes'");
    }

    @Override
    public Integer getDownVotes(String postId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDownVotes'");
    }

}
