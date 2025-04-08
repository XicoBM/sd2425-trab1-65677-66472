package fctreddit.impl.server.rest;

import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import fctreddit.api.rest.RestImage;
import fctreddit.api.java.Result;

public class ImagesResources implements RestImage {

    private static Logger Log = Logger.getLogger(ImagesResources.class.getName());
    private static final String IMAGE_DIRECTORY = "imageFiles";

    public ImagesResources() {
        File dir = new File(IMAGE_DIRECTORY);
        if (!dir.exists()) {
            dir.mkdir();
        }
    }

    @Override
    public String createImage(String userId, byte[] imageContents, String password) {
        Log.info("createImage called with userId: " + userId);

        if (imageContents.length == 0) {
            Log.info("createImage: Invalid input.");
            throw new WebApplicationException(Status.BAD_REQUEST);
        }

        String imageId = String.valueOf(System.currentTimeMillis());
        Path imagePath = Paths.get(IMAGE_DIRECTORY, imageId + ".png");

        try {
            Files.write(imagePath, imageContents);
            Log.info("createImage: Image created with ID " + imageId);
            return imagePath.toUri().toString();
        } catch (IOException e) {
            Log.severe("createImage: Failed to write image.");
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public byte[] getImage(String userId, String imageId) {
        Log.info("getImage called with userId: " + userId + " and imageId: " + imageId);

        Path imagePath = Paths.get(IMAGE_DIRECTORY, imageId + ".png");

        if (!Files.exists(imagePath)) {
            Log.info("getImage: Image not found.");
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        try {
            return Files.readAllBytes(imagePath);
        } catch (IOException e) {
            Log.severe("getImage: Failed to read image.");
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void deleteImage(String userId, String imageId, String password) {
        Log.info("deleteImage called with userId: " + userId + " and imageId: " + imageId);

        Path imagePath = Paths.get(IMAGE_DIRECTORY, imageId + ".png");

        try {
            if (Files.deleteIfExists(imagePath)) {
                Log.info("deleteImage: Image deleted with ID " + imageId);
            } else {
                Log.info("deleteImage: Image not found.");
                throw new WebApplicationException(Status.NOT_FOUND);
            }
        } catch (IOException e) {
            Log.severe("deleteImage: Failed to delete image.");
            throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
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
