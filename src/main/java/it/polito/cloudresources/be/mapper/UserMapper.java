package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.model.User;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between User and UserDTO objects
 */
@Component
public class UserMapper implements EntityMapper<UserDTO, User> {
    
    @Override
    public User toEntity(UserDTO dto) {
        if (dto == null) {
            return null;
        }
        
        User user = new User();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        user.setKeycloakId(dto.getKeycloakId());
        user.setSshPublicKey(dto.getSshPublicKey());
        
        if (dto.getRoles() != null) {
            user.setRoles(dto.getRoles());
        }
        
        return user;
    }
    
    @Override
    public UserDTO toDto(User entity) {
        if (entity == null) {
            return null;
        }
        
        UserDTO dto = new UserDTO();
        dto.setId(entity.getId());
        dto.setUsername(entity.getUsername());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setEmail(entity.getEmail());
        dto.setAvatar(entity.getAvatar());
        dto.setKeycloakId(entity.getKeycloakId());
        dto.setSshPublicKey(entity.getSshPublicKey());
        dto.setRoles(entity.getRoles());
        
        return dto;
    }
}