package com.telusko.part29springsecex.service;

import com.telusko.part29springsecex.model.UserPrincipal;
import com.telusko.part29springsecex.model.Users;
import com.telusko.part29springsecex.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = userRepo.findByUsername(username);
        if (user == null) {
            System.out.println("User Not Found");
            throw new UsernameNotFoundException("user not found");
        }
        /*
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException(email + " not found." ));

        Set<GrantedAuthority> authorities = account
                .getRoles()
                .stream()
                .map((role) -> new SimpleGrantedAuthority(role.getErole().name()))
                .collect(Collectors.toSet());

        return new org.springframework.security.core.userdetails.User(
                account.getEmail(),
                account.getPassword(),
                authorities
        );
        */
        return new UserPrincipal(user);
    }
}
