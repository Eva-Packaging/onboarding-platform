package xyz.catuns.onboarding.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.catuns.onboarding.service.domain.AppRole;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface AppRoleRepository extends JpaRepository<AppRole, UUID> {
    Set<AppRole> findByRoleKeyIn(Collection<String> roleKeys);
}
