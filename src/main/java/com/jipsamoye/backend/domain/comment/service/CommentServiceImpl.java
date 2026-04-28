package com.jipsamoye.backend.domain.comment.service;

import com.jipsamoye.backend.domain.comment.dto.request.CommentCreateRequest;
import com.jipsamoye.backend.domain.comment.dto.request.CommentUpdateRequest;
import com.jipsamoye.backend.domain.comment.dto.response.CommentResponse;
import com.jipsamoye.backend.domain.comment.entity.Comment;
import com.jipsamoye.backend.domain.comment.repository.CommentRepository;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.event.NotificationEvent;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import com.jipsamoye.backend.domain.user.entity.User;
import com.jipsamoye.backend.domain.user.repository.UserRepository;
import com.jipsamoye.backend.global.code.ErrorCode;
import com.jipsamoye.backend.global.exception.BusinessException;
import com.jipsamoye.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PetPostRepository petPostRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CommentResponse create(CommentCreateRequest request, Long userId) {
        PetPost petPost = petPostRepository.findById(request.petPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Comment parent = null;
        User mentionedUser = null;

        if (request.parentId() != null) {
            parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

            if (!parent.getPetPost().getId().equals(request.petPostId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (parent.isMasked()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "삭제된 댓글에는 답글을 달 수 없습니다.");
            }

            if (parent.isReply()) {
                // 답글의 답글 → root로 자동 매핑, mentionedUser = 원 답글 작성자
                mentionedUser = parent.getUser();
                parent = parent.getParent();
            } else {
                // 직접 답글 → mentionedUserId가 있으면 fetch
                if (request.mentionedUserId() != null) {
                    mentionedUser = userRepository.findById(request.mentionedUserId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                }
            }
        }

        Comment comment = Comment.builder()
                .petPost(petPost)
                .user(user)
                .content(request.content())
                .parent(parent)
                .mentionedUser(mentionedUser)
                .build();

        Comment saved = commentRepository.save(comment);
        petPostRepository.incrementCommentCount(request.petPostId());

        if (parent != null && !parent.getUser().getId().equals(userId)) {
            eventPublisher.publishEvent(new NotificationEvent(
                    parent.getUser(), user,
                    NotificationType.PET_POST_COMMENT_REPLY,
                    saved.getId(),
                    user.getNickname() + "님이 회원님의 댓글에 답글을 남겼습니다"
            ));
        }

        long replyCount = parent == null ? commentRepository.countByParentAndDeletedAtIsNull(saved) : 0L;
        List<CommentResponse> replies = List.of();
        return CommentResponse.from(saved, replyCount, replies);
    }

    @Override
    @Transactional
    public CommentResponse update(Long commentId, CommentUpdateRequest request, Long userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Transactional
    public void delete(Long commentId, Long userId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PageResponse<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PageResponse<CommentResponse> getReplies(Long parentId, Pageable pageable) {
        throw new UnsupportedOperationException();
    }
}
