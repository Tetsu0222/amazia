package com.example.market.postal.service;

import com.example.market.postal.dto.PostalAddressResponse;
import com.example.market.postal.repository.PostalAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SearchPostalAddressService {

    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("^\\d{7}$");

    private final PostalAddressRepository repository;

    public SearchPostalAddressService(PostalAddressRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PostalAddressResponse> search(String rawPostalCode) {
        String normalized = normalize(rawPostalCode);
        if (normalized == null) {
            return Collections.emptyList();
        }
        return repository.findByPostalCode(normalized).stream()
                .map(PostalAddressResponse::from)
                .toList();
    }

    private String normalize(String raw) {
        if (raw == null) return null;
        String stripped = raw.replace("-", "").trim();
        if (!POSTAL_CODE_PATTERN.matcher(stripped).matches()) {
            return null;
        }
        return stripped;
    }
}
