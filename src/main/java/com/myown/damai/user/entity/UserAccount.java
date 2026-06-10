package com.myown.damai.user.entity;

import java.time.Instant;

public class UserAccount {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String phone;
    private UserStatus status = UserStatus.ACTIVE;
    private Instant createdAt;
    private Instant updatedAt;

    public UserAccount() {
    }

    public UserAccount(String username, String passwordHash, String nickname, String phone) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.phone = phone;
    }

    public UserAccount(
            Long id,
            String username,
            String passwordHash,
            String nickname,
            String phone,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.phone = phone;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
