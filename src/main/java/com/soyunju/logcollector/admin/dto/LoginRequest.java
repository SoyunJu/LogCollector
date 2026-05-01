package com.soyunju.logcollector.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "아이디를 입력해주세요.")
        @Size(max = 64, message = "아이디는 64자 이하여야 합니다.")
        String username,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Size(min = 8, max = 128, message = "비밀번호는 8자 이상 128자 이하여야 합니다.")
        String password
) {}