package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.model.CachedAd;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CachedAdRepository extends JpaRepository<CachedAd, CachedAd.Key> {
    List<CachedAd> findByGameIdOrderByDisplayIndexAsc(String gameId);
}
