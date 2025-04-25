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
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
    private static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

    private final Discovery discovery;

    {
        Discovery tempDiscovery = null;
        try {
            tempDiscovery = new Discovery(DISCOVERY_ADDR);
        } catch (IOException e) {
            Log.severe("Failed to initialize Discovery: " + e.getMessage());
        }
        discovery = tempDiscovery;
    }
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
            List<String> usersServiceUris = discovery.knownUrisAsStringsOf("Users", 1);
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
