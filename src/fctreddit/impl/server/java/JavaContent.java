package fctreddit.impl.server.java;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.hibernate.Session;

import fctreddit.api.java.Content;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.clients.rest.RestImagesClient;
import fctreddit.clients.rest.RestUsersClient;
import fctreddit.impl.server.discovery.Discovery;
import fctreddit.impl.server.persistence.Hibernate;
import fctreddit.api.Post;
import fctreddit.api.User;
import fctreddit.api.Votes;

import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;



public class JavaContent implements Content {
    private static Logger Log = Logger.getLogger(JavaContent.class.getName());

    private final Discovery discovery = Discovery.getInstance();

    private final RestUsersClient restUsersClient; 
    private final RestImagesClient restImagesClient; 

    private Hibernate hibernate;

public JavaContent() {
    this.hibernate = Hibernate.getInstance();

    List<String> usersServiceUris = discovery.knownUrisOf("Users");
    if (usersServiceUris == null || usersServiceUris.isEmpty()) {
        throw new IllegalStateException("No known URIs for Users service found");
    }
    URI usersUri = URI.create(usersServiceUris.get(0));
    this.restUsersClient = new RestUsersClient(usersUri);
    Log.info("RestUsersClient created with URI: " + usersUri);

    List<String> imagesServiceUris = discovery.knownUrisOf("Image");
    if (imagesServiceUris == null || imagesServiceUris.isEmpty()) {
        throw new IllegalStateException("No known URIs for Images service found");
    }
    URI imagesUri = URI.create(imagesServiceUris.get(0));
    this.restImagesClient = new RestImagesClient(imagesUri);  
    Log.info("RestImagesClient created with URI: " + imagesUri);
}

