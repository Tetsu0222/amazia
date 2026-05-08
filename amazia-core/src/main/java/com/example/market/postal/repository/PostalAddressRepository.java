package com.example.market.postal.repository;

import com.example.market.postal.entity.PostalAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface PostalAddressRepository extends JpaRepository<PostalAddress, Long> {
    List<PostalAddress> findByPostalCode(String postalCode);

    /** Step 4-1: 整合性チェックの「MAX(updated_at)」取得用。 */
    @Query("SELECT MAX(p.updatedAt) FROM PostalAddress p")
    LocalDateTime findMaxUpdatedAt();
}
