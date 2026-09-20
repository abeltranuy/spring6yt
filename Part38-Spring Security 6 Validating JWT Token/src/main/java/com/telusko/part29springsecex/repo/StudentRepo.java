package com.telusko.part29springsecex.repo;

import com.telusko.part29springsecex.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentRepo extends JpaRepository<Student, Integer> {

    Student findByName(String name);
}
