#! /bin/bash
echo '$exit'|java ChatRoomDaemonController ServerLog.txt DaemonControl.txt
rm *.txt
# Script ends. 
