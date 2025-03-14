package it.polito.cloudresources.be.mapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic mapper interface for converting between DTO and entity objects
 *
 * @param <D> The DTO type
 * @param <E> The entity type
 */
public interface EntityMapper<D, E> {
    
    /**
     * Convert a DTO to an entity
     * 
     * @param dto the DTO to convert
     * @return the equivalent entity
     */
    E toEntity(D dto);
    
    /**
     * Convert an entity to a DTO
     * 
     * @param entity the entity to convert
     * @return the equivalent DTO
     */
    D toDto(E entity);
    
    /**
     * Convert a list of DTOs to a list of entities
     * 
     * @param dtoList the list of DTOs to convert
     * @return the equivalent list of entities
     */
    default List<E> toEntity(List<D> dtoList) {
        if (dtoList == null) {
            return null;
        }
        return dtoList.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Convert a list of entities to a list of DTOs
     * 
     * @param entityList the list of entities to convert
     * @return the equivalent list of DTOs
     */
    default List<D> toDto(List<E> entityList) {
        if (entityList == null) {
            return null;
        }
        return entityList.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}