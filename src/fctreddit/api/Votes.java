package fctreddit.api;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Votes {
    
    public static final String VOTE_UP = "up";
    public static final String VOTE_DOWN = "down";

    @Id
    private String voteId;
    private String postId;
    private String userId;
    private String voteType;


    public Votes() {

    }

    public Votes(String postId, String userId, String voteType) {
        this.voteId = UUID.randomUUID().toString();
        this.postId = postId;
        this.userId = userId;
        this.voteType = voteType;
    }

    public String getVoteId() {
        return voteId;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getVoteType() {
        return voteType;
    }

    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }
}
