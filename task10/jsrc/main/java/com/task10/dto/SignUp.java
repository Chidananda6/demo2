package com.task10.dto;

import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SignUp {
    private String firstname;
    private String lastname;
    private String email;
    private String password;
}