package com.jipsamoye.backend.domain.boardComment.service;

import com.jipsamoye.backend.domain.board.entity.Board;
import com.jipsamoye.backend.domain.board.repository.BoardRepository;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentCreateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.request.BoardCommentUpdateRequest;
import com.jipsamoye.backend.domain.boardComment.dto.response.BoardCommentResponse;
import com.jipsamoye.backend.domain.boardComment.entity.BoardComment;
import com.jipsamoye.backend.domain.boardComment.repository.BoardCommentRepository;
import com.jipsamoye.backend.domain.notification.entity.NotificationType;
import com.jipsamoye.backend.domain.notification.event.NotificationEvent;
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
public class BoardCommentServiceImpl implements BoardCommentService {

    private final BoardCommentRepository boardCommentRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public BoardCommentResponse create(BoardCommentCreateRequest request, Long userId) {
        Board board = boardRepository.findById(request.boardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        BoardComment parent = null;
        User mentionedUser = null;

        if (request.parentId() != null) {
            parent = boardCommentRepository.findById(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND));

            if (!parent.getBoard().getId().equals(request.boardId())) {
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

        BoardComment comment = BoardComment.builder()
                .board(board)
                .user(user)
                .content(request.content())
                .parent(parent)
                .mentionedUser(mentionedUser)
                .build();

        BoardComment saved = boardCommentRepository.save(comment);
        boardRepository.incrementCommentCount(request.boardId());

        // 한 댓글 = 알림 대상 1명 (중복 발송 없음)
        if (parent == null && mentionedUser == null) {
            // 최상위 댓글 → 게시판 글 작성자에게 알림
            User boardOwner = board.getUser();
            if (boardOwner != null && !boardOwner.getId().equals(userId)) {
                eventPublisher.publishEvent(new NotificationEvent(
                        boardOwner, user,
                        NotificationType.BOARD_COMMENT,
                        saved.getId(),
                        board.getId(),
                        user.getNickname() + "님이 회원님의 게시글에 댓글을 남겼습니다"
                ));
            }
        } else {
            // 답글 → mentionedUser 우선, 없으면 부모 댓글 작성자에게 알림
            User notifyTarget = (mentionedUser != null) ? mentionedUser : parent.getUser();
            if (notifyTarget != null && !notifyTarget.getId().equals(userId)) {
                eventPublisher.publishEvent(new NotificationEvent(
                        notifyTarget, user,
                        NotificationType.BOARD_COMMENT_REPLY,
                        saved.getId(),
                        board.getId(),
                        user.getNickname() + "님이 회원님의 댓글에 답글을 남겼습니다"
                ));
            }
        }

        long replyCount = parent == null ? boardCommentRepository.countByParentAndDeletedAtIsNull(saved) : 0L;
        return BoardCommentResponse.from(saved, replyCount, List.of());
    }

    @Override
    @Transactional
    public BoardCommentResponse update(Long commentId, BoardCommentUpdateRequest request, Long userId) {
        BoardComment comment = boardCommentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (comment.isMasked()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "삭제된 댓글은 수정할 수 없습니다.");
        }

        comment.updateContent(request.content());

        long replyCount = comment.isReply() ? 0L : boardCommentRepository.countByParentAndDeletedAtIsNull(comment);
        return BoardCommentResponse.from(comment, replyCount, List.of());
    }

    @Override
    @Transactional
    public void delete(Long commentId, Long userId) {
        BoardComment comment = boardCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (comment.isReply()) {
            comment.softDelete();
            boardRepository.decrementCommentCount(comment.getBoard().getId());

            BoardComment parent = comment.getParent();
            if (parent.isMasked() && boardCommentRepository.countByParentAndDeletedAtIsNull(parent) == 0) {
                parent.softDelete();
                boardRepository.decrementCommentCount(parent.getBoard().getId());
            }
        } else {
            long replyCount = boardCommentRepository.countByParentAndDeletedAtIsNull(comment);
            if (replyCount > 0) {
                comment.mask();
            } else {
                comment.softDelete();
                boardRepository.decrementCommentCount(comment.getBoard().getId());
            }
        }
    }

    @Override
    public PageResponse<BoardCommentResponse> getCommentsByBoard(Long boardId, Pageable pageable) {
        Page<BoardComment> parents = boardCommentRepository.findParentsByBoardId(boardId, pageable);

        if (parents.isEmpty()) {
            return PageResponse.from(parents.map(p -> BoardCommentResponse.from(p, 0L, List.of())));
        }

        List<Long> parentIds = parents.getContent().stream()
                .map(BoardComment::getId)
                .collect(Collectors.toList());

        Map<Long, Long> countMap = boardCommentRepository.countRepliesGroupedByParentIds(parentIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));

        Map<Long, List<BoardComment>> repliesMap = boardCommentRepository.findTop3RepliesByParentIds(parentIds).stream()
                .collect(Collectors.groupingBy(r -> r.getParent().getId()));

        Page<BoardCommentResponse> result = parents.map(p -> {
            long replyCount = countMap.getOrDefault(p.getId(), 0L);
            List<BoardCommentResponse> replies = repliesMap.getOrDefault(p.getId(), List.of()).stream()
                    .map(BoardCommentResponse::ofReply)
                    .collect(Collectors.toList());
            return BoardCommentResponse.from(p, replyCount, replies);
        });

        return PageResponse.from(result);
    }

    @Override
    public PageResponse<BoardCommentResponse> getReplies(Long parentId, Pageable pageable) {
        Page<BoardCommentResponse> page = boardCommentRepository.findRepliesByParentId(parentId, pageable)
                .map(BoardCommentResponse::ofReply);
        return PageResponse.from(page);
    }
}
