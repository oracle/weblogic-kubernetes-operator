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

# consult with Derek - this probably isn't safe!
function cleanDanglingDockerImages() {
  trace "Cleaning dangling docker images (if any)."
  if [ ! -z "$(docker images -f "dangling=true" -q)" ]; then
    docker rmi -f $(docker images -f "dangling=true" -q)
  fi
}

# this function runs command "$@" in foreground, redirecting its stdout/stderr to a file
# out file name = $WORKDIR/tmp/commmand_out/$PPID.$(timestamp).$(basename $1)
# prints out an Info with the name of the command and the location of this file
# follows info with 'dots' while it waits for command to complete
# if command fails, prints out an Error and exits non-zero
# if command succeeds, exits zero
# TBD discuss DRY_RUN
# assumes set -e is already set! TBD check if it's set 

function doCommand() {
  local command="$@"
  local out_file="$WORKDIR/command_out/$PPID.$(timestamp).$(basename $1).out"

  if [ $DRY_RUN ]; then
    echo "dryrun: $command"
    return
  fi

  tracen Info Running command "'$command'," "output='$out_file'."
  printdots_start
  (
    set +e
    eval $command > $out_file 2>&1
    local err_code=$?
    if [ $err_code -ne 0 ]; then
      echo
      trace "Error Running command '$command', output='$out_file'. Output contains:"
      cat $out_file
      trace "Error Running command '$command', output='$out_file'. Output dumped above."
    fi
    set -e
    exit $err_code
  )
  (
    # printdots_end non zero sometimes - not sure why, so disable error checking
    set +e
    printdots_end
  )
}

