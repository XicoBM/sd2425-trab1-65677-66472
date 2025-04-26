package fctreddit.impl.server.java;

import java.util.logging.Logger;
import java.net.URI;
import java.util.List;

import fctreddit.api.User;
import fctreddit.api.java.Users;
import fctreddit.clients.grpc.GrpcContentClient;
import fctreddit.clients.grpc.GrpcImagesClient;
import fctreddit.clients.java.ContentClient;
import fctreddit.clients.java.ImageClient;
import fctreddit.clients.rest.RestImagesClient;
import fctreddit.clients.rest.RestPostsClient;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.discovery.Discovery;
import fctreddit.impl.server.persistence.Hibernate;

public class JavaUsers implements Users {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

    
    private ContentClient contentClient;
    
    private ImageClient imageClient;

    private Discovery discovery = Discovery.getInstance();

    private Hibernate hibernate;

    public JavaUsers() {
        hibernate = Hibernate.getInstance();
    }

    private boolean initializeImageClient() {
        if (imageClient != null) {
            return true; 
        }
        List<String> imagesServiceUris = discovery.knownUrisOf("Image");
        if (imagesServiceUris.isEmpty()) {
            Log.warning("No known URIs for Images service found");
            return false;
        }
        try {
            URI imagesUri = URI.create(imagesServiceUris.get(0));
            if (imagesUri.getScheme().equals("http")) {
                imageClient = new RestImagesClient(imagesUri);
            } else if (imagesUri.getScheme().equals("grpc")) {
                imageClient = new GrpcImagesClient(imagesUri);
            } else {
                throw new IllegalArgumentException("Unsupported URI scheme: " + imagesUri.getScheme());
            }
            return true;
        } catch (Exception e) {
            Log.severe("Failed to initialize ImageClient: " + e.getMessage());
            return false;
        }
    }

    private boolean initializeContentClient() {
        if (contentClient != null) {
            return true; 
        }
        List<String> contentServiceUris = discovery.knownUrisOf("Content");
        if (contentServiceUris.isEmpty()) {
            Log.warning("No known URIs for Content service found");
            return false;
        }
        try {
            URI contentUri = URI.create(contentServiceUris.get(0));
            if (contentUri.getScheme().equals("http")) {
                contentClient = new RestPostsClient(contentUri);
            } else if (contentUri.getScheme().equals("grpc")) {
                contentClient = new GrpcContentClient(contentUri);
            } else {
                throw new IllegalArgumentException("Unsupported URI scheme: " + contentUri.getScheme());
            }
            return true;
        } catch (Exception e) {
            Log.severe("Failed to initialize ContentClient: " + e.getMessage());
            return false;
        }
    }

private void deleteImage(String imageId, String userId, String password) {
        if (!initializeImageClient()) {
            Log.warning("Skipping image deletion due to unavailable Images service");
            return;
        }

        Result<Void> result = imageClient.deleteImage(userId, imageId, password);
        if (result == null || !result.isOK()) {
            if (result != null && result.error() == ErrorCode.NOT_FOUND) {
                Log.info("Image does not exist.");
            } else {
                Log.severe("Failed to delete image with ID: " + imageId);
            }
        } else {
            Log.info("Image deleted successfully.");
        }
    }

    private void deleteVotesFromUser(String userId) {
        if (!initializeContentClient()) {
            Log.warning("Skipping votes deletion due to unavailable Content service");
            return;
        }

        Result<Void> result = contentClient.deleteVotesFromUser(userId);
        if (result == null || !result.isOK()) {
            if (result != null && result.error() == ErrorCode.NOT_FOUND) {
                Log.info("Votes do not exist.");
            } else {
                Log.severe("Failed to delete votes for user with ID: " + userId);
            }
        } else {
            Log.info("Votes deleted successfully.");
        }
    }

