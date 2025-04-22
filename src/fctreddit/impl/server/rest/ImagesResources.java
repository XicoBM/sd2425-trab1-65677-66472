package fctreddit.impl.server.rest;

import java.util.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import fctreddit.api.rest.RestImage;
import fctreddit.api.java.Result;
import fctreddit.impl.server.java.JavaImage;
import fctreddit.api.java.Image;

public class ImagesResources implements RestImage {

    private static Logger Log = Logger.getLogger(ImagesResources.class.getName());

    final Image impl;
    
    public ImagesResources() {
        this.impl = new JavaImage();
    } 

    @Override
    public String createImage(String userId, byte[] imageContents, String password) {
        Log.info("createImage called with userId: " + userId);
        return handleResult(impl.createImage(userId, imageContents, password), "Failed to create image");
    }

    @Override
    public byte[] getImage(String userId, String imageId) {
        Log.info("getImage called with userId: " + userId + " and imageId: " + imageId);
        return handleResult(impl.getImage(userId, imageId), "Failed to retrieve image with ID: " + imageId);
    }

    @Override
    public void deleteImage(String userId, String imageId, String password) {
        Log.info("deleteImage called with userId: " + userId + " and imageId: " + imageId);
        handleResult(impl.deleteImage(userId, imageId, password), "Failed to delete image with ID: " + imageId);
    }

    private <T> T handleResult(Result<T> result, String errorMessage) {
        if (!result.isOK()) {
            Log.severe(errorMessage + ": " + result.error());
            throw new WebApplicationException(errorMessage, errorCodeToStatus(result.error()));
        }
        return result.value();
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
