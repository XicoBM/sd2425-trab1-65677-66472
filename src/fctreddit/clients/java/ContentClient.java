package fctreddit.clients.java;

import fctreddit.api.java.Content;
import fctreddit.api.java.Result;
import fctreddit.api.Post;

import java.util.List;

public abstract class ContentClient implements Content {
    
  	protected static final int READ_TIMEOUT = 5000;
	protected static final int CONNECT_TIMEOUT = 5000;

	protected static final int MAX_RETRIES = 10;
	protected static final int RETRY_SLEEP = 5000;

    public abstract Result<String> createPost(Post post, String userPassword);

    public abstract Result<List<String>> getPosts(long timestamp, String sortOrder);

    public abstract Result<Post> getPost(String postId);

    public abstract Result<List<String>> getPostAnswers(String postId, long maxTimeout);

    public abstract Result<Post> updatePost(String postId, String userPassword, Post post);

    public abstract Result<Void> deletePost(String postId, String userPassword);

    public abstract Result<Void> upVotePost(String postId, String userId, String userPassword);

    public abstract Result<Void> removeUpVotePost(String postId, String userId, String userPassword);

    public abstract Result<Void> downVotePost(String postId, String userId, String userPassword);

    public abstract Result<Void> removeDownVotePost(String postId, String userId, String userPassword);

    public abstract Result<Integer> getUpVotes(String postId);

    public abstract Result<Integer> getDownVotes(String postId);

    public abstract Result<Void> nullifyPostAuthors(String userId);

    public abstract Result<Void> deleteVotesFromUser(String userId);
}
