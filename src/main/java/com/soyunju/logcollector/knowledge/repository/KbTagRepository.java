package com.soyunju.logcollector.repository.kb;

import com.soyunju.logcollector.domain.kb.KbTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KbTagRepository extends JpaRepository<KbTag, Long> {
    Optional<KbTag> findByKeyword(String keyword);
}
