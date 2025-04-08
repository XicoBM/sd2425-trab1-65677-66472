package fctreddit.impl.server.java;

import java.util.logging.Logger;
import java.util.List;

import fctreddit.api.User;
import fctreddit.api.java.Users;
import fctreddit.api.java.Image;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.persistence.Hibernate;
import fctreddit.api.rest.RestImage;

public class JavaImage implements Image {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

    private Hibernate hibernate;

    public JavaImage() {
        hibernate = Hibernate.getInstance();
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
            //<OK, String> in the case of success returning the URI to access the image. 
            hibernate.persist(new RestImage(userId, imageContents));
            return Result.ok(userId + "/" + imageContents);
        } catch (Exception e) {
            e.printStackTrace(); // Most likely the exception is due to the user already existing...
            Log.info("Image already exists.");
            return Result.error(ErrorCode.CONFLICT);
        }
    }

    @Override
    public Result<byte[]> getImage(String userId, String imageId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getImage'");
    }

    @Override
    public Result<Void> deleteImage(String userId, String imageId, String password) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deleteImage'");
    }
}
