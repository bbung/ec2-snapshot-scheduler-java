package de.bbung.aws.lambda.ec2backupscheduler;


import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.amazonaws.services.lambda.runtime.Context;


/**
 * A simple test harness for locally invoking your Lambda function handler.
 */
public class LambdaFunctionHandlerTest {

    private static Object input;

    @BeforeClass
    public static void createInput() throws IOException {
        // TODO: set up your sample input object here.
        input = null;
    }

    private Context createContext() {
        TestContext ctx = new TestContext();

        // TODO: customize your context here if needed.
        ctx.setFunctionName( "Your Function Name" );

        return ctx;
    }

    @Test
    public void testLambdaFunctionHandler() {
        LambdaFunctionHandler handler = new LambdaFunctionHandler();
        Context ctx = createContext();

        // String output = handler.handleRequest( input, ctx );

        // TODO: validate output here if needed.
        // Assert.assertEquals( "Hello from Lambda!", output );

        DayOfWeek[] monday = { DayOfWeek.MONDAY };

        BackupSchedule schedule = new BackupSchedule( LocalTime.of( 12, 12 ), monday );
        // simulated Now
        LocalDateTime mondayLunch = LocalDateTime.of( 2017, 1, 2, 12, 00 ); // monday

        Assert.assertTrue( handler.isScheduledAt( mondayLunch, schedule ) );
        schedule.setStart( LocalTime.of( 12, 05 ) );
        Assert.assertTrue( handler.isScheduledAt( mondayLunch, schedule ) );
        schedule.setStart( LocalTime.of( 12, 16 ) );
        Assert.assertFalse( handler.isScheduledAt( mondayLunch, schedule ) );
        schedule.setStart( LocalTime.of( 11, 59 ) );
        Assert.assertFalse( handler.isScheduledAt( mondayLunch, schedule ) );
        schedule.setStart( LocalTime.of( 12, 00 ) );
        DayOfWeek[] tuesday = { DayOfWeek.TUESDAY };
        schedule.setDays( tuesday );
        Assert.assertFalse( handler.isScheduledAt( mondayLunch, schedule ) );
        Assert.assertFalse( handler.isScheduledAt( mondayLunch, schedule ) );
    }
}
