package de.bbung.aws.lambda.ec2backupscheduler;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateSnapshotRequest;
import com.amazonaws.services.ec2.model.CreateSnapshotResult;
import com.amazonaws.services.ec2.model.DescribeSnapshotsRequest;
import com.amazonaws.services.ec2.model.DescribeSnapshotsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceBlockDeviceMapping;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Snapshot;
import com.amazonaws.services.ec2.model.SnapshotState;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.util.StringUtils;

public class LambdaFunctionHandler implements RequestHandler<Object, String> {

	private static final String ALL_OPTION = "all"; //$NON-NLS-1$
	private static final String WEEKDAYS_OPTION = "weekdays"; //$NON-NLS-1$
	private static final String NONE_OPTION = "none"; //$NON-NLS-1$
	private final boolean DEBUG = true;

	private static LambdaLogger lambdaLogger;
	private LocalTime defaultStart;

	private int schedule_frequenzy;
	private String backup_schedule_tag = "ebs-backup-schedule";
	private boolean dryrun;
	private DayOfWeek[] defaultDays = null;

	private String timezone;
	private boolean enable_copy_tags;
	private boolean enable_auto_delete;

	private AmazonEC2 ec2;

	@Override
	public String handleRequest(final Object input, final Context context) {
		setLambdaLogger(context.getLogger());
		respectEnvSettingsIfPresent();

		TimeZone.setDefault(TimeZone.getTimeZone(timezone));

		log("Running with Timezone: " + TimeZone.getDefault().getDisplayName());

		this.ec2 = buildEC2Client();

		List<String> tagNames = new ArrayList<>();
		tagNames.add("name");

		List<Instance> instancesToBackup = new ArrayList<>();

		for (Reservation reservation : this.ec2.describeInstances().getReservations()) {
			for (Instance instance : reservation.getInstances()) {
				BackupSchedule schedule = new BackupSchedule();

				for (Tag tag : instance.getTags()) {
					if (tag.getKey().equalsIgnoreCase(backup_schedule_tag)) {
						log("Found Backup Tag on " + instance.getInstanceId() + " trying to parse value: " + tag.getValue());
						schedule = parseBackupScheduleTag(tag.getValue());
						log(schedule.toString());
					}
				}

				if (isScheduledNow(schedule)) {
					createAndTagSnapshots(instancesToBackup, tagNames);
				}
				if (enable_auto_delete) {
					deleteSnapshots(instance, schedule.getRetentionCount());
				}
			}
		}

		return "Done!";
	}

	private void respectEnvSettingsIfPresent() {
		this.backup_schedule_tag = StringUtils.isNullOrEmpty(System.getenv("BACKUP_SCHEDULER_TAG")) ?  "ebs-backup-schedule" : System.getenv("BACKUP_SCHEDULER_TAG");
		
		this.timezone = StringUtils.isNullOrEmpty(System.getenv("TIMEZONE")) ? "Europe/Berlin" : System.getenv("TIMEZONE"); //$NON-NLS-1$
		this.schedule_frequenzy = StringUtils.isNullOrEmpty("SCHEDULE_FREQUENCY") ? 900 : Integer.valueOf(System.getenv("SCHEDULE_FREQUENCY"));
		
		this.dryrun = StringUtils.isNullOrEmpty("DRYRUN") ? false : Boolean.valueOf("DRYRUN");

		this.enable_copy_tags = StringUtils.isNullOrEmpty("ENABLE_COPY_TAGS") ? true : Boolean.valueOf(System.getenv("ENABLE_COPY_TAGS"));
		this.enable_auto_delete = StringUtils.isNullOrEmpty("ENABLE_AUTO_DELETE") ? false : Boolean.valueOf(System.getenv("ENABLE_AUTO_DELETE"));
	
		this.defaultDays = null;
	}

	private void deleteSnapshots(final Instance instance, final Integer retentionCount) {

		for (InstanceBlockDeviceMapping blockDeviceMapping : instance.getBlockDeviceMappings()) {

			Filter volumeIdFilter = new Filter().withName("volume-id")
					.withValues(blockDeviceMapping.getEbs().getVolumeId());
			DescribeSnapshotsRequest describeRequest = new DescribeSnapshotsRequest().withFilters(volumeIdFilter);
			DescribeSnapshotsResult describeSnapshots = this.ec2.describeSnapshots(describeRequest);

			List<Snapshot> completedSnapshots = describeSnapshots.getSnapshots().stream()
					.filter(s -> s.getState().equals(SnapshotState.Completed)).collect(Collectors.toList());

			if (completedSnapshots.size() > retentionCount) {
				log("Completed Snapshot count:" + completedSnapshots.size());
				Collections.sort(completedSnapshots, new SnaphotCreationDateComperator());
				for (int i = retentionCount; i < completedSnapshots.size(); i++) {
					log("Will delete Snapshot " + completedSnapshots.get(i).getSnapshotId());
				}
			}
		}
	}

	public class SnaphotCreationDateComperator implements Comparator<Snapshot> {
		@Override
		public int compare(final Snapshot snap1, final Snapshot snap2) {
			return snap2.getStartTime().compareTo(snap1.getStartTime());
		}
	}

