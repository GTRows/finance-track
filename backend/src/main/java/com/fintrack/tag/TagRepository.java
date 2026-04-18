package com.fintrack.tag;

import com.fintrack.common.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByUserIdOrderByNameAsc(UUID userId);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    Optional<Tag> findByUserIdAndName(UUID userId, String name);

    List<Tag> findAllByIdInAndUserId(List<UUID> ids, UUID userId);
}
