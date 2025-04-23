package fctreddit.impl.server.java;

import java.util.logging.Logger;
import java.util.List;

import fctreddit.api.User;
import fctreddit.api.java.Users;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.persistence.Hibernate;

public class JavaUsers implements Users {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

    private Hibernate hibernate;

    public JavaUsers() {
        hibernate = Hibernate.getInstance();
    }

    @Override
    public Result<String> createUser(User user) {
        Log.info("createUser : " + user);

        if (user.getUserId() == null || user.getPassword() == null || user.getFullName() == null
                || user.getEmail() == null) {
            Log.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST);
        }

        try {
            hibernate.persist(user);
        } catch (Exception e) {
            e.printStackTrace(); 
            Log.info("User already exists.");
            return Result.error(ErrorCode.CONFLICT);
        }

        return Result.ok(user.getUserId());
    }

    @Override
    public Result<User> getUser(String userId, String password) {
        Log.info("getUser : user = " + userId + "; pwd = " + password);

        // Check if user is valid
        if (userId == null || password == null) {
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
        if (!user.getPassword().equals(password)) {
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
}
