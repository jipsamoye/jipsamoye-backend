package com.jipsamoye.backend.domain.figurine.repository;

import com.jipsamoye.backend.domain.figurine.entity.FigurineJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface FigurineJobRepository extends JpaRepository<FigurineJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FigurineJob> findWithLockById(Long id);
}