    private void nullifyPostAuthors(String userId) {
        if (!initializeContentClient()) {
            Log.warning("Skipping post nullification due to unavailable Content service");
            return;
        }
        Result<Void> result = contentClient.nullifyPostAuthors(userId);
        if (result == null || !result.isOK()) {
            if (result != null && result.error() == ErrorCode.NOT_FOUND) {
                Log.warning("No posts found to nullify for user: " + userId);
            } else {
                Log.severe("Failed to nullify posts for user: " + userId);
            }
        } else {
            Log.info("All posts by user " + userId + " were successfully nullified.");
        }
    }
    @Override
    public Result<String> createUser(User user) {
        Log.info("createUser : " + user);

        if (user.getUserId() == null || user.getUserId().isBlank() ||
            user.getPassword() == null || user.getPassword().isBlank() ||
            user.getFullName() == null || user.getFullName().isBlank() ||
            user.getEmail() == null || user.getEmail().isBlank()) {
            Log.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        User existingUser = hibernate.get(User.class, user.getUserId());
        if (existingUser != null) {
            Log.info("User already exists.");
            return Result.error(ErrorCode.CONFLICT);
        }

        try {
            hibernate.persist(user);
            Log.info("User persisted successfully: " + user.getUserId());
        } catch (Exception e) {
            Log.severe("Failed to persist user: " + e.getMessage());
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        return Result.ok(user.getUserId());
    }

    @Override
    public Result<User> getUser(String userId, String password) {
        Log.info("getUser : user = " + userId + "; pwd = " + password);

        // Check if user is valid
        if (userId == null) {
            Log.info("UserId or password null.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        User user = null;
        try {
            user = hibernate.get(User.class, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        if (user == null) {
            Log.info("User does not exist.");
            return Result.error(ErrorCode.NOT_FOUND);
        }

        if (password == null || !user.getPassword().equals(password)) {
            Log.info("Password is incorrect");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        return Result.ok(user);
    }

    @Override
    public Result<User> updateUser(String userId, String password, User updatedUser) {
        Log.info("updateUser : " + userId);

        if (userId == null || password == null || updatedUser == null) {
            Log.info("Missing parameters.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        User existingUser;
        try {
            existingUser = hibernate.get(User.class, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        if (existingUser == null) {
            Log.info("User not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }

        if (!existingUser.getPassword().equals(password)) {
            Log.info("Incorrect password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        if (updatedUser.getFullName() != null) {
            existingUser.setFullName(updatedUser.getFullName());
        }
        if (updatedUser.getEmail() != null) {
            existingUser.setEmail(updatedUser.getEmail());
        }
        if (updatedUser.getPassword() != null) {
            existingUser.setPassword(updatedUser.getPassword());
        }
        if (updatedUser.getAvatarUrl() != null) {
            existingUser.setAvatarUrl(updatedUser.getAvatarUrl());
        }

        try {
            hibernate.update(existingUser);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        return Result.ok(existingUser);
    }

    @Override
    public Result<User> deleteUser(String userId, String password) {
        Log.info("deleteUser : " + userId);

        if (userId == null || password == null) {
            Log.info("Missing userId or password.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        User user;
        try {
            user = hibernate.get(User.class, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        if (user == null) {
            Log.info("User not found.");
            return Result.error(ErrorCode.NOT_FOUND);
        }

        if (!user.getPassword().equals(password)) {
            Log.info("Incorrect password.");
            return Result.error(ErrorCode.FORBIDDEN);
        }

        
            deleteVotesFromUser(userId);
            nullifyPostAuthors(userId);

        if (user.getAvatarUrl() != null) {
            String imageUrl = user.getAvatarUrl();
            String id = imageUrl.substring(imageUrl.lastIndexOf('/') + 1, imageUrl.lastIndexOf('.'));
                deleteImage(id, userId, password);
        }

        try {
            hibernate.delete(user);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        return Result.ok(user);
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info("searchUsers : " + pattern);

        if (pattern == null || pattern.isEmpty()) {
            Log.info("Empty search pattern.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        try {
            List<User> users = hibernate.jpql("SELECT u FROM User u WHERE u.userId LIKE '%" + pattern + "%'", User.class);
            return Result.ok(users);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<User> getUserAux(String userId) {
        Log.info("getUser : user = " + userId);

        if (userId == null) {
            Log.info("UserId null.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        User user = null;
        try {
            user = hibernate.get(User.class, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(ErrorCode.INTERNAL_ERROR);
        }

        if (user == null) {
            Log.info("User does not exist.");
            return Result.error(ErrorCode.NOT_FOUND);
        }

        return Result.ok(user);
    }
}