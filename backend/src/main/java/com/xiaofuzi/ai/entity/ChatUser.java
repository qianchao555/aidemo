package com.xiaofuzi.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatUser {

    private Long id;

    private String username;

    private String displayName;

    private LocalDateTime createTime;

    private String passwordHash;

    private String authToken;

    private String role;

    private String department;
}
