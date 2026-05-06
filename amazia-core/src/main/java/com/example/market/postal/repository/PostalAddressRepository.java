package com.example.market.postal.repository;

import com.example.market.postal.entity.PostalAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostalAddressRepository extends JpaRepository<PostalAddress, Long> {
    List<PostalAddress> findByPostalCode(String postalCode);
}
