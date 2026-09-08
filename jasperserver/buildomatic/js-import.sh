#!/bin/bash
#
# script to run import command
#

# BUGFIX (audit BUG-04): -noverify is deprecated since JDK 13 and a JVM that
# removes it refuses to start. Add it only where it is still supported.
jrsNoVerifyFlag() {
  if [ -n "${JAVA_HOME}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    _jv=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -1 | cut -d '"' -f 2 | cut -d '.' -f1)
  else
    _jv=$(java -version 2>&1 | head -1 | cut -d '"' -f 2 | cut -d '.' -f1)
  fi
  if [ "${_jv:-0}" -le 12 ] 2>/dev/null; then printf %s " -noverify"; fi
}
export JAVA_OPTS="$JAVA_OPTS -Xms128m -Xmx512m$(jrsNoVerifyFlag)"

BASEDIR=$(dirname $0);export BASEDIR

JS_EXP_CMD_CLASS=com.jaspersoft.jasperserver.export.ImportCommand
export JS_EXP_CMD_CLASS

JS_CMD_NAME=$0
export JS_CMD_NAME
export ANT_ARGS="$ANT_ARGS -q -emacs -logger com.jaspersoft.buildomatic.ImportExportLogger -lib . -lib lib"
$BASEDIR/js-ant validate-database validate-keystore

if [ $? -eq 0 ];
then
  $BASEDIR/bin/js-import-export.sh $*
fi


