package com.myown.damai.user.entity;

import java.time.Instant;

/**
 * Represents a real-name ticket buyer stored in the d_ticket_user table.
 */
public class TicketUser {

    private Long id;
    private Long userId;
    private String relName;
    private Integer idType = 1;
    private String idNumber;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer status = 1;

    /**
     * Returns the ticket buyer primary key.
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the ticket buyer primary key.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the owner user id.
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Sets the owner user id.
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Returns the real name of the ticket buyer.
     */
    public String getRelName() {
        return relName;
    }

    /**
     * Sets the real name of the ticket buyer.
     */
    public void setRelName(String relName) {
        this.relName = relName;
    }

    /**
     * Returns the identity document type.
     */
    public Integer getIdType() {
        return idType;
    }

    /**
     * Sets the identity document type.
     */
    public void setIdType(Integer idType) {
        this.idType = idType;
    }

    /**
     * Returns the identity document number.
     */
    public String getIdNumber() {
        return idNumber;
    }

    /**
     * Sets the identity document number.
     */
    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    /**
     * Returns the create time.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the create time.
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the update time.
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the update time.
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the logical status.
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * Sets the logical status.
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
}
