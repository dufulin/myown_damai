package com.myown.damai.program.entity;

import java.time.Instant;

/**
 * Represents a program category in d_program_category.
 */
public class ProgramCategory {

    public Long id;
    public Long parentId;
    public String name;
    public Integer type;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
