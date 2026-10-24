package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.model.CachedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface CachedItemRepository extends JpaRepository<CachedItem, CachedItem.Key> {
    List<CachedItem> findByGameIdOrderByDisplayIndexAsc(String gameId);

    // Derived deletes need a transaction
    @Transactional
    void deleteByGameId(String gameId);
}
