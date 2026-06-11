package com.myown.damai.program.entity;

import java.time.Instant;

/**
 * Represents one show time for a program.
 */
public class ProgramShowTime {

    public Long id;
    public Long programId;
    public Instant showTime;
    public Instant showDayTime;
    public String showWeekTime;
    public Long areaId;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
