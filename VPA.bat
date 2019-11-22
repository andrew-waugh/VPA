@echo off
rem set code="C:\Users\Andrew\Documents\Work\VERSCode\VPA"
set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERSCode\VPA"
set versclasspath=%code%/dist/*
java -classpath %versclasspath% VPA.DAIngest -s %code%/support %*