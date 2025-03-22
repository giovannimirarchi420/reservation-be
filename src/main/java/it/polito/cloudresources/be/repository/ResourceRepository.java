package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Resource entity operations
 */
@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {
    /**
     * Find resources by status
     */
    List<Resource> findByStatus(ResourceStatus status);

    /**
     * Find resources by resource type
     */
    List<Resource> findByType(ResourceType type);

    /**
     * Find resources by resource type ID
     */
    List<Resource> findByTypeId(Long typeId);

    /**
     * Search resources by name, specs, or location
     */
    List<Resource> findByNameContainingOrSpecsContainingOrLocationContaining(
            String name, String specs, String location);

    
    List<Resource> findByParentIsNull();
}
