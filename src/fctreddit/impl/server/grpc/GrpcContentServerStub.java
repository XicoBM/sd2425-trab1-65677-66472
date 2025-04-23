package fctreddit.impl.server.grpc;

import java.util.List;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.StreamObserver;

import fctreddit.api.java.Content;
import fctreddit.api.java.Result;
import fctreddit.api.Post;
import fctreddit.impl.server.java.JavaContent;
import fctreddit.impl.grpc.generated_java.ContentGrpc;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.CreatePostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.CreatePostResult;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.DeletePostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.EmptyMessage;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostAnswersArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostsArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GetPostsResult;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GrpcPost;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.UpdatePostArgs;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.ChangeVoteArgs;
import fctreddit.impl.grpc.util.DataModelAdaptarPosts;

public class GrpcContentServerStub implements ContentGrpc.AsyncService, BindableService {

    Content impl = new JavaContent();

    @Override
    public ServerServiceDefinition bindService() {
        return ContentGrpc.bindService(this);
    }

    @Override
    public void createPost(CreatePostArgs request, StreamObserver<CreatePostResult> responseObserver) {
        Result<String> res = impl.createPost(DataModelAdaptarPosts.GrpcPost_to_Post(request.getPost()),
                request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(CreatePostResult.newBuilder().setPostId(res.value()).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getPosts(GetPostsArgs request, StreamObserver<GetPostsResult> responseObserver) {
        Result<List<String>> res = impl.getPosts(request.getTimestamp(), request.getSortOrder());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(GetPostsResult.newBuilder().addAllPostId(res.value()).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getPost(GetPostArgs request, StreamObserver<GrpcPost> responseObserver) {
        Result<Post> res = impl.getPost(request.getPostId());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(DataModelAdaptarPosts.Post_to_GrpcPost(res.value()));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getPostAnswers(GetPostAnswersArgs request, StreamObserver<GetPostsResult> responseObserver) {
        long Timeout = 3000;
        Result<List<String>> res = impl.getPostAnswers(request.getPostId(), Timeout);
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(GetPostsResult.newBuilder().addAllPostId(res.value()).build());
            responseObserver.onCompleted();
        } 
    }

    @Override
    public void updatePost(UpdatePostArgs request, StreamObserver<GrpcPost> responseObserver) {
        Result<Post> res = impl.updatePost(request.getPostId(), request.getPassword(),
                DataModelAdaptarPosts.GrpcPost_to_Post(request.getPost()));
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(DataModelAdaptarPosts.Post_to_GrpcPost(res.value()));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deletePost(DeletePostArgs request, StreamObserver<EmptyMessage> responseObserver) {
        Result<Void> res = impl.deletePost(request.getPostId(), request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void upVotePost(ChangeVoteArgs request, StreamObserver<EmptyMessage> responseObserver) {
        Result<Void> res = impl.upVotePost(request.getPostId(), request.getUserId(), request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void downVotePost(ChangeVoteArgs request, StreamObserver<EmptyMessage> responseObserver) {
        Result<Void> res = impl.downVotePost(request.getPostId(), request.getUserId(), request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void removeUpVotePost(ChangeVoteArgs request, StreamObserver<EmptyMessage> responseObserver) {
        Result<Void> res = impl.removeUpVotePost(request.getPostId(), request.getUserId(), request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void removeDownVotePost(ChangeVoteArgs request, StreamObserver<EmptyMessage> responseObserver) {
        Result<Void> res = impl.removeDownVotePost(request.getPostId(), request.getUserId(), request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    public void getUpVotePost(GrpcPost request, StreamObserver<EmptyMessage> responseObserver) {
        Result<Integer> res = impl.getupVotes(request.getPostId());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }
    }
    
    public void getDownVotePost(GrpcPost request, StreamObserver<EmptyMessage> responseObserver) {
        Result<Integer> res = impl.getDownVotes(request.getPostId());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(EmptyMessage.newBuilder().build());
            responseObserver.onCompleted();
        }
    }

    protected static Throwable errorCodeToStatus(Result.ErrorCode error) {
        var status = switch (error) {
            case NOT_FOUND -> io.grpc.Status.NOT_FOUND;
            case CONFLICT -> io.grpc.Status.ALREADY_EXISTS;
            case FORBIDDEN -> io.grpc.Status.PERMISSION_DENIED;
            case NOT_IMPLEMENTED -> io.grpc.Status.UNIMPLEMENTED;
            case BAD_REQUEST -> io.grpc.Status.INVALID_ARGUMENT;
            default -> io.grpc.Status.INTERNAL;
        };

        return status.asException();
    }
}
