#!/bin/sh
# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#

set -eu
set -o pipefail

[ "${PWGDEBUG:-}" = "true" ] && set -x

usage() {
  cat << EOF

  By default, this script generates a 12 letter random password with a mix
  of lower case, upper case, numeric, and special characters.

  Usage:

    $(basename $0) [ -l count ] [ -u count ] [ -d count ] [ -s count ] [ -n ] [ -? ]

    -l count : Number of lower case characters (default $l_count_default).
    -u count : Number of upper case characters (default $u_count_default).
    -d count : Number of digit characters (default $d_count_default).
    -s count : Number of special characters (default $s_count_default).

    -n       : Suppress printing a new-line after the generated password.

    -?       : This help.

  The special chars include: $special_chars_pretty

EOF
}

syntax_error() {
  echo "@@ Syntax Error: ${1:-} Pass '-?' for help."
}

l_count_default=6
u_count_default=2
d_count_default=2
s_count_default=2

l_count=$l_count_default
u_count=$u_count_default
d_count=$d_count_default
s_count=$s_count_default

print_newline=true

# Special Characters
#   special_chars_tr:  formatted suitable for "tr"
#   special_chars_pretty:  formatted suitable only for pretty printing
 
#   Limited set:
#     "Unquoted" Oracle DB passwords look like they only can contain _, $, and # when used
#     in DB scripts, and $ is a problem in shell scripts, which leaves us _, and #:

special_chars_tr='#_'
special_chars_pretty='#_'

#   Extended set:
#     These are a subset of special chars from the "OWASP" list
#     (at https://owasp.org/www-community/password-special-characters)
#     which are accepted by both Oracle Identity Manager and Microsoft Active Directory
#     per IDM Appendix B Special Characters
#     (at https://docs.oracle.com/cd/E11223_01/doc.910/e11197/app_special_char.htm):
#
#   special_chars_tr='@%+\\\/'\''!#$^?:,(){}[\]~`.\-_'
#   special_chars_pretty='@%+\/'\''!#$^?:,(){}[]~`.-_'
#

# process command line arguments

while [ ! "${1:-}" = "" ]; do
  case "$1" in
    -l|-u|-d|-s)
        count="${2:-}"
        case "$count" in
          ''|*[!0-9]*)
            syntax_error "The '$1' parameter expects a number, but got '${2:-}'."
            exit 1
            ;;
        esac
        case "$1" in
          -l) l_count=$count ;;
          -u) u_count=$count ;;
          -d) d_count=$count ;;
          -s) s_count=$count ;;
        esac
        shift
        shift
        ;;
    -n) print_newline=false
        shift
        ;;
    -\?) usage ; exit 0
        ;;
    *)  syntax_error "Unrecognized argument '$1'."
        exit 1
        ;;
  esac
done


random_chars() {

  #  this helper fn generates random characters,
  #  one per line, formatted as "$RANDOM <character>"
  #
  #  - arg1: number of lines
  #  - arg2: character set suitable for "tr" command

  local count=0
  while [ $count -lt $1 ]; do
    echo $RANDOM $(LC_ALL=C tr -dc "$2" < /dev/urandom | head -c 1)
    count=$((count + 1))
  done
}

# generate the password (the sort -n shuffles its chars)

(
  random_chars $l_count 'a-z'
  random_chars $u_count 'A-Z'
  random_chars $d_count '0-9'
  random_chars $s_count "$special_chars_tr"
) | sort -n | awk '{ print $2 }' | tr -d '\n'

[ "$print_newline" = "true" ] && echo
