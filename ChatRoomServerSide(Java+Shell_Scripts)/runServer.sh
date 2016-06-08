#! /bin/bash

# A script to run the server

echo ">A server for the ChatRoom will be instatiated: "
echo ">Please enter the name of the server's file buffer: "
read -e fileBuffer
echo ">Please enter the name of the control file: "
read -e daemonController
echo ">Please enter the name of the log file: "
read -e logFile

echo "Instantiating server with the following: "
echo ">File Buffer: $fileBuffer"
echo ">Control File: $daemonController"
echo ">Server Log File: $logFile"

nohup java ChatRoom1 $fileBuffer $daemonController > "$logFile" 2> errors.txt < /dev/null &

PID=$!
echo ">File Buffer: $fileBuffer >Control File: $daemonController >Log File: $logFile >PID: $PID" > process_record.txt

echo ">A server instance has been created"
echo ">To monitor this server run the following command: "
echo "java ChatRoomDaemonController $logFile $daemonController"



echo '#! /bin/bash' > monitorServer.sh
monitorCommand="java ChatRoomDaemonController $logFile $daemonController"
echo "$monitorCommand" >> monitorServer.sh
echo '# Script ends. ' >> monitorServer.sh

echo '#! /bin/bash' > quitServer.sh
quitCommand="echo ""'"'$exit'"'|"
monitorCommand="java ChatRoomDaemonController $logFile $daemonController"
echo "$quitCommand""$monitorCommand" >> quitServer.sh
echo 'rm *.txt' >> quitServer.sh
echo '# Script ends. ' >> quitServer.sh

# Script ends. 
