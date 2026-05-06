package com.example.address.repository;

import com.example.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdAndIsActiveTrue(Long userId);
    List<Address> findByUserId(Long userId);
}
