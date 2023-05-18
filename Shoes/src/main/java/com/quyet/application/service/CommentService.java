package com.quyet.application.service;

import com.quyet.application.entity.Comment;
import com.quyet.application.model.request.CreateCommentPostRequest;
import com.quyet.application.model.request.CreateCommentProductRequest;
import org.springframework.stereotype.Service;

@Service
public interface CommentService {
    Comment createCommentPost(CreateCommentPostRequest createCommentPostRequest,long userId);
    Comment createCommentProduct(CreateCommentProductRequest createCommentProductRequest, long userId);
}
