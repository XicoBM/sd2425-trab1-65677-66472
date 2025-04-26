package fctreddit.clients.java;

import fctreddit.api.java.Image;
import fctreddit.api.java.Result;

public abstract class ImageClient implements Image {
	
	protected static final int READ_TIMEOUT = 5000;
	protected static final int CONNECT_TIMEOUT = 5000;

	protected static final int MAX_RETRIES = 10;
	protected static final int RETRY_SLEEP = 5000;
	
	abstract public Result<String> createImage(String userId, byte[] imageContents, String password);;

	abstract public Result<byte[]> getImage(String userId, String imageId);
	
	abstract public Result<Void> deleteImage(String userId, String imageId, String password);

}
