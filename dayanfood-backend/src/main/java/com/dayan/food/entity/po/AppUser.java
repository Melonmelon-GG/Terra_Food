package com.dayan.food.entity.po;

import com.dayan.food.entity.enums.SignatureStatus;
import com.dayan.food.entity.enums.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class AppUser {

    private Long id;

    private String username;

    private String password;

    private String displayName;

    private String email;

    private String avatarUrl;

    private String signature;

    private String signaturePending;

    private SignatureStatus signatureStatus;

    private UserRole role;

    private boolean active;

    private LocalDateTime lastFoodUploadAt;

    private LocalDateTime createdAt;

    public AppUser(String username, String password, String displayName, UserRole role) {
        this(username, password, displayName, null, role);
    }

    public AppUser(String username, String password, String displayName, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }
}
