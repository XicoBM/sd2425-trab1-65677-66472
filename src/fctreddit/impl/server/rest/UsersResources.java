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

		Result<String> result = impl.createUser(user);
		if (!result.isOK()) {
			throw new WebApplicationException(errorCodeToStatus(result.error()));
		} else {
			return result.value();
		}
	}

	@Override
	public User getUser(String userId, String password) {
		Log.info("getUser : user = " + userId + "; password = " + password);

		Result<User> result = impl.getUser(userId, password);
		if (!result.isOK()) {
			throw new WebApplicationException(errorCodeToStatus(result.error()));
		} else {
			return result.value();
		}
	}

	@Override
	public User updateUser(String userId, String password, User user) {
		Log.info("updateUser : user = " + userId + "; pwd = " + password + " ; userData = " + user);

		Result<User> result = impl.updateUser(userId, password, user);
		if (!result.isOK()) {
			throw new WebApplicationException(errorCodeToStatus(result.error()));
		} else {
			return result.value();
		}
	}

	@Override
	public User deleteUser(String userId, String password) {
		Log.info("deleteUser : user = " + userId + "; pwd = " + password);

		Result<User> result = impl.deleteUser(userId, password);
		if (!result.isOK()) {
			throw new WebApplicationException(errorCodeToStatus(result.error()));
		} else {
			return result.value();
		}
	}

	@Override
	public List<User> searchUsers(String pattern) {
		Log.info("searchUsers : pattern = " + pattern);

		Result<List<User>> result = impl.searchUsers(pattern);
		if (!result.isOK()) {
			throw new WebApplicationException(errorCodeToStatus(result.error()));
		} else {
			return result.value();
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
