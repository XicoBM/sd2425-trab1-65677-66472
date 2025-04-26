package fctreddit.impl.server.grpc;

import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;

import fctreddit.impl.grpc.generated_java.ImageGrpc;
import fctreddit.impl.server.java.JavaImage;
import fctreddit.api.java.Result;


import fctreddit.api.java.Image;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.CreateImageArgs;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.CreateImageResult;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.GetImageArgs;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.GetImageResult;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.DeleteImageArgs;
import fctreddit.impl.grpc.generated_java.ImageProtoBuf.DeleteImageResult;

public class GrpcImagesServerStub implements ImageGrpc.AsyncService, BindableService {

    Image impl;

    public GrpcImagesServerStub() {
        this.impl = new JavaImage();
    }

    @Override
    public io.grpc.ServerServiceDefinition bindService() {
        return ImageGrpc.bindService(this);
    }

    @Override
    public void createImage(CreateImageArgs request, StreamObserver<CreateImageResult> responseObserver) {
        Result<String> res = impl.createImage(request.getUserId(), request.getImageContents().toByteArray(),
                request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(CreateImageResult.newBuilder().setImageId(res.value()).build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getImage(GetImageArgs request, StreamObserver<GetImageResult> responseObserver) {
        Result<byte[]> res = impl.getImage(request.getUserId(), request.getImageId());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(GetImageResult.newBuilder()
                    .setData(com.google.protobuf.ByteString.copyFrom(res.value()))
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteImage(DeleteImageArgs request, StreamObserver<DeleteImageResult> responseObserver) {
        Result<Void> res = impl.deleteImage(request.getUserId(), request.getImageId(), request.getPassword());
        if (!res.isOK())
            responseObserver.onError(errorCodeToStatus(res.error()));
        else {
            responseObserver.onNext(DeleteImageResult.newBuilder().build());
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
