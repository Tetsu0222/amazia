package com.example.market.postal.controller;

import com.example.market.postal.dto.PostalAddressResponse;
import com.example.market.postal.service.SearchPostalAddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
public class SearchPostalAddressController {

    private final SearchPostalAddressService service;

    public SearchPostalAddressController(SearchPostalAddressService service) {
        this.service = service;
    }

    @GetMapping("/postal-addresses")
    public ResponseEntity<List<PostalAddressResponse>> search(@RequestParam("postal_code") String postalCode) {
        return ResponseEntity.ok(service.search(postalCode));
    }
}
