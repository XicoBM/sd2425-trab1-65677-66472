package fctreddit.impl.server.java;

import fctreddit.api.User;
import fctreddit.api.java.Image;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.clients.rest.RestUsersClient;
import fctreddit.impl.server.discovery.Discovery;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class JavaImage implements Image {

    private static Logger Log = Logger.getLogger(JavaImage.class.getName());
    private static final String IMAGE_DIR = "imageFiles";

    private final Discovery discovery = Discovery.getInstance();

    private final RestUsersClient restUsersClient; 


    public JavaImage() {
        List<String> usersServiceUris = discovery.knownUrisOf("Users");
        if (usersServiceUris.isEmpty()) {
            throw new IllegalStateException("No known URIs for Users service found");
        }
        URI usersUri = URI.create(usersServiceUris.get(0));
        this.restUsersClient = new RestUsersClient(usersUri);
    
        Log.info("RestUsersClient created with URI: " + usersUri.toString());
    
        File dir = new File(IMAGE_DIR);
        if (!dir.exists()) {
            dir.mkdir();
        }
    }

    private User getUser(String userId) {
        Result<User> result = restUsersClient.getUserAux(userId);
    
        if (result == null || !result.isOK()) {
            if (result != null && result.error() == ErrorCode.NOT_FOUND) {
                Log.info("User does not exist.");
            } else {
                Log.severe("Failed to retrieve user with ID: " + userId);
            }
            return null;
        }
    
        return result.value();
    }
    
    

@Override
public Result<String> createImage(String userId, byte[] imageContents, String password) {
    Log.info("createImage : userId = " + userId);

    if (userId == null || password == null || imageContents == null) {
        return Result.error(ErrorCode.BAD_REQUEST);
    }

    User user = getUser(userId);
    if (user == null) {
        return Result.error(ErrorCode.NOT_FOUND);
    }

    if (!user.getPassword().equals(password)) {
        return Result.error(ErrorCode.FORBIDDEN);
    }

    try {
        String imageId = UUID.randomUUID().toString();
        String userFolderPath = IMAGE_DIR + File.separator + userId;

        File userFolder = new File(userFolderPath);
        if (!userFolder.exists()) {
            userFolder.mkdirs();
        }

        String imagePath = userFolderPath + File.separator + imageId + ".png";
        Files.write(Paths.get(imagePath), imageContents);

        String serverIp = InetAddress.getLocalHost().getHostAddress();
        String imageUrl = String.format("http://%s:8082/rest/image/%s/%s.png", serverIp, userId, imageId);

        return Result.ok(imageUrl);

    } catch (IOException e) {
        Log.severe("Error writing image: " + e.getMessage());
        return Result.error(ErrorCode.CONFLICT);
    }
}


@Override
public Result<byte[]> getImage(String userId, String imageId) {
    Log.info("getImage : " + imageId + " by user " + userId);

    String imagePath = IMAGE_DIR + File.separator + userId + File.separator + imageId;
    File file = new File(imagePath);

    if (!file.exists() || !file.isFile()) {
        Log.warning("Image not found at path: " + imagePath);
        return Result.error(ErrorCode.NOT_FOUND);
    }

    try (FileInputStream in = new FileInputStream(file)) {
        byte[] data = in.readAllBytes();
        return Result.ok(data);
    } catch (IOException e) {
        Log.severe("Unexpected error reading image file: " + e.getMessage());
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }
}

    
    @Override
    public Result<Void> deleteImage(String userId, String imageId, String password) {
        Log.info("deleteImage : " + imageId + " by user " + userId);

        if (userId == null || password == null || imageId == null) {
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        User user = getUser(userId);
        if (user == null) {
            return Result.error(ErrorCode.NOT_FOUND);
        }

        if (!user.getPassword().equals(password)) {
            return Result.error(ErrorCode.FORBIDDEN);
        }

        String imagePath = IMAGE_DIR + File.separator + userId + File.separator + imageId;
        File imgFile = new File(imagePath);

        if (!imgFile.exists()) {
            return Result.error(ErrorCode.NOT_FOUND);
        }

        if (imgFile.delete()) {
            return Result.ok();
        } else {
            Log.warning("Failed to delete image: " + imagePath);
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }
}
