package com.dezxxx.minios3.model;

import com.dezxxx.minios3.model.status.Role;
import com.dezxxx.minios3.model.status.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;


@Table("users")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder



public class User {

    @Id
    private Integer id;

    private String username;

    private UserStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    private String passwordHash;

    private Role role;


}
