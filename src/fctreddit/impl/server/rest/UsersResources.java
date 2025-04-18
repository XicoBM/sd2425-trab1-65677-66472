package fctreddit.impl.server.rest;

import java.util.logging.Logger;
import java.util.List;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import fctreddit.api.User;
import fctreddit.api.java.Users;
import fctreddit.api.java.Result;
import fctreddit.api.rest.RestUsers;
import fctreddit.impl.server.java.JavaUsers;

public class UsersResources implements RestUsers {

    private static Logger Log = Logger.getLogger(UsersResources.class.getName());

    final Users impl;

    public UsersResources() {
        impl = new JavaUsers();
    }

    @Override
    public String createUser(User user) {
        Log.info("createUser: " + user);
        return handleResult(impl.createUser(user), "Failed to create user");
    }

    @Override
    public User getUser(String userId, String password) {
        Log.info("getUser : user = " + userId + "; password = [PROTECTED]");
        return handleResult(impl.getUser(userId, password), "Failed to retrieve user with ID: " + userId);
    }

    @Override
    public User updateUser(String userId, String password, User user) {
        Log.info("updateUser : user = " + userId + "; pwd = [PROTECTED] ; userData = " + user);
        return handleResult(impl.updateUser(userId, password, user), "Failed to update user with ID: " + userId);
    }

    @Override
    public User deleteUser(String userId, String password) {
        Log.info("deleteUser : user = " + userId + "; pwd = [PROTECTED]");
        return handleResult(impl.deleteUser(userId, password), "Failed to delete user with ID: " + userId);
    }

    @Override
    public List<User> searchUsers(String pattern) {
        Log.info("searchUsers : pattern = " + pattern);
        return handleResult(impl.searchUsers(pattern), "Failed to search users with pattern: " + pattern);
    }

    private <T> T handleResult(Result<T> result, String errorMessage) {
        if (!result.isOK()) {
            Log.severe(errorMessage + ": " + result.error());
            throw new WebApplicationException(errorMessage, errorCodeToStatus(result.error()));
        }
        return result.value();
    }

    protected static Status errorCodeToStatus(Result.ErrorCode errorCode) {
        return switch (errorCode) {
            case NOT_FOUND -> Status.NOT_FOUND;
            case CONFLICT -> Status.CONFLICT;
            case FORBIDDEN -> Status.FORBIDDEN;
            case NOT_IMPLEMENTED -> Status.NOT_IMPLEMENTED;
            case BAD_REQUEST -> Status.BAD_REQUEST;
            default -> Status.INTERNAL_SERVER_ERROR;
        };
    }
}