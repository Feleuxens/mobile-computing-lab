#!/bin/bash

FILE="${1:-adhoc.py}"

echo "Updating $FILE on all servers..."

sshpass -p 'oht1Ahs7' scp $FILE team10@129.69.210.60:/home/team10
echo "Updated 129.69.210.60"

sshpass -p 'oht1Ahs7' scp $FILE team10@129.69.210.61:/home/team10
echo "Updated 129.69.210.61"

sshpass -p 'oht1Ahs7' scp $FILE team10@129.69.210.62:/home/team10
echo "Updated 129.69.210.62"

sshpass -p 'oht1Ahs7' scp $FILE team10@129.69.210.63:/home/team10
echo "Updated 129.69.210.63"

sshpass -p 'oht1Ahs7' scp $FILE team10@129.69.210.64:/home/team10
echo "Updated 129.69.210.64"

sshpass -p 'oht1Ahs7' scp $FILE team10@129.69.210.65:/home/team10
echo "Updated 129.69.210.65"

sshpass -p 'oht1Ahs7' scp $FILE team10@129.69.210.66:/home/team10
echo "Updated 129.69.210.66"

