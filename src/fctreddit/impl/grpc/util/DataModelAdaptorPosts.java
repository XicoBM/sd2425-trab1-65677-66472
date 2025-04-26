package fctreddit.impl.grpc.util;

import fctreddit.api.Post;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GrpcPost;
import fctreddit.impl.grpc.generated_java.ContentProtoBuf.GrpcPost.Builder;

public class DataModelAdaptorPosts {
    public static GrpcPost Post_to_GrpcPost( Post from )  {
		Builder b = GrpcPost.newBuilder();
		
		if(from.getPostId() != null)
			b.setPostId( from.getPostId());
		
		if(from.getContent() != null)
			b.setContent( from.getContent());
		
		if(from.getAuthorId() != null)
			b.setAuthorId( from.getAuthorId());

        if(from.getParentUrl() != null)
            b.setParentUrl(from.getParentUrl());
	
        if(from.getMediaUrl() != null)
            b.setMediaUrl(from.getMediaUrl());
        
        if(from.getUpVote() != 0)
            b.setUpVote(from.getUpVote());

        if(from.getDownVote() != 0)
            b.setDownVote(from.getDownVote());

		return b.build();
	}

    public static Post GrpcPost_to_Post(GrpcPost from) {
        Post p = new Post();

        if (from.hasPostId())
            p.setPostId(from.getPostId());

        if (from.hasContent())
            p.setContent(from.getContent());

        if (from.hasAuthorId())
            p.setAuthorId(from.getAuthorId());

        if (from.hasParentUrl())
            p.setParentUrl(from.getParentUrl());

        if (from.hasMediaUrl())
            p.setMediaUrl(from.getMediaUrl());

        if (from.hasUpVote())
            p.setUpVote(from.getUpVote());

        if (from.hasDownVote())
            p.setDownVote(from.getDownVote());

        return p;
    }
}
