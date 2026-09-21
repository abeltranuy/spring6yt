package com.telusko.part29springsecex.service;

import com.telusko.part29springsecex.enums.RoleList;
import com.telusko.part29springsecex.exception.UsernameAlreadyExistsException;
import com.telusko.part29springsecex.model.Users;
import com.telusko.part29springsecex.repo.UserRepo;
import com.telusko.part29springsecex.request.AuthRequest;
//import com.web.backend.model.Role;
//import com.web.backend.model.RoleName;
//import com.web.backend.payload.response.JwtResponse;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);
	
    @Autowired
    private UserRepo repo;

    @Autowired
    private PasswordEncoder encoder;

    public Users register(AuthRequest request) {
        if (repo.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException();
        }
        // Entidad nueva armada en el servidor: el id lo genera la BD y el rol siempre es ROLE_USER
        Users user = new Users();
        user.setUsername(request.username());
        user.setPassword(encoder.encode(request.password()));
        user.setRole(RoleList.ROLE_USER);

        /*
        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);

                        break;
                    case "pm":
                        Role modRole = roleRepository.findByName(RoleName.ROLE_PM)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(modRole);

                        break;
                    default:
                        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(userRole);
                }
            });
        }
        user.setRoles(roles);
        */
        
        try {
            return repo.save(user);
        } catch (DataIntegrityViolationException e) {
            // Dos registros simultaneos con el mismo username: el segundo choca con la restriccion unique
            throw new UsernameAlreadyExistsException();
        }
    }

}
