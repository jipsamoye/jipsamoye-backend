package com.jipsamoye.backend.global.scheduler;

import com.jipsamoye.backend.domain.petPost.dto.response.PetPostListResponse;
import com.jipsamoye.backend.domain.petPost.entity.PetPost;
import com.jipsamoye.backend.domain.petPost.repository.PetPostRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopularPostScheduler {

    private final PetPostRepository petPostRepository;
    private volatile List<PetPostListResponse> popularPosts = Collections.emptyList();

    @PostConstruct
    public void init() {
        refreshPopularPosts();
    }

    @Scheduled(fixedRate = 3600000)
    public void refreshPopularPosts() {
        log.info("오늘의 멍냥 갱신 시작");
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<PetPost> posts = petPostRepository.findPopularPosts(since, PageRequest.of(0, 10));
        this.popularPosts = posts.stream()
                .map(PetPostListResponse::from)
                .toList();
        log.info("오늘의 멍냥 갱신 완료: {}건", popularPosts.size());
    }

    public List<PetPostListResponse> getPopularPosts() {
        return popularPosts;
    }
}
