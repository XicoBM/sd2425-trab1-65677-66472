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
                     * case "MOST_REPLIES":
                     * posts.sort((p1, p2) -> Integer.compare(p2.getReplies().size(),
                     * p1.getReplies().size()));
                     * break;
                     */
                    default:
                        Log.warning("Invalid sortOrder: " + sortOrder);
                        break;
                }
            }
            Log.info(Status.OK + " : Posts retrieved with timestamp " + timestamp);
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
        Log.info("getPost called with postId: " + postId);

        try {
            Post post = hibernate.get(Post.class, postId);
            if (post == null) {
                Log.info("getPost: Post not found.");
                throw new WebApplicationException(Status.NOT_FOUND);
            }
            Log.info(Status.OK + " : Post retrieved with ID " + postId);
            return post;
        } catch (Exception e) {
            Log.severe("Error retrieving post: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<String> getPostAnswers(String postId) {
        Log.info("getPostAnswers called with postId: " + postId);

        try {
            Post post = hibernate.get(Post.class, postId);
            if (post == null) {
                Log.info("getPostAnswers: Post not found.");
                throw new WebApplicationException(Status.NOT_FOUND);
            }

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
        Log.info("updatePost called with postId: " + postId + " and userPassword: " + userPassword);

        User user = hibernate.get(User.class, post.getAuthorId());

        if (user.getPassword() != userPassword) {
            Log.info("updatePost: Invalid input.");
            throw new WebApplicationException(Status.FORBIDDEN);
        }
        try {
            Post existingPost = hibernate.get(Post.class, postId);
            if (existingPost == null) {
                Log.info("updatePost: Post not found.");
                throw new WebApplicationException(Status.NOT_FOUND);
            }

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
            Log.severe("Error updating post: " + e.getMessage());
            throw new WebApplicationException(Status.BAD_REQUEST);
        }
    }

    @Override
    public void deletePost(String postId, String userPassword) {
        Log.info("deletePost called with postId: " + postId + " and userPassword: " + userPassword);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("deletePost: Post not found.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        User user = hibernate.get(User.class, post.getAuthorId());
        if (user.getPassword() != userPassword) {
            Log.info("deletePost: Invalid input.");
            throw new WebApplicationException(Status.FORBIDDEN);
        }

        try {
            hibernate.delete(post);
            Log.info(Status.NO_CONTENT.toString());
        } catch (Exception e) {
            Log.severe("Error deleting post: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }

    }

    @Override
    public void upVotePost(String postId, String userId, String userPassword) {
        Log.info("upVotePost called with postId: " + postId + " by userId: " + userId);

        try {
            removeVote(postId, userId, userPassword);
            Log.info(Status.NO_CONTENT + " : Upvote removed from post with ID " + postId);
        } catch (Exception e) {
            Log.severe("Error upvoting post: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void removeUpVotePost(String postId, String userId, String userPassword) {
        Log.info("removeUpVotePost called with postId: " + postId + " by userId: " + userId);

        try {
            removeVote(post, userId);
            Log.info(Status.NO_CONTENT + " : Upvote removed from post with ID " + postId);
        } catch (Exception e) {
            Log.severe("Error removing upvote from post: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
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

    /*
     * Generic code for the removing votes
     */
    private void removeVote(String postId, String userId, String userPassword) {
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

        post.removeVote(userId);
        hibernate.update(post);
    }
}
