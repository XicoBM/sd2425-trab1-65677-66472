package fctreddit.impl.server.java;

import fctreddit.api.User;
import fctreddit.api.java.Image;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.discovery.Discovery;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class JavaImage implements Image {

    private static Logger Log = Logger.getLogger(JavaImage.class.getName());
    private static final String IMAGE_DIR = "imageFiles";
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int REPLY_TIMEOUT = 3000;

    private final Discovery discovery = Discovery.getInstance();
    private final Client client;

    public JavaImage() {
        File dir = new File(IMAGE_DIR);
        if (!dir.exists()) {
            dir.mkdir();
        }

        ClientConfig config = new ClientConfig();
        config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
        config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMEOUT);
        this.client = ClientBuilder.newClient(config);
    }

    private User getUser(String userId) {
        try {
            List<String> usersServiceUris = discovery.knownUrisOf("Users");
            if (usersServiceUris.isEmpty()) {
                return null; 
            }
    
            String usersUri = usersServiceUris.get(0) + "/users/" + userId + "/aux";
    
            WebTarget target = client.target(usersUri);
    
            Response r = target.request().accept(MediaType.APPLICATION_JSON).get();
    
            if (r.getStatus() == 200) {
                return r.readEntity(User.class);
            } else {
                Log.warning("Failed to get user. Status: " + r.getStatus());
            }
        } catch (Exception e) {
            Log.severe("Exception while contacting Users service: " + e.getMessage());
        }
    
        return null;
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

            String absoluteUri = new File(imagePath).toURI().toString();

            Log.info("Image saved: " + imagePath + " -> URI: " + absoluteUri);
            return Result.ok(absoluteUri);
    

        } catch (IOException e) {
            Log.severe("Error writing image: " + e.getMessage());
            return Result.error(ErrorCode.CONFLICT);
        }
    }

    @Override
    public Result<byte[]> getImage(String userId, String imageId) {
        Log.info("getImage : " + imageId + " for user " + userId);
        try {
            String imagePath = IMAGE_DIR + File.separator + userId + File.separator + imageId + ".png";
            byte[] data = Files.readAllBytes(Paths.get(imagePath));
            return Result.ok(data);
        } catch (IOException e) {
            Log.severe("Image not found: " + e.getMessage());
            return Result.error(ErrorCode.NOT_FOUND);
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

        String imagePath = IMAGE_DIR + File.separator + userId + File.separator + imageId + ".png";
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
