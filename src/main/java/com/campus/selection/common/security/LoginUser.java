package com.campus.selection.common.security;

import com.campus.selection.domain.Role;

import java.io.Serializable;

public record LoginUser(Long id, String studentNo, String name, Role role) implements Serializable {
}
