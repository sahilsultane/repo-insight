package com.githubrepoinsight.service;

import com.githubrepoinsight.dto.AuthRequest;
import com.githubrepoinsight.dto.AuthResponse;
import com.githubrepoinsight.dto.RegisterRequest;
import com.githubrepoinsight.exception.AuthException;
import com.githubrepoinsight.model.User;
import com.githubrepoinsight.repository.UserRepository;
import com.githubrepoinsight.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AuthException("Username '" + request.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email '" + request.getEmail() + "' is already registered.");
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role("ROLE_USER")
            .build();

        userRepository.save(user);
        log.info("New user registered: {}", request.getUsername());

        String token = jwtService.generateToken(user.getUsername());
        return AuthResponse.builder()
            .token(token)
            .username(user.getUsername())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
    }

    public AuthResponse login(AuthRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            throw new AuthException("Invalid username or password.");
        }

        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new AuthException("User not found."));

        String token = jwtService.generateToken(user.getUsername());
        return AuthResponse.builder()
            .token(token)
            .username(user.getUsername())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
    }
}
