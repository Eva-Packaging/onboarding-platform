package xyz.catuns.onboarding.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.user.domain.AppRole;
import xyz.catuns.onboarding.user.domain.AppRoleKey;

import java.util.Optional;
import java.util.UUID;

public interface AppRoleRepository extends JpaRepository<AppRole, UUID> {
    Optional<AppRole> findByRoleKey(AppRoleKey roleKey);
}