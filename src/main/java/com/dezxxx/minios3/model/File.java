package com.dezxxx.minios3.model;

import com.dezxxx.minios3.model.status.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;


@Table("files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class File {

    @Id
   private Integer id;

   private Integer userId;

   private String name;

   private String location;

   private FileStatus status;

   @CreatedDate
   private LocalDateTime createdAt;

   private LocalDateTime deletedAt;
}
