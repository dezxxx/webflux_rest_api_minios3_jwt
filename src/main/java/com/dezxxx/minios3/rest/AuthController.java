package com.dezxxx.minios3.rest;

import com.dezxxx.minios3.dto.AuthenticationRequestDto;
import com.dezxxx.minios3.dto.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.RegistrationRequestDto;
import com.dezxxx.minios3.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping ("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<AuthenticationResponseDto> login (@Valid @RequestBody
        AuthenticationRequestDto request) {
        return authService.login(request);
    }

    @PostMapping ("/register")
    @ResponseStatus(HttpStatus.CREATED)

    public Mono<AuthenticationResponseDto> register (@Valid @RequestBody
        RegistrationRequestDto request) {
        return authService.register(request);
    }
}
