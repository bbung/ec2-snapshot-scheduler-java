# ec2-snapshot-scheduler-java

Java Version of the ec2-backup-scheduler provided by Amazon Web Services (AWS).
(work still in progress and progress may be slow as i do not have that much time at hand)


This is my first AWS Lambda project and mainly for education purpose. Feel free to use as-is or do what ever you want.

Other than the original python version from aws, this version will rely only on tags and has no dynamodb-settings-table.
You can set defaults by adding Environmentvariables:

Variablename|Description|default
---|---|---
BACKUP_SCHEDULER_TAG|todo|"ec2-backup-scheduler"
TIMEZONE|todo|"Europe/Berlin"
SCHEDULE_FREQUENCY|millis, has to match execution interval|900
ENABLE_COPY_TAGS|copies all tags|true
ENABLE_AUTO_DELETE|todo|false
DRYRUN|no actions will be taken, more logging|false