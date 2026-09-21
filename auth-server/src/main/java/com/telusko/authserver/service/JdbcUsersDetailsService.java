package com.telusko.authserver.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Lee la misma tabla users que llena la app de Part38 con POST /registrar.
// Las claves estan cifradas con BCrypt; este servidor solo las compara, nunca las escribe.
@Service
public class JdbcUsersDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbc;

    public JdbcUsersDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<UserDetails> found = jdbc.query(
                "select username, password, role from users where username = ?",
                (rs, rowNum) -> User.withUsername(rs.getString("username"))
                        .password(rs.getString("password"))
                        .authorities(rs.getString("role") == null ? "ROLE_USER" : rs.getString("role"))
                        .build(),
                username);
        if (found.isEmpty()) {
            throw new UsernameNotFoundException("user not found");
        }
        return found.get(0);
    }
}
