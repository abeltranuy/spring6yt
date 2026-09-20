package com.telusko.part29springsecex.config;

import com.telusko.part29springsecex.service.JWTService;
import com.telusko.part29springsecex.service.MyUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {
	
	private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JWTService jwtService;

    @Autowired
    ApplicationContext context;

    @Autowired
    MyUserDetailsService myUserDetailsService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && context.getAuthentication() == null) {
            String token = authHeader.substring(7);
            //jwt = jwtService.getJwtFromCookie(request);
            try {
                String username = jwtService.extractUserName(token);
                if (username != null) {
                    UserDetails userDetails = myUserDetailsService.loadUserByUsername(username);

                    if (jwtService.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        context.setAuthentication(authToken);
                        log.debug("Token is still active for {}", username);
                    }
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                // Token expirado, malformado, con firma invalida o de un usuario borrado: la peticion sigue
                // sin autenticar. Las rutas publicas (/login) funcionan y las protegidas responden 401.
                log.debug("JWT rechazado: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
