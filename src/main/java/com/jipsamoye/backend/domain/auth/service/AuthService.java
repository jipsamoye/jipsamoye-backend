package com.jipsamoye.backend.domain.auth.service;

import com.jipsamoye.backend.domain.user.dto.response.UserResponse;

public interface AuthService {

    UserResponse createGuest();

    UserResponse getMe(Long userId);

    void logout();

    void withdraw(Long userId);
}
