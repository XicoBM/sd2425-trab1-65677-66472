package fctreddit.impl.server.java;

import java.util.logging.Logger;
import java.net.URI;
import java.util.List;

import fctreddit.api.User;
import fctreddit.api.java.Users;
import fctreddit.clients.rest.RestImagesClient;
import fctreddit.clients.rest.RestPostsClient;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.discovery.Discovery;
import fctreddit.impl.server.persistence.Hibernate;

public class JavaUsers implements Users {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

    private final Discovery discovery = Discovery.getInstance();

    private final RestPostsClient restPostsClient; 
    private final RestImagesClient restImagesClient; 

    private Hibernate hibernate;

    public JavaUsers() {
        hibernate = Hibernate.getInstance();

        List<String> contentServiceUris = discovery.knownUrisOf("Content");
        if (contentServiceUris.isEmpty()) {
            throw new IllegalStateException("No known URIs for Content service found");
        }
        URI contentUri = URI.create(contentServiceUris.get(0));
        this.restPostsClient = new RestPostsClient(contentUri);

        List<String> imagesServiceUris = discovery.knownUrisOf("Image");
        if (imagesServiceUris.isEmpty()) {
            throw new IllegalStateException("No known URIs for Images service found");
        }
        URI imagesUri = URI.create(imagesServiceUris.get(0));
        this.restImagesClient = new RestImagesClient(imagesUri);  
    }

    private void deleteImage(String imageId, String userId, String password) {
        Result<Void> result = restImagesClient.deleteImage(userId, imageId, password);

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
        Result<Void> result = restPostsClient.deleteVotesFromUser(userId);

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
        Log.info("========== NULLIFY POST AUTHORS ==========");
        Log.info(">>> [CALL] nullifyPostAuthors called for user: " + userId);
    
        Result<Void> result = restPostsClient.nullifyPostAuthors(userId);
    
        if (result == null || !result.isOK()) {
            if (result != null && result.error() == ErrorCode.NOT_FOUND) {
                Log.warning(">>> [RESULT] No posts found to nullify for user: " + userId);
            } else {
                Log.severe(">>> [ERROR] Failed to nullify posts for user: " + userId);
            }
        } else {
            Log.info(">>> [SUCCESS] All posts by user " + userId + " were successfully nullified.");
        }
        Log.info("==========================================");
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
        } catch (Exception e) {
            e.printStackTrace(); 
            Log.info("Unable to store user.");
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

        // Check if user exists
        if (user == null) {
            Log.info("User does not exist.");
            return Result.error(ErrorCode.NOT_FOUND);
        }

        // Check if the password is correct
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
			List<User> users = hibernate.jpql("SELECT u FROM User u WHERE u.userId LIKE '%" + pattern +"%'", User.class);
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
