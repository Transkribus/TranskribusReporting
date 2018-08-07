#!/bin/bash
BASEDIR=$(dirname $0)
java -DHOST_NAME=`hostname` -jar $BASEDIR/${appName}-${project.version}.jar $@
