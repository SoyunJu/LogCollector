package com.soyunju.logcollector.knowledge.repository;


import com.soyunju.logcollector.knowledge.domain.KbTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KbTagRepository extends JpaRepository<KbTag, Long> {
    Optional<KbTag> findByKeyword(String keyword);
}
