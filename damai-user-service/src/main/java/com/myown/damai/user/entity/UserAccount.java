package com.myown.damai.user.entity;

import com.myown.damai.common.auth.UserRole;
import java.time.Instant;

/**
 * Represents the core user record stored in the d_user table.
 */
public class UserAccount {

    private Long id;
    private String name;
    private String relName;
    private String mobile;
    private Integer gender = 1;
    private String passwordHash;
    private Integer emailStatus = 0;
    private String email;
    private Integer relAuthenticationStatus = 0;
    private String idNumber;
    private String address;
    private UserStatus status = UserStatus.ACTIVE;
    private UserRole role = UserRole.USER;
    private Instant createdAt;
    private Instant updatedAt;

    public UserAccount() {
    }

    public UserAccount(String name, String passwordHash, String mobile, String email) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.mobile = mobile;
        this.email = email;
    }

    public UserAccount(
            Long id,
            String name,
            String relName,
            String mobile,
            Integer gender,
            String passwordHash,
            Integer emailStatus,
            String email,
            Integer relAuthenticationStatus,
            String idNumber,
            String address,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.relName = relName;
        this.mobile = mobile;
        this.gender = gender;
        this.passwordHash = passwordHash;
        this.emailStatus = emailStatus;
        this.email = email;
        this.relAuthenticationStatus = relAuthenticationStatus;
        this.idNumber = idNumber;
        this.address = address;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelName() {
        return relName;
    }

    public void setRelName(String relName) {
        this.relName = relName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Integer getEmailStatus() {
        return emailStatus;
    }

    public void setEmailStatus(Integer emailStatus) {
        this.emailStatus = emailStatus;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getRelAuthenticationStatus() {
        return relAuthenticationStatus;
    }

    public void setRelAuthenticationStatus(Integer relAuthenticationStatus) {
        this.relAuthenticationStatus = relAuthenticationStatus;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    /**
     * Returns the account authorization role.
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * Sets the account authorization role.
     */
    public void setRole(UserRole role) {
        this.role = role == null ? UserRole.USER : role;
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

    /**
     * Returns the numeric status code used by the d_user table.
     */
    public int getStatusCode() {
        return status == UserStatus.ACTIVE ? 1 : 0;
    }

    /**
     * Keeps the old username response field compatible with existing frontend code.
     */
    public String getUsername() {
        return name;
    }

    /**
     * Keeps the old nickname response field compatible with existing frontend code.
     */
    public String getNickname() {
        return name;
    }

    /**
     * Keeps the old phone response field compatible with existing frontend code.
     */
    public String getPhone() {
        return mobile;
    }
}
