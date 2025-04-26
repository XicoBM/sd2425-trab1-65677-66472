package fctreddit.clients.grpc;

import java.net.URI;
import java.util.Iterator;

import fctreddit.api.java.Result;
import fctreddit.impl.grpc.generated_java.ImageGrpc;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.CreateImageArgs;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.CreateImageResult;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.GetImageArgs;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.GetImageResult;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.DeleteImageArgs;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.clients.java.ImageClient;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.PickFirstLoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;

public class GrpcImagesClient extends ImageClient {

    static {
        LoadBalancerRegistry.getDefaultRegistry().register(new PickFirstLoadBalancerProvider());
    }
    
    private final ImageGrpc.ImageBlockingStub stub;

    public GrpcImagesClient(URI serverURI) {
        Channel channel = ManagedChannelBuilder.forAddress(serverURI.getHost(), serverURI.getPort()).enableRetry().usePlaintext().build();
        stub = ImageGrpc.newBlockingStub(channel);
    }
    

    public Result<String> createImage(String userId, byte[] imageContents, String password) {
        try {
            CreateImageResult res = stub.createImage(CreateImageArgs.newBuilder()
                    .setUserId(userId)
                    .setImageContents(com.google.protobuf.ByteString.copyFrom(imageContents))
                    .setPassword(password)
                    .build());
            return Result.ok(res.getImageId());
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    public Result<byte[]> getImage(String userId, String imageId) {
        try {
            Iterator<GetImageResult> res = stub.getImage(GetImageArgs.newBuilder()
                    .setUserId(userId)
                    .setImageId(imageId)
                    .build());
            return Result.ok(res.next().getData().toByteArray());
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
        
    }

    public Result<Void> deleteImage(String userId, String imageId, String password) {
        try {
            stub.deleteImage(DeleteImageArgs.newBuilder()
                    .setUserId(userId)
                    .setImageId(imageId)
                    .setPassword(password)
                    .build());
            return Result.ok(null);
        } catch (StatusRuntimeException sre) {
            return Result.error(statusToErrorCode(sre.getStatus()));
        }
    }

    private static ErrorCode statusToErrorCode(Status status) {
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
