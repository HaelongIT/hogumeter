package dev.hogumeter.core.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DigestStateRepository extends JpaRepository<DigestStateEntity, Long> {
}
