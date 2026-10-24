package com.akbori.solution.mugloar.repository;

import com.akbori.solution.mugloar.model.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {}