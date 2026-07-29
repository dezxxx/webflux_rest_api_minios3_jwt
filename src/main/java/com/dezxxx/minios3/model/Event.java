package com.dezxxx.minios3.model;


import com.dezxxx.minios3.model.status.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;


@Table("events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder


public class Event {

    @Id
    private Integer id;

    private Integer userId;

    private  Integer fileId;

    private EventStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

}
