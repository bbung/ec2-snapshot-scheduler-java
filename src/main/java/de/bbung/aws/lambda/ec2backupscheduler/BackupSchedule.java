package de.bbung.aws.lambda.ec2backupscheduler;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;

public class BackupSchedule {

    private LocalTime   start;
    private Integer     retentionCount;
    private DayOfWeek[] days;


    public BackupSchedule() {
        // default Constructor
    };

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "BackupSchedule [start=" + this.start + ", retentionCount=" + this.retentionCount + ", days="
                + Arrays.toString( this.days ) + "]";
    }

    public BackupSchedule( final LocalTime localTime, final DayOfWeek[] days ) {
        this.start = localTime;
        this.days = days;
    }

    public void setStart( final LocalTime start ) {
        this.start = start;
    }

    public void setRetentionCount( final Integer retentionCount ) {
        this.retentionCount = retentionCount;
    }

    public void setDays( final DayOfWeek[] days ) {
        this.days = days;
    }

    public LocalTime getStart() {
        return this.start;
    }

    public Integer getRetentionCount() {
        return this.retentionCount;
    }

    public DayOfWeek[] getDays() {
        return this.days;
    }

}
