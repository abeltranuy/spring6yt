package com.telusko.part29springsecex.service;

import com.telusko.part29springsecex.enums.RoleList;
import com.telusko.part29springsecex.exception.UsernameAlreadyExistsException;
import com.telusko.part29springsecex.model.UserPrincipal;
import com.telusko.part29springsecex.model.Users;
import com.telusko.part29springsecex.repo.UserRepo;
import com.telusko.part29springsecex.request.AuthRequest;
import com.telusko.part29springsecex.response.AuthResponse;
//import com.web.backend.model.Role;
//import com.web.backend.model.RoleName;
//import com.web.backend.payload.response.JwtResponse;

import io.jsonwebtoken.JwtException;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);
	
    @Autowired
    private JWTService jwtService;

    @Autowired
    AuthenticationManager authManager;

    @Autowired
    private UserRepo repo;

	@Autowired
    private RedisService redisService;
	
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

    // Lanza AuthenticationException si el usuario no existe o el password no coincide;
    // GlobalExceptionHandler la convierte en un 401 con mensaje.
    public AuthResponse verify(AuthRequest request) {
        Authentication authentication = authManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<String> roles = principal.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        String jwtToken = jwtService.generateToken(principal.getUsername());
        return new AuthResponse(jwtToken, principal.getUsername(), roles);
    }

    public void logout(String token) {
        try {
            invalidateToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            // Token expirado, malformado o con firma invalida: ya no sirve, no hay nada que revocar
            log.debug("Logout con token no valido: {}", e.getMessage());
        }
    }
    
    /*
    public void logoutUser(HttpServletResponse response){
        jwtService.removeTokenFromCookie(response);
    }
	*/
    
    private void invalidateToken(String token) {
        long expirationTimeInMilliseconds = jwtService.extractExpiration(token).getTime() - System.currentTimeMillis();
        log.info("Token with remaining : TTL: {} ms", expirationTimeInMilliseconds);

        if(expirationTimeInMilliseconds > 0L){
            // La entrada caduca sola cuando el token hubiera expirado de todos modos
            redisService.setTokenWithTTL(token, "blacklisted", expirationTimeInMilliseconds, TimeUnit.MILLISECONDS);
        }
    }
    
}
