package com.telusko.part29springsecex.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JWTService {
	
	private static final Logger log = LoggerFactory.getLogger(JWTService.class);

    private final SecretKey key;
    private final long tokenTtl;

    // La clave viene de jwt.token.secret (Base64, minimo 256 bits): asi los tokens
    // sobreviven a un reinicio y sirven en varias instancias. Nunca se escribe en el log.
    public JWTService(@Value("${jwt.token.secret}") String secret,
                      @Value("${jwt.token.expires:30}") long expiresMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.tokenTtl = expiresMinutes * 60 * 1000;
        log.info("JWT configurado con vigencia de {} minutos", expiresMinutes);
    }

    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return Jwts.builder()
                .claims()
                .add(claims)
                // jti unico: dos logins en el mismo segundo no producen el mismo token,
                // asi revocar uno en el logout no revoca el otro
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + tokenTtl))
                .and()
                .signWith(getKey())
                .compact();
        
        /*
        Cookie cookie = new Cookie("JWT", jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        response.addCookie(cookie);
        */
        
    }

    private SecretKey getKey() {
        return key;
    }

    public String extractUserName(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String userName = extractUserName(token);
        return (userName.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String getJwtFromCookie(HttpServletRequest request){
    	System.out.println("entro a ver la cookie");
        Cookie cookie = WebUtils.getCookie(request, "JWT");
        if(cookie != null){
            return cookie.getValue();
        }
        return null;
    }

    public void removeTokenFromCookie(HttpServletResponse response){
        Cookie cookie = new Cookie("JWT", null);
        cookie.setPath("/");

        response.addCookie(cookie);
    }
    
}
