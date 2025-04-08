package fctreddit.impl.server.java;

import java.util.logging.Logger;

import fctreddit.api.User;
import fctreddit.api.java.Image;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.rest.ImagesResources;
import fctreddit.impl.server.persistence.Hibernate;

public class JavaImage implements Image {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

    private ImagesResources imagesResources;
    private Hibernate hibernate;

    public JavaImage() {
        imagesResources = new ImagesResources();
    }

    @Override
    public Result<String> createImage(String userId, byte[] imageContents, String password) {
        Log.info("createImage : userId = " + userId + "; password = " + password);

        if (password == null || imageContents == null) {
            Log.info("UserId, password or imageContents null.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        User user = hibernate.get(User.class, userId);
        if (user == null) {
            Log.info("UserId does not exist.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        if (!user.getPassword().equals(password)) {
            Log.info("UserId or password incorrect.");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        try {
            String imageUri = imagesResources.createImage(userId, imageContents, password);
            return Result.ok(imageUri);
        } catch (Exception e) {
            e.printStackTrace(); // Most likely the exception is due to the user already existing...
            Log.info("Image already exists.");
            return Result.error(ErrorCode.CONFLICT);
        }
    }

    @Override
    public Result<byte[]> getImage(String userId, String imageId) {
        Log.info("getImage : userId = " +  userId + "; imageId = " + imageId);

        if(userId == null || imageId == null) {
            Log.info("UserId or imageId null.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        byte[] image = imagesResources.getImage(userId, imageId);
        if (image == null) {
            Log.info("Image does not exist.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        return Result.ok(image); 
    }

    @Override
    public Result<Void> deleteImage(String userId, String imageId, String password) {
        Log.info("deleteImage : userId = " + userId + "; imageId = " + imageId + "; password = " + password);

        if (password == null || imageId == null) {
            Log.info("UserId, password or imageContents null.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
        User user = hibernate.get(User.class, userId);
        if (user == null) {
            Log.info("UserId does not exist.");
            return Result.error(ErrorCode.NOT_FOUND);
        }
        if (!user.getPassword().equals(password)) {
            Log.info("UserId or password incorrect.");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        try {
            imagesResources.deleteImage(userId, imageId, password);
            return Result.ok(null);
        } catch (Exception e) {
            e.printStackTrace(); // Most likely the exception is due to the user already existing...
            Log.info("Image already exists.");
            return Result.error(ErrorCode.CONFLICT);
        }
    }
}
