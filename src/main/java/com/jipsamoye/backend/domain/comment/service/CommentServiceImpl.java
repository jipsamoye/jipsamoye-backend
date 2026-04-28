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
                mentionedUser = parent.getUser();
                parent = parent.getParent();
            } else {
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
        return CommentResponse.from(saved, replyCount, List.of());
    }

    @Override
    @Transactional
    public CommentResponse update(Long commentId, CommentUpdateRequest request, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (comment.isMasked()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "삭제된 댓글은 수정할 수 없습니다.");
        }

        comment.updateContent(request.content());

        long replyCount = comment.isReply() ? 0L : commentRepository.countByParentAndDeletedAtIsNull(comment);
        return CommentResponse.from(comment, replyCount, List.of());
    }

    @Override
    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (comment.isReply()) {
            comment.softDelete();
            petPostRepository.decrementCommentCount(comment.getPetPost().getId());

            Comment parent = comment.getParent();
            if (parent.isMasked() && commentRepository.countByParentAndDeletedAtIsNull(parent) == 0) {
                parent.softDelete();
                petPostRepository.decrementCommentCount(parent.getPetPost().getId());
            }
        } else {
            long replyCount = commentRepository.countByParentAndDeletedAtIsNull(comment);
            if (replyCount > 0) {
                comment.mask();
            } else {
                comment.softDelete();
                petPostRepository.decrementCommentCount(comment.getPetPost().getId());
            }
        }
    }

    @Override
    public PageResponse<CommentResponse> getCommentsByPost(Long postId, Pageable pageable) {
        Page<Comment> parents = commentRepository.findParentsByPetPostId(postId, pageable);

        if (parents.isEmpty()) {
            return PageResponse.from(parents.map(p -> CommentResponse.from(p, 0L, List.of())));
        }

        List<Long> parentIds = parents.getContent().stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        Map<Long, Long> countMap = commentRepository.countRepliesGroupedByParentIds(parentIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));

        Map<Long, List<Comment>> repliesMap = commentRepository.findTop3RepliesByParentIds(parentIds).stream()
                .collect(Collectors.groupingBy(r -> r.getParent().getId()));

        Page<CommentResponse> result = parents.map(p -> {
            long replyCount = countMap.getOrDefault(p.getId(), 0L);
            List<CommentResponse> replies = repliesMap.getOrDefault(p.getId(), List.of()).stream()
                    .map(CommentResponse::ofReply)
                    .collect(Collectors.toList());
            return CommentResponse.from(p, replyCount, replies);
        });

        return PageResponse.from(result);
    }

    @Override
    public PageResponse<CommentResponse> getReplies(Long parentId, Pageable pageable) {
        Page<CommentResponse> page = commentRepository.findRepliesByParentId(parentId, pageable)
                .map(CommentResponse::ofReply);
        return PageResponse.from(page);
    }
}
