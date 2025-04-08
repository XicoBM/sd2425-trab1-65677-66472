package fctreddit.impl.server.java;

import java.util.logging.Logger;
import java.util.List;

import fctreddit.api.User;
import fctreddit.api.java.Users;
import fctreddit.api.java.Image;
import fctreddit.api.java.Result;
import fctreddit.api.java.Result.ErrorCode;
import fctreddit.impl.server.persistence.Hibernate;

public class JavaImage implements Image {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

    private Hibernate hibernate;

    public JavaImage() {
        hibernate = Hibernate.getInstance();
    }

    @Override
    public Result<String> createImage(String userId, byte[] imageContents, String password) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createImage'");
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

