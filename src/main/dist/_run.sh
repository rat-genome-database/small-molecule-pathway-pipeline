#!/usr/bin/env bash
# gradle wrapper script to run SmallMoleculePathwayPipeline
. /etc/profile

APPNAME=SmallMoleculePathwayPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME

cd $APPDIR
pwd
DB_OPTS="-Dspring.config=$APPDIR/../properties/default_db.xml"
LOG4J_OPTS="-Dlog4j.configuration=file://$APPDIR/properties/log4j.properties"
export SMALL_MOLECULE_PATHWAY_PIPELINE_OPTS="$DB_OPTS $LOG4J_OPTS"

bin/$APPNAME "$@"
