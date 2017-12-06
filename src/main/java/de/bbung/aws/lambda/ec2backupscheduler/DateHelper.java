package de.bbung.aws.lambda.ec2backupscheduler;


import java.time.DayOfWeek;

import com.amazonaws.util.StringUtils;


public class DateHelper {

    private DateHelper() {
        // Hide
    }

    private static DayOfWeek getWeekday( final String day ) {
        switch ( day ) {
            case "mo": //$NON-NLS-1$
                return DayOfWeek.MONDAY;
            case "tu"://$NON-NLS-1$
                return DayOfWeek.TUESDAY;
            case "we"://$NON-NLS-1$
                return DayOfWeek.WEDNESDAY;
            case "th"://$NON-NLS-1$
                return DayOfWeek.THURSDAY;
            case "fr"://$NON-NLS-1$
                return DayOfWeek.FRIDAY;
            case "sa"://$NON-NLS-1$
                return DayOfWeek.SATURDAY;
            case "su"://$NON-NLS-1$
                return DayOfWeek.SUNDAY;
            default:
                return null;
        }

    }

    public static DayOfWeek[] parseDaysFromStringArray( final String[] days ) {
        DayOfWeek[] resultDays = new DayOfWeek[days.length];

        for ( int i = 0; i < days.length; i++ ) {
            String trimedDay = StringUtils.trim( days[i] );
            String dayShort = trimedDay.substring( 0, 2 ).toLowerCase();

            resultDays[i] = getWeekday( dayShort );
        }

        return resultDays;
    }

}