	private void createAndTagSnapshots(final List<Instance> instancesToBackup, final List<String> tagNames) {
		for (Instance instance : instancesToBackup) {
			for (InstanceBlockDeviceMapping blockDeviceMapping : instance.getBlockDeviceMappings()) {

				String description = "EBSSS: Snapshot from " + instance.getInstanceId() + "Device: "
						+ blockDeviceMapping.getDeviceName();
				CreateSnapshotRequest request = new CreateSnapshotRequest(blockDeviceMapping.getEbs().getVolumeId(),
						description);
				if (dryrun) {
					log("DRY-RUN: Would create Snapshot with description:" + description);
				} else {
					CreateSnapshotResult snapshotResult = buildEC2Client().createSnapshot(request);
					if (enable_copy_tags) {
						addTags(instance, snapshotResult.getSnapshot(), tagNames);
					}
				}
			}
		}
	}

	private void addTags(final Instance instance, final Snapshot snapshot, final List<String> tagKeys) {

		List<Tag> instanceTags = instance.getTags();
		Collection<Tag> tagsToAdd = new ArrayList<>();

		for (String tagKey : tagKeys) {
			for (Tag instanceTag : instanceTags) {
				if (instanceTag.getKey().equals(tagKey) && !instanceTag.getValue().isEmpty()) {
					tagsToAdd.add(new Tag(tagKey, instanceTag.getValue()));
				}
			}
		}
		if (!tagsToAdd.isEmpty()) {
			List<Tag> tags = snapshot.getTags();
			tags.addAll(tagsToAdd);
			snapshot.setTags(tags);
		}
	}

	/*
	 * StartTime;RetentionCount;Weekdays,,
	 */
	private BackupSchedule parseBackupScheduleTag(final String value) {
		BackupSchedule backupSchedule = new BackupSchedule();

		String[] tagValues = value.split(";");

		if (tagValues.length > 0) {
			LocalTime startTime = parseStart(tagValues[0].toString());
			if (startTime != null) {
				backupSchedule.setStart(startTime);
			}
		}
		if (tagValues.length > 1) {
			backupSchedule.setRetentionCount(Integer.valueOf(tagValues[1]));
		}
		if (tagValues.length > 2) {
			backupSchedule.setDays(parseDays(tagValues[2], defaultDays));
		}

		return backupSchedule;
	}

	private LocalTime parseStart(final String startTime) {
		String trimedTime = StringUtils.trim(startTime);

		if ((trimedTime == null) || trimedTime.isEmpty()) { // $NON-NLS-1$
			return this.defaultStart;
		}

		return parseTime(trimedTime);
	}

	private LocalTime parseTime(final String trimedTime) {
		if (NONE_OPTION.equals(trimedTime)) {
			return null;
		}

		try {
			return LocalTime.parse(trimedTime, DateTimeFormatter.ofPattern("HHmm")); //$NON-NLS-1$
		} catch (DateTimeParseException ex) {
			getLambdaLogger().log(ex.getMessage());
			return null;
		}
	}

	private AmazonEC2 buildEC2Client() {
		ClientConfiguration clientConfiguration = new ClientConfiguration();
		// clientConfiguration.setProxyHost(null);
		// clientConfiguration.setProxyPort(null);

		return AmazonEC2ClientBuilder.standard().withClientConfiguration(clientConfiguration).build();
	}

	private boolean isScheduledNow(final BackupSchedule schedule) {
		return isScheduledAt(LocalDateTime.now(), schedule);
	}

	public boolean isScheduledAt(final LocalDateTime now, final BackupSchedule schedule) {
		
		boolean fittingDay = Arrays.asList(schedule.getDays()).contains(now.getDayOfWeek());
		LocalTime currentTime = LocalTime.of(now.getHour(), now.getMinute());
		long timeBetween = currentTime.until(schedule.getStart(), ChronoUnit.SECONDS);
		boolean fittingTime = (timeBetween >= 0) && (timeBetween <= schedule_frequenzy);
		boolean isScheduledNow = fittingDay && fittingTime;
		
		log("Checking if scheduled: currentTime= " + now.toString() + " Schedule:  " + schedule.toString() +  " ScheduleNow: " + isScheduledNow);
		return isScheduledNow;
	}

	public DayOfWeek[] parseDays(final String days,DayOfWeek[] defaultDays) {
		String trimedDays = StringUtils.trim(days);

		//TODO default settings should play no role here
		if ((trimedDays == null) || "".equals(trimedDays) || WEEKDAYS_OPTION.equals(trimedDays)) { //$NON-NLS-1$
			return defaultDays;
		}

		if (ALL_OPTION.equals(trimedDays)) {
			return DayOfWeek.values();
		}

		return DateHelper.parseDaysFromStringArray(trimedDays.split(",")); //$NON-NLS-1$
	}

	private void log(final String log) {
		if (DEBUG) {
			getLambdaLogger().log(log + "\n");
		}
	}

	private void setLambdaLogger(final LambdaLogger logger) {
		LambdaFunctionHandler.lambdaLogger = logger;
	}

	public static LambdaLogger getLambdaLogger() {
		return lambdaLogger;
	}

}
