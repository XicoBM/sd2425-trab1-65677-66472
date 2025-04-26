package fctreddit.clients.grpc;

import java.net.URI;
import java.util.List;

import fctreddit.api.java.Result;
import fctreddit.api.Post;
import fctreddit.impl.grpc.generated_java.ContentGrpc;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.CreatePostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.CreatePostResult;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostsArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostsResult;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostAnswersArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.UpdatePostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.DeletePostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.DeleteVotesArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.ChangeVoteArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.VoteCountResult;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.NullifyAuthorsArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GrpcPost;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.clients.java.ContentClient;
import fctreddit.impl.grpc.util.DataModelAdaptorPosts;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;

public class GrpcContentClient extends ContentClient {

    static {
        LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
    }
    
    final ContentGrpc.ContentBlockingStub stub;

    public GrpcContentClient(URI serverURI) {
        Channel channel = ManagedChannelBuilder.forAddress(serverURI.getHost(), serverURI.getPort()).enableRetry().usePlaintext().build();
        stub = ContentGrpc.newBlockingStub(channel);
    }

    @Override
    public Result<String> createPost(Post post, String password) {
        try {
            CreatePostResult res = stub.createPost(CreatePostArgs.newBuilder()
                    .setPost(DataModelAdaptorPosts.Post_to_GrpcPost(post))
                    .setPassword(password)
                    .build());
            
            return Result.ok(res.getPostId());
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<List<String>> getPosts(long timestamp, String sortOrder) {
        try {
            GetPostsArgs.Builder argsBuilder = GetPostsArgs.newBuilder();
            if (timestamp != 0) {
                argsBuilder.setTimestamp(timestamp);
            }
            if (sortOrder != null && !sortOrder.isEmpty()) {
                argsBuilder.setSortOrder(sortOrder);
            }
            GetPostsResult res = stub.getPosts(argsBuilder.build());
            return Result.ok(res.getPostIdList());
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Post> getPost(String postId) {
        try {
            GrpcPost res = stub.getPost(GetPostArgs.newBuilder()
                    .setPostId(postId)
                    .build());
            
            return Result.ok(DataModelAdaptorPosts.GrpcPost_to_Post(res));
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<List<String>> getPostAnswers(String postId, long timeout) {
        try {
            GetPostAnswersArgs args = GetPostAnswersArgs.newBuilder()
                    .setPostId(postId)
                    .setTimeout(timeout)
                    .build();
    
            GetPostsResult res = stub.getPostAnswers(args);
            return Result.ok(res.getPostIdList());
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }
    

    @Override
    public Result<Post> updatePost(String postId, String password, Post post) {
        try {
            UpdatePostArgs args = UpdatePostArgs.newBuilder()
                    .setPostId(postId)
                    .setPassword(password)
                    .setPost(DataModelAdaptorPosts.Post_to_GrpcPost(post))
                    .build();
            GrpcPost res = stub.updatePost(args);
            
            return Result.ok(DataModelAdaptorPosts.GrpcPost_to_Post(res));
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Void> deletePost(String postId, String password) {
        try {
            stub.deletePost(DeletePostArgs.newBuilder()
                    .setPostId(postId)
                    .setPassword(password)
                    .build());
            
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Void> upVotePost(String postId, String userId, String password) {
        try {
            stub.upVotePost(ChangeVoteArgs.newBuilder()
                    .setPostId(postId)
                    .setUserId(userId)
                    .setPassword(password)
                    .build());
            
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Void> downVotePost(String postId, String userId, String password) {
        try {
            stub.downVotePost(ChangeVoteArgs.newBuilder()
                    .setPostId(postId)
                    .setUserId(userId)
                    .setPassword(password)
                    .build());
            
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Void> removeUpVotePost(String postId, String userId, String password) {
        try {
            stub.removeUpVotePost(ChangeVoteArgs.newBuilder()
                    .setPostId(postId)
                    .setUserId(userId)
                    .setPassword(password)
                    .build());
            
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Void> removeDownVotePost(String postId, String userId, String password) {
        try {
            stub.removeDownVotePost(ChangeVoteArgs.newBuilder()
                    .setPostId(postId)
                    .setUserId(userId)
                    .setPassword(password)
                    .build());
            
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Integer> getUpVotes(String postId) {
        try {
            VoteCountResult res = stub.getUpVotes(GetPostArgs.newBuilder()
                    .setPostId(postId)
                    .build());
            
            return Result.ok(res.getCount());
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Integer> getDownVotes(String postId) {
        try {
            VoteCountResult res = stub.getDownVotes(GetPostArgs.newBuilder()
                    .setPostId(postId)
                    .build());
            
            return Result.ok(res.getCount());
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Void> deleteVotesFromUser(String userId) {
        try {
            stub.deleteVotesFromUser(DeleteVotesArgs.newBuilder()
                    .setUserId(userId)
                    .build());
            
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    @Override
    public Result<Void> nullifyPostAuthors(String userId) {
        try {
            stub.nullifyPostAuthors(NullifyAuthorsArgs.newBuilder()
                    .setUserId(userId)
                    .build());
            
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    static ErrorCode statusToErrorCode(Status status) {
        return switch (status.getCode()) {
            case OK -> ErrorCode.OK;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case ALREADY_EXISTS -> ErrorCode.CONFLICT;
            case PERMISSION_DENIED -> ErrorCode.FORBIDDEN;
            case INVALID_ARGUMENT -> ErrorCode.BAD_REQUEST;
            case UNIMPLEMENTED -> ErrorCode.NOT_IMPLEMENTED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }


}
