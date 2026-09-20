package com.telusko.part29springsecex.repo;

import com.telusko.part29springsecex.model.Role;
import com.telusko.part29springsecex.enums.RoleList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(RoleList name);
}
