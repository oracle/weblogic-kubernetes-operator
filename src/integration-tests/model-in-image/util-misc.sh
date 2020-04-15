# TBD  doc/copyright

function timestamp() {
  date --utc '+%Y-%m-%dT%H:%M:%S'
}

function trace() {
  echo @@ "[$(timestamp)]" "[$(basename $0):${BASH_LINENO[0]}]:" "$@"
}

function tracen() {
  echo -n @@ "[$(timestamp)]" "[$(basename $0):${BASH_LINENO[0]}]:" "$@"
}

# doCommand()
#
#  if DRY_RUN is not set
#    - runs command "$@" or in foreground
#    - if first parameter is ! -c
#      - redirects its stdout/stderr to a file:
#        - $WORKDIR/tmp/commmand_out/$PPID.$COUNT.$(timestamp).$(basename $1)
#      - prints out an Info with the name of the command and the location of this file
#      - follows info with 'dots' while it waits for command to complete
#    - if command fails, prints out an Error and exits non-zero
#    - if command succeeds, exits zero
#
#  if DRY_RUN is set to 'true'
#    - echos command to stdout prepended with 'dryrun: '
#
#  This function expects -e, -u, and -o pipefail to be set. If not, it returns 1.
#

function doCommand() {
  if [ "${SHELLOPTS/errexit}" = "${SHELLOPTS}" ]; then
    trace "Error: The doCommand script expects that 'set -e' was already called."
    return 1
  fi
  if [ "${SHELLOPTS/pipefail}" = "${SHELLOPTS}" ]; then
    trace "Error: The doCommand script expects that 'set -o pipefail' was already called."
    return 1
  fi
  if [ "${SHELLOPTS/nounset}" = "${SHELLOPTS}" ]; then
    trace "Error: The doCommand script expects that 'set -u' was already called."
    return 1
  fi

  local redirect=true
  if [ "${1:-}" = "-c" ]; then
    redirect=false 
    shift
  fi

  local command="$@"

  if [ "${DRY_RUN:-}" = "true" ]; then
    echo "dryrun: $command"
    return
  fi

  if [ "$redirect" = "false" ]; then
    trace "Info: Running command '$command':"
    eval $command
    return $?
  fi

  COMMAND_OUTFILE_COUNT=${COMMAND_OUTFILE_COUNT:=0}
  COMMAND_OUTFILE_COUNT=$((COMMAND_OUTFILE_COUNT + 1))

  mkdir -p $WORKDIR/command_out

  local out_file="$WORKDIR/command_out/$PPID.$(printf "%3.3u" $COMMAND_OUTFILE_COUNT).$(timestamp).$(basename $(printf $1)).out"
  tracen Info: Running command "'$command'," "output='$out_file'."
  printdots_start

  set +e
  eval $command > $out_file 2>&1
  local err_code=$?
  if [ $err_code -ne 0 ]; then
    echo
    trace "Error: Error running command '$command', output='$out_file'. Output contains:"
    cat $out_file
    trace "Error: Error running command '$command', output='$out_file'. Output dumped above."
  fi
  printdots_end
  set -e

  return $err_code
}
