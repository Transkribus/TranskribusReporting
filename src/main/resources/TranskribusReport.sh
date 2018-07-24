#!/bin/bash
java -DHOST_NAME=`hostname` -jar ${appName}-${project.version}.jar $@
