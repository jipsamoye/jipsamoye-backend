package com.jipsamoye.backend.domain.user.repository;

import com.jipsamoye.backend.domain.user.entity.Provider;
import com.jipsamoye.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);

    boolean existsByNickname(String nickname);
}
