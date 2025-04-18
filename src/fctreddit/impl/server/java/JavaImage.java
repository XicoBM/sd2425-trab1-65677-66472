package fctreddit.impl.server.java;

import java.io.File;
import java.util.logging.Logger;

import fctreddit.api.User;
import fctreddit.api.java.Image;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.persistence.Hibernate;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;

public class JavaImage implements Image {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
    private static final String IMAGE_URI = "imageFiles";
    private Hibernate hibernate;

    public JavaImage() {
        File dir = new File(IMAGE_URI);
        if (!dir.exists()) {
            dir.mkdir();
        }
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
            String imageId = String.valueOf(System.currentTimeMillis());
            String imagePath = IMAGE_URI + File.separator + imageId + ".png";
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                imageFile.createNewFile();
            }
            java.nio.file.Files.write(imageFile.toPath(), imageContents);
            return Result.ok(imagePath);
        } catch (Exception e) {
            e.printStackTrace(); 
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
        byte[] image;
        try {
            image = getImage(imageId);
            if (image == null) {
                Log.info("Image not found.");
                return Result.error(ErrorCode.NOT_FOUND);
            }
            return Result.ok(image);
        } catch (Exception e) {
            Log.info("Image not found.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
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
            Path image = Paths.get(IMAGE_URI, imageId + ".png");
            Files.delete(image);
            return Result.ok();
        } catch (Exception e) {
            e.printStackTrace(); 
            Log.info("Image already exists.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }
    }

    private static byte[] getImage(String imageId) throws IOException {
        Path imagePath = Paths.get(IMAGE_URI, imageId + ".png");

        if (!Files.exists(imagePath)) {
            return null;
        }
        try {
            return Files.readAllBytes(imagePath);
        } catch (IOException e) {
            Log.severe("getImage: Failed to read image.");
            throw new IOException("Failed to read image", e);
        }
    }
}