    private User getUser(String userId) {
    Result<User> result = restUsersClient.getUserAux(userId);

    if (result == null || !result.isOK()) {
        if (result != null && result.error() == ErrorCode.NOT_FOUND) {
            Log.info("User does not exist.");
        } else {
            Log.severe("Failed to retrieve user with ID: " + userId);
        }
        return null;
    }

    return result.value();
}   

    
    private void deleteImage(String imageId, String userId, String password) {
        Result<Void> result = restImagesClient.deleteImage(userId, imageId, password);

        if (result == null || !result.isOK()) {
            if (result != null && result.error() == ErrorCode.NOT_FOUND) {
                Log.info("Image does not exist.");
            } else {
                Log.severe("Failed to delete image with ID: " + imageId);
            }
        } else {
            Log.info("Image deleted successfully.");
        }
    }

@Override
public Result<String> createPost(Post post, String userPassword) {
    Log.info("createPost called with userId: " + post.getAuthorId());
    User user = getUser(post.getAuthorId());
    if (user == null) {
        Log.info("createPost: User not found.");
        return Result.error(ErrorCode.NOT_FOUND);
    }
    if (!user.getPassword().equals(userPassword)) {
        Log.info("createPost: Invalid input.");
        return Result.error(ErrorCode.FORBIDDEN);
    }
    if (post.getParentUrl() != null) {
        String parentId = post.getParentUrl().substring(post.getParentUrl().lastIndexOf('/') + 1);
        Post parentPost = hibernate.get(Post.class, parentId);
        if (parentPost == null) {
            Log.info("createPost: Parent post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
    }
    
    try {
        String postId = UUID.randomUUID().toString();
        post.setPostId(postId); 
        hibernate.persist(post);
        return Result.ok(postId);
    } catch (Exception e) {
        e.printStackTrace();
        Log.info("createPost: Failed to write post.");
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }
}


@Override
public Result<List<String>> getPosts(long timestamp, String sortOrder) {
    Log.info("getPosts called with timestamp: " + timestamp + " and sortOrder: " + sortOrder);

    try {
        String query = "SELECT p.postId FROM Post p WHERE p.parentUrl IS NULL";

        if (timestamp > 0) {
            query += " AND p.creationTimestamp >= :timestamp";
        }

        List<String> postIds = hibernate.jpql(query, String.class);

        if (sortOrder != null) {
            switch (sortOrder) {
                case Content.MOST_UP_VOTES:
                    query += " ORDER BY p.upVote DESC";
                    postIds = hibernate.jpql(query, String.class);
                    break;

                case Content.MOST_REPLIES:
                    long maxTimeout = 0;
                    postIds.sort((a, b) -> {
                        List<String> answersB = getPostAnswers(b, maxTimeout).value();
                        List<String> answersA = getPostAnswers(a, maxTimeout).value();
                        return Integer.compare(answersB.size(), answersA.size());
                    });                    
                    break;

                default:
                    Log.warning("Invalid sortOrder: " + sortOrder);
                    throw new WebApplicationException(Status.BAD_REQUEST);
            }
        }

        return Result.ok(postIds);

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
    public Result<List<String>> getPostAnswers(String postId, long timeout) {
        Log.info("getPostAnswers called with postId: " + postId + ", timeout: " + timeout);
    
        if (postId == null || postId.trim().isEmpty()) {
            Log.info("getPostAnswers: Invalid postId.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    
        try (Session session = Hibernate.getInstance().sessionFactory.openSession()) {
            Post parentPost = session.get(Post.class, postId);
            if (parentPost == null) {
                Log.info("getPostAnswers: Post not found.");
                return Result.error(ErrorCode.NOT_FOUND);
            }
    
            String serverIp = InetAddress.getLocalHost().getHostAddress();
            String parentUrl = String.format("http://%s:8081/rest/posts/%s", serverIp, postId);
    
            TypedQuery<String> query = session.createQuery(
                "SELECT p.postId FROM Post p WHERE p.parentUrl = :parentUrl",
                String.class
            );
            query.setParameter("parentUrl", parentUrl);
    
            List<String> initialAnswers = query.getResultList();
            if (timeout <= 0) {
                return Result.ok(initialAnswers);
            }
    
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeout) {
                Thread.sleep(1000); 
    
                List<String> currentAnswers = session.createQuery(
                    "SELECT p.postId FROM Post p WHERE p.parentUrl = :parentUrl",
                    String.class
                ).setParameter("parentUrl", parentUrl).getResultList();
    
                if (currentAnswers.size() > initialAnswers.size()) {
                    return Result.ok(currentAnswers);
                }
            }
    
            return Result.ok(initialAnswers);
    
        } catch (Exception e) {
            Log.severe("getPostAnswers: Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }
    


    @Override
    public Result<Post> updatePost(String postId, String userPassword, Post post) {
        Log.info("updatePost called with postId: " + postId + " and userPassword: " + userPassword);

        Post existingPost = hibernate.get(Post.class, postId);
        User user = getUser(existingPost.getAuthorId());
        if (!user.getPassword().equals(userPassword)) {
            Log.info("updatePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
        if (existingPost.getUpVote() > 0 || existingPost.getDownVote() > 0) {
            Log.info("updatePost: Post has votes. Update not allowed.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        Result<List<String>> answersResult = getPostAnswers(postId, 0);
        if (answersResult.isOK() && !answersResult.value().isEmpty()) {
            Log.info("updatePost: Post has answers. Update not allowed.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        try {
            if (post.getContent() != null) {
                existingPost.setContent(post.getContent());
            }
            if (post.getMediaUrl() != null) {
                existingPost.setMediaUrl(post.getMediaUrl());
            }
            hibernate.update(existingPost);
            Log.info("updatePost: Updated post with ID " + postId);
            return Result.ok(existingPost);
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
    
        User author = getUser(post.getAuthorId());
        if (author == null || !author.getPassword().equals(userPassword)) {
            Log.info("deletePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }
    
        if (post.getUpVote() != 0 || post.getDownVote() != 0) {
            Log.info("deletePost: Cannot delete post with votes.");
            return Result.error(ErrorCode.CONFLICT);
        }

        String mediaUrl = null; 
        String id = null;
        if (post.getMediaUrl() !=null){
            mediaUrl = post.getMediaUrl(); 
            id = mediaUrl.substring(mediaUrl.lastIndexOf('/') + 1);
            String userId = author.getUserId();
            deleteImage(id, userId, userPassword);  
        } 
            
        deletePostRecursively(postId);
    
        Log.info("deletePost: Deleted post with ID " + postId);
        return Result.ok();
    }
    
    private void deletePostRecursively(String postId) {
        Result<List<String>> repliesResult = getPostAnswers(postId, 0);
        if (repliesResult.isOK()) {
            List<String> replies = repliesResult.value();
            for (String replyId : replies) {
                deletePostRecursively(replyId);
            }
        }
    
        try (Session session = Hibernate.getInstance().sessionFactory.openSession()) {
            session.beginTransaction();
            List<Votes> votes = session.createQuery(
                "SELECT v FROM Votes v WHERE v.postId = :postId", Votes.class)
                .setParameter("postId", postId)
                .getResultList();
            for (Votes v : votes) {
                session.remove(v);
            }
            session.getTransaction().commit();
        } catch (Exception e) {
            Log.warning("Failed to delete votes for postId: " + postId + " - " + e.getMessage());
        }
    
        Post toDelete = hibernate.get(Post.class, postId);
        if (toDelete != null) {
            hibernate.delete(toDelete);
        }
    }
    

    @Override
    public Result<Void> upVotePost(String postId, String userId, String userPassword) {
        Log.info("upVotePost called with postId: " + postId + " and userId: " + userId);
    
        if (postId == null || userId == null || userPassword == null) {
            Log.info("upVotePost: Invalid input.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    
        try (Session session = Hibernate.getInstance().sessionFactory.openSession()) {
    
            session.beginTransaction();
    
            Post post = session.get(Post.class, postId);
            if (post == null) {
                Log.info("upVotePost: Post not found.");
                return Result.error(ErrorCode.NOT_FOUND);
            }
    
            User user = getUser(userId);
            if (user == null) {
                Log.info("upVotePost: User not found.");
                return Result.error(ErrorCode.NOT_FOUND);
            }
    
            if (!user.getPassword().equals(userPassword)) {
                Log.info("upVotePost: Invalid password.");
                return Result.error(ErrorCode.FORBIDDEN);
            }
    
            TypedQuery<Votes> query = session.createQuery(
                "SELECT v FROM Votes v WHERE v.postId = :postId AND v.userId = :userId",
                Votes.class
            );
            query.setParameter("postId", postId);
            query.setParameter("userId", userId);
    
            if (!query.getResultList().isEmpty()) {
                Log.info("upVotePost: User already voted.");
                return Result.error(ErrorCode.CONFLICT);
            }
    
            Votes vote = new Votes(postId, userId, Votes.VOTE_UP);
            session.persist(vote);
            post.setUpVote(post.getUpVote() + 1);
            session.getTransaction().commit();
    
            Log.info("upVotePost: Added upvote to post with ID " + postId);
            return Result.ok();
    
        } catch (PersistenceException e) {
            Log.severe("Error in upVotePost: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
        } catch (Exception e) {
            Log.severe("Unexpected error in upVotePost: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
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
    
        if (postId == null || userId == null || userPassword == null) {
            Log.info("downVotePost: Invalid input.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    
        try (Session session = Hibernate.getInstance().sessionFactory.openSession()) {
    
            session.beginTransaction();
    
            Post post = session.get(Post.class, postId);
            if (post == null) {
                Log.info("downVotePost: Post not found.");
                return Result.error(ErrorCode.NOT_FOUND);
            }
    
            User user = getUser(userId);
            if (user == null) {
                Log.info("downVotePost: User not found.");
                return Result.error(ErrorCode.NOT_FOUND);
            }
    
            if (!user.getPassword().equals(userPassword)) {
                Log.info("downVotePost: Invalid password.");
                return Result.error(ErrorCode.FORBIDDEN);
            }
            
            TypedQuery<Votes> query = session.createQuery(
                "SELECT v FROM Votes v WHERE v.postId = :postId AND v.userId = :userId",
                Votes.class
            );
            query.setParameter("postId", postId);
            query.setParameter("userId", userId);
    
            if (!query.getResultList().isEmpty()) {
                Log.info("downVotePost: User already voted.");
                return Result.error(ErrorCode.CONFLICT);
            }
    
            Votes vote = new Votes(postId, userId, Votes.VOTE_DOWN);
            session.persist(vote);
            post.setDownVote(post.getDownVote() + 1);
            session.getTransaction().commit();    
            Log.info("downVotePost: Added downvote to post with ID " + postId);
            return Result.ok();
    
        } catch (PersistenceException e) {
            Log.severe("Error in downVotePost: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
        } catch (Exception e) {
            Log.severe("Unexpected error in downVotePost: " + e.getMessage());
            return Result.error(ErrorCode.INTERNAL_ERROR);
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

    @Override
    public Result<Void> deleteVotesFromUser(String userId) {
        Log.info("==================== deleteVotesFromUser called with userId: " + userId + " ====================");
    
        if (userId == null || userId.trim().isEmpty()) {
            Log.warning("==================== deleteVotesFromUser: Invalid userId provided ====================");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    
        try (Session session = Hibernate.getInstance().sessionFactory.openSession()) {
            List<Votes> votes = session.createQuery(
                    "FROM Votes v WHERE v.userId = :userId", Votes.class
                )
                .setParameter("userId", userId)
                .getResultList();
    
            if (votes.isEmpty()) {
                Log.info("==================== deleteVotesFromUser: No votes found for user with ID " + userId + " ====================");
                return Result.ok();
            }
    
            session.beginTransaction();
    
            for (Votes vote : votes) {
                session.remove(vote);
            }

            for (Votes vote : votes) {
                Post post = session.get(Post.class, vote.getPostId());
                if (vote.getVoteType().equals(Votes.VOTE_UP)) {
                    post.setUpVote(post.getUpVote() - 1);
                } else if (vote.getVoteType().equals(Votes.VOTE_DOWN)) {
                    post.setDownVote(post.getDownVote() - 1);
                }
            }
    
            session.getTransaction().commit();
    
            Log.info("==================== deleteVotesFromUser: Deleted " + votes.size() + " votes for user with ID " + userId + " ====================");
            return Result.ok();
    
        } catch (PersistenceException e) {
            Log.severe("==================== Persistence error in deleteVotesFromUser: " + e.getMessage() + " ====================");
            return Result.error(ErrorCode.INTERNAL_ERROR);
        } catch (Exception e) {
            Log.severe("==================== Unexpected error in deleteVotesFromUser ====================");
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }
    

    @Override
    public Result<Void> nullifyPostAuthors(String userId) {

    if (userId == null || userId.trim().isEmpty()) {
        return Result.error(ErrorCode.BAD_REQUEST);
    }

    try (Session session = Hibernate.getInstance().sessionFactory.openSession()) {
        List<Post> posts = session.createQuery(
                "FROM Post p WHERE p.authorId = :userId", Post.class
            )
            .setParameter("userId", userId)
            .getResultList();

        if (posts.isEmpty()) {
            Log.info("nullifyPostAuthors: No posts found for user with ID " + userId);
            return Result.ok();
        }

        session.beginTransaction();

        for (Post post : posts) {
            post.setAuthorId(null);
        }

        session.getTransaction().commit();

        Log.info("nullifyPostAuthors: Nullified authorId for " + posts.size() + " posts by user with ID " + userId);
        return Result.ok();

    } catch (PersistenceException e) {
        Log.severe("Persistence error in nullifyPostAuthors: " + e.getMessage());
        return Result.error(ErrorCode.INTERNAL_ERROR);
    } catch (Exception e) {
        Log.severe("Unexpected error in nullifyPostAuthors");
        e.printStackTrace();
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }
}

    

}
