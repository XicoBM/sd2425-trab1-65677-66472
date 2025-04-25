package fctreddit.impl.server.java;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.hibernate.Session;

import fctreddit.api.java.Content;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.discovery.Discovery;
import fctreddit.impl.server.persistence.Hibernate;
import fctreddit.api.Post;
import fctreddit.api.User;
import fctreddit.api.Votes;

import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

public class JavaContent implements Content {
    private static Logger Log = Logger.getLogger(JavaContent.class.getName());
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int REPLY_TIMEOUT = 3000;
    private static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

    private final Discovery discovery;

    {
        Discovery tempDiscovery = null;
        try {
            tempDiscovery = new Discovery(DISCOVERY_ADDR);
        } catch (IOException e) {
            Log.severe("Failed to initialize Discovery: " + e.getMessage());
        }
        discovery = tempDiscovery;
    }
    private final Client client;

    private Hibernate hibernate;

    public JavaContent() {
        hibernate = Hibernate.getInstance();
        ClientConfig config = new ClientConfig();
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMEOUT);
        this.client = ClientBuilder.newClient(config);
    }

    private User getUser(String userId) {
        try {
            List<String> usersServiceUris = discovery.knownUrisAsStringsOf("Users", 1);
            if (usersServiceUris.isEmpty()) {
                return null;
            }

            String usersUri = usersServiceUris.get(0) + "/users/" + userId + "/aux";

            WebTarget target = client.target(usersUri);

            Response r = target.request().accept(MediaType.APPLICATION_JSON).get();

            if (r.getStatus() == 200) {
                return r.readEntity(User.class);
            } else {
                Log.warning("Failed to get user. Status: " + r.getStatus());
            }
        } catch (Exception e) {
            Log.severe("Exception while contacting Users service: " + e.getMessage());
        }

        return null;
    }

    private String getPostUrl(String postId) {
        try {
            List<String> postsServiceUris = discovery.knownUrisOf("Posts");
            if (postsServiceUris.isEmpty()) {
                return null;
            }

            String postsUri = postsServiceUris.get(0) + "/posts/" + postId;

            WebTarget target = client.target(postsUri);

            Response r = target.request().accept(MediaType.APPLICATION_JSON).get();

            if (r.getStatus() == 200) {
                return r.readEntity(String.class);
            } else {
                Log.warning("Failed to get post URL. Status: " + r.getStatus());
            }
        } catch (Exception e) {
            Log.severe("Exception while contacting Posts service: " + e.getMessage());
        }

        return null;
    }

    private String getPostIdByUrl(String postUrl) {
        try {
            List<String> postsServiceUris = discovery.knownUrisOf("Posts");
            if (postsServiceUris.isEmpty()) {
                return null;
            }

            String postsUri = postsServiceUris.get(0) + "/posts/" + postUrl;

            WebTarget target = client.target(postsUri);

            Response r = target.request().accept(MediaType.APPLICATION_JSON).get();

            if (r.getStatus() == 200) {
                return r.readEntity(String.class);
            } else {
                Log.warning("Failed to get post ID by URL. Status: " + r.getStatus());
            }
        } catch (Exception e) {
            Log.severe("Exception while contacting Posts service: " + e.getMessage());
        }

        return null;
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
public Result<List<String>> getPostAnswers(String postId, long maxTimeout) {
    Log.info("getPostAnswers called with postId: " + postId);

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

        // Agora buscamos todos os posts que têm o parentUrl igual ao URL do post pai
        TypedQuery<String> query = session.createQuery(
            "SELECT p.postId FROM Post p WHERE p.parentUrl = :parentUrl", 
            String.class
        );
        query.setParameter("parentUrl", parentUrl); // Comparar com o URL completo do post pai

        List<String> result = query.getResultList();
        Log.info("Returning " + result.size() + " answers for postId: " + postId);

        return Result.ok(result);
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
        User user = hibernate.get(User.class, existingPost.getAuthorId());
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
            return Result.error(ErrorCode.CONFLICT);
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

        Post post = getPost(postId).value();
        if (post == null) {
            Log.info("deletePost: Post not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        User author = hibernate.get(User.class, post.getAuthorId());
        if (!author.getPassword().equals(userPassword)) {
            Log.info("deletePost: Invalid password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        List<String> postIds = hibernate.jpql("SELECT p.postId FROM Post p WHERE p.parentUrl = :parentUrl", String.class);
        for (String ids : postIds) {
            /**
            Post var = contentResources.getPost(ids);
            if (var.getParentUrl() == post.getMediaUrl()) {
                Log.info("deletePost: Cannot delete post with answers.");
                return Result.error(ErrorCode.CONFLICT);
            }*/
        }

        if (post.getUpVote() != 0 || post.getDownVote() != 0) {
            Log.info("deletePost: Cannot delete post with votes.");
            return Result.error(ErrorCode.CONFLICT);
        }
        TypedQuery<Post> query = hibernate.jpql2("SELECT p FROM Post p WHERE p.parentUrl = :parentUrl", Post.class);
        String parentUrl = getPostUrl(postId);
        query.setParameter("parentUrl", parentUrl);
        List<Post> posts = query.getResultList();
        for (Post p : posts) {
            hibernate.delete(p);
        }
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
