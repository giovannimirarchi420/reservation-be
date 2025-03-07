package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for ResourceType entity operations
 */
@Repository
public interface ResourceTypeRepository extends JpaRepository<ResourceType, Long> {
    /**
     * Check if a resource type with given name exists
     */
    Boolean existsByName(String name);
}
