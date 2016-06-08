#! /bin/bash
toReplace=$(grep 'final String ip' $1)
#echo $toReplace
toReplace=${toReplace%\"*}
#echo $toReplace
toReplace=${toReplace#*\"}
echo 'IP in file: '$toReplace
newIP=$(ipconfig getifaddr en0)
echo 'newIP: '$newIP
sed -i ""  "s/$toReplace/$newIP/g" $1

