package fctreddit.impl.server.java;

import java.util.List;
import java.util.logging.Logger;

import fctreddit.api.java.Content;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.rest.ContentResources;
import fctreddit.impl.server.persistence.Hibernate;
import fctreddit.api.Post;
import fctreddit.api.User;
import fctreddit.api.Votes;

import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;



public class JavaContent implements Content {
    private static Logger Log = Logger.getLogger(JavaContent.class.getName());

    private ContentResources contentResources;
    private Hibernate hibernate;

    public JavaContent() {
        hibernate = Hibernate.getInstance();
        contentResources = new ContentResources();
        }

    @Override
    public Result<String> createPost(Post post, String userPassword) {
        Log.info("createPost called with userId: " + post.getAuthorId());
        User user = hibernate.get(User.class, post.getAuthorId());

        if (post.getAuthorId() == null) {
            Log.info("createPost: Invalid input.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("createPost: Invalid input.");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        try {
            hibernate.persist(post);
            String postId = post.getPostId();
            return Result.ok(postId);
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("createPost: Failed to write post.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    public Result<List<String>> getPosts(long timestamp, String sortOrder) {
        Log.info("getPosts called with timestamp: " + timestamp + " and sortOrder: " + sortOrder);

        try {
            String query = "SELECT p.postId FROM Post p WHERE p.parentId IS NULL";

            if (timestamp > 0) {
                query += " AND p.creationTimestamp >= :timestamp";
            }
            if (sortOrder != null) {
                switch (sortOrder) {
                    case Content.MOST_UP_VOTES:
                        query += " ORDER BY p.upVote DESC";
                        break;
                    case Content.MOST_REPLIES:
                        query += " ORDER BY SIZE(p.replies) DESC";
                        break;
                    default:
                        Log.warning("Invalid sortOrder: " + sortOrder);
                        throw new WebApplicationException(Status.BAD_REQUEST);
                }
            }
            List<String> res = hibernate.jpql(query, String.class);
            return Result.ok(res);
        } catch (Exception e) {
            Log.severe("Error retrieving posts: " + e.getMessage());
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Result<Post> getPost(String postId) {
        Log.info("getPost called with postId: " + postId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("getPost: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        Log.info("getPost: Retrieved post with ID " + postId);
        return Result.ok(post);
    }

    @Override
    public Result<List<String>> getPostAnswers(String postId) {
        Log.info("getPostAnswers called with postId: " + postId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("getPostAnswers: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        TypedQuery<String> query = hibernate.jpql2("SELECT p.postId FROM Post p WHERE p.parentId = :parentId",
                String.class);
        List<String> res = query.setParameter("parentId", postId).getResultList();
        return Result.ok(res);
    }

    @Override
    public Result<Post> updatePost(String postId, String userPassword, Post post) {
        Log.info("updatePost called with postId: " + postId + " and userPassword: " + userPassword);

        Post existingPost = hibernate.get(Post.class, postId);
        User user = hibernate.get(User.class, existingPost.getAuthorId());
        if (!user.getPassword().equals(userPassword)) {
            Log.info("updatePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
        try {
            hibernate.delete(existingPost);
            hibernate.persist(post);
            Log.info("updatePost: Updated post with ID " + postId);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("updatePost: Failed to update post.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    public Result<Void> deletePost(String postId, String userPassword) {
        Log.info("deletePost called with postId: " + postId + " and userPassword: " + userPassword);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("deletePost: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        User author = hibernate.get(User.class, post.getAuthorId());
        if (!author.getPassword().equals(userPassword)) {
            Log.info("deletePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
        List<String> postIds = hibernate.jpql("SELECT p.postId FROM Post p WHERE p.parentId = :parentId", String.class);
        for (String ids : postIds) {
            Post var = contentResources.getPost(ids);
            if (var.getParentUrl() == post.getMediaUrl()) {
                Log.info("deletePost: Cannot delete post with answers.");
                return Result.error(ErrorCode.CONFLICT);
            }
        }
        if (post.getUpVote() != 0 || post.getDownVote() != 0) {
            Log.info("deletePost: Cannot delete post with votes.");
            return Result.error(ErrorCode.CONFLICT);
        }
        contentResources.deletePost(postId, userPassword);
        Log.info("deletePost: Deleted post with ID " + postId);
        return Result.ok();
    }

    @Override
    public Result<Void> upVotePost(String postId, String userId, String userPassword) {
        Log.info("upVotePost called with postId: " + postId + " and userId: " + userId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("upVotePost: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        User user = hibernate.get(User.class, userId);
        if (user == null) {
            Log.info("upVotePost: User not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("upVotePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
        TypedQuery<Votes> query = hibernate.jpql2(
                "SELECT v FROM Votes v WHERE v.postId = :postId AND v.userId = :userId",
                Votes.class);
        query.setParameter("postId", postId);
        query.setParameter("userId", userId);
        Votes userVote = query.getSingleResult();
        if (userVote != null) {
            Log.info("upVotePost: User already voted.");
            return Result.error(ErrorCode.CONFLICT);
        }
        try {
            Votes vote = new Votes(postId, userId, Votes.VOTE_UP);
            hibernate.persist(vote);
            post.setUpVote(post.getUpVote() + 1);
            updatePost(postId, userPassword, post);
            Log.info("upVotePost: Added upvote to post with ID " + postId);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("upVotePost: Failed to add upvote.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    public Result<Void> removeUpVotePost(String postId, String userId, String userPassword) {
        Log.info("removeUpVotePost called with postId: " + postId + " and userId: " + userId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("removeUpVotePost: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        User user = hibernate.get(User.class, userId);
        if (user == null) {
            Log.info("removeUpVotePost: User not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("removeUpVotePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
        TypedQuery<Votes> query = hibernate.jpql2(
                "SELECT v FROM Votes v WHERE v.postId = :postId AND v.userId = :userId",
                Votes.class);
        query.setParameter("postId", postId);
        query.setParameter("userId", userId);
        Votes userVote = query.getSingleResult();
        if (userVote.getUserId().equals(userId)) {
            if (userVote.getVoteType() != Votes.VOTE_UP) {
                Log.info("removeUpVotePost: User did not cast an upvote.");
                return Result.error(ErrorCode.CONFLICT);
            }
        }
        try {
            hibernate.delete(userVote);
            post.setUpVote(post.getUpVote() - 1);
            updatePost(postId, userPassword, post);
            Log.info("removeUpVotePost: Removed upvote from post with ID " + postId);
            return Result.ok();
        } catch (NoResultException e) {
            Log.info("removeUpVotePost: User did not vote.");
            return Result.error(ErrorCode.CONFLICT);
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("removeUpVotePost: Failed to remove upvote.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    public Result<Void> downVotePost(String postId, String userId, String userPassword) {
        Log.info("downVotePost called with postId: " + postId + " and userId: " + userId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("downVotePost: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        User user = hibernate.get(User.class, userId);
        if (user == null) {
            Log.info("downVotePost: User not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("downVotePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
        TypedQuery<Votes> query = hibernate.jpql2(
                "SELECT v FROM Votes v WHERE v.postId = :postId AND v.userId = :userId",
                Votes.class);
        query.setParameter("postId", postId);
        query.setParameter("userId", userId);
        Votes userVote = query.getSingleResult();
        if (userVote != null) {
            Log.info("downVotePost: User already voted.");
            return Result.error(ErrorCode.CONFLICT);
        }
        try {
            Votes vote = new Votes(postId, userId, Votes.VOTE_DOWN);
            hibernate.persist(vote);
            post.setDownVote(post.getDownVote() + 1);
            updatePost(postId, userPassword, post);
            Log.info("downVotePost: Added downvote to post with ID " + postId);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("downVotePost: Failed to add downvote.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    public Result<Void> removeDownVotePost(String postId, String userId, String userPassword) {
        Log.info("removeDownVotePost called with postId: " + postId + " and userId: " + userId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("removeDownVotePost: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        User user = hibernate.get(User.class, userId);
        if (user == null) {
            Log.info("removeDownVotePost: User not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        if (!user.getPassword().equals(userPassword)) {
            Log.info("removeDownVotePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
        TypedQuery<Votes> query = hibernate.jpql2(
                "SELECT v FROM Votes v WHERE v.postId = :postId AND v.userId = :userId",
                Votes.class);
        query.setParameter("postId", postId);
        query.setParameter("userId", userId);
        Votes userVote = query.getSingleResult();
        if (userVote.getUserId().equals(userId)) {
            if (userVote.getVoteType() != Votes.VOTE_DOWN) {
                Log.info("removeDownVotePost: User did not cast a downvote.");
                return Result.error(ErrorCode.CONFLICT);
            }
        }
        try {
            hibernate.delete(userVote);
            post.setDownVote(post.getDownVote() - 1);
            updatePost(postId, userPassword, post);
            Log.info("removeDownVotePost: Removed downvote from post with ID " + postId);
            return Result.ok();
        } catch (NoResultException e) {
            Log.info("removeDownVotePost: User did not vote.");
            return Result.error(ErrorCode.CONFLICT);
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("removeDownVotePost: Failed to remove downvote.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    public Result<Integer> getupVotes(String postId) {
        Log.info("getupVotes called with postId: " + postId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("getupVotes: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        try {
            int upVotes = post.getUpVote();
            Log.info("getupVotes: Retrieved upvotes for post with ID " + postId);
            return Result.ok(upVotes);
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("getupVotes: Failed to retrieve upvotes.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    public Result<Integer> getDownVotes(String postId) {
        Log.info("getDownVotes called with postId: " + postId);

        Post post = hibernate.get(Post.class, postId);
        if (post == null) {
            Log.info("getDownVotes: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        try {
            int downVotes = post.getDownVote();
            Log.info("getDownVotes: Retrieved downvotes for post with ID " + postId);
            return Result.ok(downVotes);
        } catch (Exception e) {
            e.printStackTrace();
            Log.info("getDownVotes: Failed to retrieve downvotes.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

}
