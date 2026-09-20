package com.telusko.part29springsecex.config;

import com.telusko.part29springsecex.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TokenBlackListFilter extends OncePerRequestFilter {
	
	private static final Logger log = LoggerFactory.getLogger(TokenBlackListFilter.class);

	@Autowired
    private RedisService redisService;

	@Autowired
    private JsonAuthErrorHandler errorHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if(authHeader != null && authHeader.startsWith("Bearer ") && redisService.hasToken(authHeader.substring(7))) {
            log.info("Token has been blacklisted.");
            errorHandler.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Token is blacklisted");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
