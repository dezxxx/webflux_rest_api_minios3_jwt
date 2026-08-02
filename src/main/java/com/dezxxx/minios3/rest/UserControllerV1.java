package com.dezxxx.minios3.rest;


import com.dezxxx.minios3.dto.ErrorResponseDto;
import com.dezxxx.minios3.dto.user.UserCreateRequestDto;
import com.dezxxx.minios3.dto.user.UserResponseDto;
import com.dezxxx.minios3.dto.user.UserUpdateRequestDto;
import com.dezxxx.minios3.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping ("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users",
     description = "Account management. Every endpoint needs a token; most need a role. "
             + "Deleted accounts are hidden from every read, so they answer 404.")
public class UserControllerV1 {

    private final UserService  userService;

    @Operation(summary = "Read your own account",
               description = "The caller is identified by the token, so there is no id to pass "
                       + "and nothing to check ownership against. This is how a plain USER "
                       + "reads their own row — /users/{id} is closed to them.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Your account"),
            @ApiResponse(responseCode = "401", description = "No token, or the token is not an access token",
                    content = @Content)
    })
    @GetMapping( "/me")
    public Mono<UserResponseDto> getCurrentUser(@AuthenticationPrincipal String username) {
        return userService.getByUsername(username);
    }

    @Operation(summary = "List every account",
               description = "ADMIN and MODERATOR only — this is the specification's "
                       + "\"MODERATOR may read all users\". Soft-deleted rows are left out.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All active accounts"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED — a plain USER asked",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping
    @PreAuthorize ("hasAnyRole ('ADMIN', 'MODERATOR')")
    public Flux<UserResponseDto> getAllUsers() {
        return userService.getAll();
    }

    @Operation(summary = "Read one account by id",
               description = "ADMIN and MODERATOR only. A plain USER reads themselves through "
                       + "/users/me instead, which needs no ownership check.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The account"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "USER_NOT_FOUND — no such id, or the account was deleted",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole ('ADMIN', 'MODERATOR') ")
    public Mono<UserResponseDto> getUserById (
            @Parameter(description = "Primary key of the account", example = "1")
            @PathVariable Integer userId) {
        return  userService.getById(userId);
    }

    @Operation(summary = "Create an account with any role",
               description = "Unlike /auth/register, the role is taken from the body — safe "
                       + "because only an ADMIN reaches this method. This is the one way a "
                       + "MODERATOR comes to exist. The new account is always ACTIVE.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "403", description = "ACCESS_DENIED — caller is not an ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "USERNAME_TAKEN",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping
    @PreAuthorize(" hasRole ('ADMIN') ")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponseDto> createUser (@Valid @RequestBody UserCreateRequestDto requestDto){
        return userService.create(requestDto);
    }

    @Operation(summary = "Replace an account",
               description = "A PUT, so all three fields are written at once. This is where a "
                       + "role is changed and where an account is blocked. An administrator "
                       + "may not take ADMIN away from themselves or block themselves — there "
                       + "would be nobody left able to undo it. The new role reaches that "
                       + "user's token at their next /auth/refresh.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "403",
                    description = "ACCESS_DENIED — not an ADMIN, or an ADMIN demoting or "
                            + "blocking their own account",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/{userId}")
    @PreAuthorize(" hasRole('ADMIN') ")
    public Mono<UserResponseDto> updateUser (
            @Parameter(description = "Primary key of the account to replace", example = "2")
            @PathVariable Integer userId,
            @Valid @RequestBody UserUpdateRequestDto requestDto,
            @AuthenticationPrincipal String currentUsername) {
        return userService.update(userId, requestDto,  currentUsername);
    }


    @Operation(summary = "Delete an account",
               description = "Soft delete: the row stays and only deleted_at is set, because the "
                       + "data is kept for later analysis. The account then answers 404 "
                       + "everywhere and can no longer log in. An administrator may not delete "
                       + "their own account.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(responseCode = "403",
                    description = "ACCESS_DENIED — not an ADMIN, or an ADMIN deleting themselves",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping ("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteUser (
            @Parameter(description = "Primary key of the account to delete", example = "3")
            @PathVariable Integer userId,
            @AuthenticationPrincipal String currentUsername) {
        return userService.delete(userId, currentUsername);
    }
}