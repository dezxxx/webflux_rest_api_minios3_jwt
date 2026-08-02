package com.dezxxx.minios3.rest;

import com.dezxxx.minios3.dto.ErrorResponseDto;
import com.dezxxx.minios3.dto.auth.AuthenticationRequestDto;
import com.dezxxx.minios3.dto.auth.AuthenticationResponseDto;
import com.dezxxx.minios3.dto.auth.RefreshRequestDto;
import com.dezxxx.minios3.dto.auth.RegistrationRequestDto;
import com.dezxxx.minios3.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping ("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication",
     description = "Open endpoints: register, log in, renew the token pair. No token required.")
public class AuthControllerV1 {

    private final AuthService authService;

    @Operation(summary = "Log in and receive a token pair",
               description = "Checks the password against the stored bcrypt hash. "
                       + "A blocked account is refused even with the right password.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access and refresh tokens issued"),
            @ApiResponse(responseCode = "401",
                    description = "INVALID_CREDENTIALS — unknown user name or wrong password. "
                            + "The two are deliberately not told apart, so valid user names "
                            + "cannot be discovered by trying them.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "USER_BLOCKED — the account is not ACTIVE",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/login")
    public Mono<AuthenticationResponseDto> login (@Valid @RequestBody
        AuthenticationRequestDto request) {
        return authService.login(request);
    }

    @Operation(summary = "Register and log in at once",
               description = "The role is never read from the body — a new account is always "
                       + "an active USER. Tokens come back immediately, so no second call "
                       + "to /login is needed.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created, tokens issued"),
            @ApiResponse(responseCode = "409", description = "USERNAME_TAKEN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping ("/register")
    @ResponseStatus(HttpStatus.CREATED)

    public Mono<AuthenticationResponseDto> register (@Valid @RequestBody
        RegistrationRequestDto request) {
        return authService.register(request);
    }

    @Operation(summary = "Trade a refresh token for a fresh pair",
               description = "The only endpoint that reads the user row after login. That is why "
                       + "a changed role or a block takes effect here within one access-token "
                       + "lifetime, instead of waiting for the old token to expire.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A new pair issued"),
            @ApiResponse(responseCode = "401",
                    description = "EXPIRED_TOKEN — the refresh token ran out, log in again. "
                            + "INVALID_TOKEN — malformed, forged, or an access token was sent here.",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403",
                    description = "USER_BLOCKED — blocked since the token was issued",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/refresh")
    public Mono<AuthenticationResponseDto> refresh (@Valid @RequestBody
        RefreshRequestDto request) {
        return authService.refresh(request);
    }

    /**
     * Deliberately empty. A signed token cannot be recalled, and this application
     * stores none, so logging out is the client discarding both tokens.
     * The endpoint exists to document that contract, not to change server state.
     */
    @Operation(summary = "Log out — client-side only",
               description = "Does nothing on the server and always returns 204. A signed token "
                       + "is valid until it expires and nothing is stored that could mark one as "
                       + "dead, so logging out means the client discarding both tokens.")
    @ApiResponse(responseCode = "204", description = "Always")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout () {
        return Mono.empty();
    }
}