package com.xiaofuzi.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private Long userId;

    private String username;

    private String displayName;

    private String role;

    private String token;
}
