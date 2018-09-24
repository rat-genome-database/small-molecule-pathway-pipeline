# runs the pipeline

. /etc/profile
APPDIR=/home/rgddata/pipelines/SmallMoleculePathwayPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu
if [ "$SERVER" = "REED" ]; then
  EMAIL_LIST=mtutaj@mcw.edu
fi

$APPDIR/_run.sh 2>&1 > $APPDIR/run.log

mailx -s "[$SERVER] Small Molecule Pathway Pipeline OK" $EMAIL_LIST < $APPDIR/run.log
mailx -s "[$SERVER] Small Molecule Pathway Pipeline - unmatching SMPDB ids" $EMAIL_LIST < $APPDIR/data/unmatchingSmpdbIds.txt
