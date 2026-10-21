package ro.axonsoft.services.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.axonsoft.services.user.model.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}
