package com.example.market.customer.service;

import com.example.market.customer.dto.RegisterCustomerRequest;
import com.example.market.customer.entity.Customer;
import com.example.market.customer.entity.CustomerPasswordHistory;
import com.example.market.customer.repository.CustomerPasswordHistoryRepository;
import com.example.market.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;

@Service
public class RegisterCustomerService {

    private static final Logger log = LoggerFactory.getLogger(RegisterCustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerPasswordHistoryRepository historyRepository;
    private final BCryptPasswordEncoder encoder;
    private final int minAgeYears;
    private final int passwordMinLength;

    public RegisterCustomerService(CustomerRepository customerRepository,
                                   CustomerPasswordHistoryRepository historyRepository,
                                   BCryptPasswordEncoder encoder,
                                   @Value("${market.account.min-age-years:18}") int minAgeYears,
                                   @Value("${market.account.password-min-length:8}") int passwordMinLength) {
        this.customerRepository = customerRepository;
        this.historyRepository = historyRepository;
        this.encoder = encoder;
        this.minAgeYears = minAgeYears;
        this.passwordMinLength = passwordMinLength;
    }

    @Transactional
    public Customer register(RegisterCustomerRequest req) {
        validatePasswordMatch(req);
        validatePasswordPolicy(req.getPassword());
        validateAge(req.getBirthday());

        if (customerRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }

        Customer c = new Customer();
        c.setNameLast(req.getNameLast());
        c.setNameFirst(req.getNameFirst());
        c.setPostalCode(req.getPostalCode());
        c.setAddress(req.getAddress());
        c.setBirthday(req.getBirthday());
        c.setEmail(req.getEmail());
        c.setPasswordHash(encoder.encode(req.getPassword()));
        c.setPaymentMethod(req.getPaymentMethod());
        c.setCardToken(req.getCardToken());
        c.setActiveFlag(true);
        Customer saved = customerRepository.save(c);

        CustomerPasswordHistory history = new CustomerPasswordHistory();
        history.setCustomerId(saved.getId());
        history.setPasswordHash(saved.getPasswordHash());
        historyRepository.save(history);

        log.info("market customer registered id={} email={}", saved.getId(), maskEmail(saved.getEmail()));
        return saved;
    }

    private void validatePasswordMatch(RegisterCustomerRequest req) {
        if (!req.getPassword().equals(req.getPasswordConfirm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password mismatch");
        }
    }

    private void validatePasswordPolicy(String password) {
        if (password.length() < passwordMinLength
                || !password.chars().anyMatch(Character::isUpperCase)
                || !password.chars().anyMatch(Character::isLowerCase)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weak password");
        }
    }

    private void validateAge(LocalDate birthday) {
        if (Period.between(birthday, LocalDate.now()).getYears() < minAgeYears) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "under age limit");
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
