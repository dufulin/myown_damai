package com.myown.damai.program.entity;

import java.time.Instant;

/**
 * Groups related programs and stores the nearest show time.
 */
public class ProgramGroup {

    public Long id;
    public String programJson;
    public Instant recentShowTime;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
