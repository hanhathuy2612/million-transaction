package com.hnh.example.transaction_example.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hnh.example.transaction_example.domain.Authority;

@Repository
public interface AuthorityRepository extends JpaRepository<Authority, String> {

}
