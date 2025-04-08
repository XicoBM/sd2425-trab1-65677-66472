package fctreddit.impl.server.rest;

import java.util.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import fctreddit.api.java.Image;
import fctreddit.api.rest.RestImage;
import fctreddit.api.java.Result;
import fctreddit.impl.server.java.JavaImage;

public class ImagesResources implements RestImage {

    private static Logger Log = Logger.getLogger(ImagesResources.class.getName());

    final Image impl;

    public ImagesResources() {
        impl = new JavaImage();
    }

    @Override
    public String createImage(String userId, byte[] imageContents, String password) {
        Log.info("createImage called with userId: " + userId);

        Result<String> result = impl.createImage(userId, imageContents, password);
        if (!result.isOK()) {
            throw new WebApplicationException(errorCodeToStatus(result.error()));
        } else {
            return result.value();
        }
    }

    @Override
    public byte[] getImage(String userId, String imageId) {
        Log.info("getImage called with userId: " + userId + " and imageId: " + imageId);

        Result<byte[]> result = impl.getImage(userId, imageId);
        if (!result.isOK()) {
            throw new WebApplicationException(errorCodeToStatus(result.error()));
        } else {
            return result.value();
        }
    }

    @Override
    public void deleteImage(String userId, String imageId, String password) {
        Log.info("deleteImage called with userId: " + userId + " and imageId: " + imageId);

        Result<Void> result = impl.deleteImage(userId, imageId, password);
        if (!result.isOK()) {
            throw new WebApplicationException(errorCodeToStatus(result.error()));
        }

    }

    protected static Status errorCodeToStatus(Result.ErrorCode errorCode) {
        Status status = switch (errorCode) {
            case NOT_FOUND -> Status.NOT_FOUND;
            case CONFLICT -> Status.CONFLICT;
            case FORBIDDEN -> Status.FORBIDDEN;
            case NOT_IMPLEMENTED -> Status.NOT_IMPLEMENTED;
            case BAD_REQUEST -> Status.BAD_REQUEST;
            default -> Status.INTERNAL_SERVER_ERROR;
        };
        return status;
    }

}
