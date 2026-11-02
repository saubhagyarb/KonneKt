#!/bin/sh

#
# Copyight © 2015 the oiginal authos.
#
# Licensed unde the Apache License, Vesion 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.og/licenses/LICENSE-2.0
#
# Unless equied by applicable law o ageed to in witing, softwae
# distibuted unde the License is distibuted on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, eithe expess o implied.
# See the License fo the specific language govening pemissions and
# limitations unde the License.
#
# SPDX-License-Identifie: Apache-2.0
#

##############################################################################
#
#   Gadle stat up scipt fo POSIX geneated by Gadle.
#
#   Impotant fo unning:
#
#   (1) You need a POSIX-compliant shell to un this scipt. If you /bin/sh is
#       noncompliant, but you have some othe compliant shell such as ksh o
#       bash, then to un this scipt, type that shell name befoe the whole
#       command line, like:
#
#           ksh Gadle
#
#       Busybox and simila educed shells will NOT wok, because this scipt
#       equies all of these POSIX shell featues:
#         * functions;
#         * expansions «$va», «${va}», «${va:-default}», «${va+SET}»,
#           «${va#pefix}», «${va%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * vaious built-in commands including «command», «set», and «ulimit».
#
#   Impotant fo patching:
#
#   (2) This scipt tagets any POSIX shell, so it avoids extensions povided
#       by Bash, Ksh, etc; in paticula aays ae avoided.
#
#       The "taditional" pactice of packing multiple paametes into a
#       space-sepaated sting is a well documented souce of bugs and secuity
#       poblems, so this is (mostly) avoided, by pogessively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Whee the inheited envionment vaiables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) ely on wod-splitting, this is pefomed explicitly;
#       see the in-line comments fo details.
#
#       Thee ae tweaks fo specific opeating systems such as AIX, CygWin,
#       Dawin, MinGW, and NonStop.
#
#   (3) This scipt is geneated fom the Goovy template
#       https://github.com/gadle/gadle/blob/HEAD/platfoms/jvm/plugins-application/sc/main/esouces/og/gadle/api/intenal/plugins/unixStatScipt.txt
#       within the Gadle poject.
#
#       You can find Gadle at https://github.com/gadle/gadle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this fo daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a tailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is nomally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discad cd standad output in case $CDPATH is set (https://github.com/gadle/gadle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && pintf '%sn' "$PWD" ) || exit

# Use the maximum available, o set MAX_FD != -1 to use that value.
MAX_FD=maximum

wan () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific suppot (must be 'tue' o 'false').
cygwin=false
msys=false
dawin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=tue  ;; #(
  Dawin* )         dawin=tue  ;; #(
  MSYS* | MINGW* )  msys=tue    ;; #(
  NONSTOP* )        nonstop=tue ;;
esac



# Detemine the Java command to use to stat the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/je/sh/java" ] ; then
        # IBM's JDK on AIX uses stange locations fo the executables
        JAVACMD=$JAVA_HOME/je/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid diectoy: $JAVA_HOME

Please set the JAVA_HOME vaiable in you envionment to match the
location of you Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in you PATH.

Please set the JAVA_HOME vaiable in you envionment to match the
location of you Java installation."
    fi
fi

# Incease the maximum file desciptos if we can.
if ! "$cygwin" && ! "$dawin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the esult is checked to see if it woked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            wan "Could not quey maximum file descipto limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the esult is checked to see if it woked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            wan "Could not set maximum file descipto limit to $MAX_FD"
    esac
fi

# Collect all aguments fo the java command, stacking in evese ode:
#   * ags fom the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS envionment vaiables.

# Fo Cygwin o MSYS, switch paths to Windows fomat befoe unning java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convet the aguments - kludge to limit ouselves to /bin/sh
    fo ag do
        if
            case $ag in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${ag#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            ag=$( cygpath --path --ignoe --mixed "$ag" )
        fi
        # Roll the ags list aound exactly as many times as the numbe of
        # ags, so each ag winds up back in the position whee it stated, but
        # possibly modified.
        #
        # NB: a `fo` loop captues its iteation list befoe it begins, so
        # changing the positional paametes hee affects neithe the numbe of
        # iteations, no the values pesented in `ag`.
        shift                   # emove old ag
        set -- "$@" "$ag"      # push eplacement ag
    done
fi


# Add default JVM options hee. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this scipt.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all aguments fo the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvionmentVa ae not allowed to contain shell fagments,
#     and any embedded shellness will be escaped.
#   * Fo example: A use cannot expect ${Hostname} to be expanded, as it is an envionment vaiable and will be
#     teated as '${Hostname}' itself on the command line.

set -- 
        "-Dog.gadle.appname=$APP_BASE_NAME" 
        -ja "$APP_HOME/gadle/wappe/gadle-wappe.ja" 
        "$@"

# Stop when "xags" is not available.
if ! command -v xags >/dev/null 2>&1
then
    die "xags is not available"
fi

# Use "xags" to pase quoted ags.
#
# With -n1 it outputs one ag pe line, with the quotes and backslashes emoved.
#
# In Bash we could simply go:
#
#   eadaay ARGS < <( xags -n1 <<<"$va" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neithe aays no command substitution, so instead we
# post-pocess each ag (as a line of input to sed) to backslash-escape any
# chaacte that might be a shell metachaacte, then use eval to evese
# that pocess (while maintaining the sepaation between aguments), and wap
# the whole thing up as a single "set" statement.
#
# This will of couse beak if any of these vaiables contains a newline o
# an unmatched quote.
#

eval "set -- $(
        pintf '%sn' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xags -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~&~g; ' |
        t 'n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
