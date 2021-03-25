:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

SCRIPT_VERSION=dotnet-cmd-v1
COMPANY_DIR="JetBrains"
TARGET_DIR="${TEMPDIR:-/tmp}/$COMPANY_DIR"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

if [ "$darwin" = "true" ]; then
    DOTNET_TEMP_FILE=$TARGET_DIR/dotnet-sdk-5.0.200-osx-x64.tar.gz
    DOTNET_URL=https://download.visualstudio.microsoft.com/download/pr/db160ec7-2f36-4f41-9a87-fab65cd142f9/7d4afadf1808146ba7794edaf0f97924/dotnet-sdk-5.0.200-osx-x64.tar.gz
    DOTNET_TARGET_DIR=$TARGET_DIR/dotnet-sdk-5.0.200-osx-x64-$SCRIPT_VERSION
else
    DOTNET_TEMP_FILE=$TARGET_DIR/dotnet-sdk-5.0.200-linux-x64.tar.gz
    DOTNET_URL=https://download.visualstudio.microsoft.com/download/pr/64a6b4b9-a92e-4efc-a588-569d138919c6/a97f4be78d7cc237a4f5c306866f7a1c/dotnet-sdk-5.0.200-linux-x64.tar.gz
    DOTNET_TARGET_DIR=$TARGET_DIR/dotnet-sdk-5.0.200-linux-x64-$SCRIPT_VERSION
fi

set -e

if [ -e "$DOTNET_TARGET_DIR/.flag" ] && [ -n "$(ls "$DOTNET_TARGET_DIR")" ] && [ "x$(cat "$DOTNET_TARGET_DIR/.flag")" = "x${DOTNET_URL}" ]; then
    # Everything is up-to-date in $DOTNET_TARGET_DIR, do nothing
    true
else
  warn "Downloading $DOTNET_URL to $DOTNET_TEMP_FILE"

  rm -f "$DOTNET_TEMP_FILE"
  mkdir -p "$TARGET_DIR"
  if command -v curl >/dev/null 2>&1; then
      if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
      curl $CURL_PROGRESS --output "${DOTNET_TEMP_FILE}" "$DOTNET_URL"
  elif command -v wget >/dev/null 2>&1; then
      if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
      wget $WGET_PROGRESS -O "${DOTNET_TEMP_FILE}" "$DOTNET_URL"
  else
      die "ERROR: Please install wget or curl"
  fi

  warn "Extracting $DOTNET_TEMP_FILE to $DOTNET_TARGET_DIR"
  rm -rf "$DOTNET_TARGET_DIR"
  mkdir -p "$DOTNET_TARGET_DIR"

  tar -x -f "$DOTNET_TEMP_FILE" -C "$DOTNET_TARGET_DIR"
  rm -f "$DOTNET_TEMP_FILE"

  echo "$DOTNET_URL" >"$DOTNET_TARGET_DIR/.flag"
fi

if [ '!' -e "$DOTNET_TARGET_DIR/dotnet" ]; then
  die "Unable to find dotnet under $DOTNET_TARGET_DIR"
fi

exec "$DOTNET_TARGET_DIR/dotnet" "$@"

:CMDSCRIPT

SETLOCAL
SET SCRIPT_VERSION=dotnet-cmd-v1
SET COMPANY_NAME=JetBrains
SET TARGET_DIR=%LOCALAPPDATA%\Temp\%COMPANY_NAME%\
SET DOTNET_TARGET_DIR=%TARGET_DIR%dotnet-sdk-5.0.200-win-x64-%SCRIPT_VERSION%\
SET DOTNET_TEMP_FILE=dotnet-sdk-5.0.200-win-x64.zip
SET DOTNET_URL=https://download.visualstudio.microsoft.com/download/pr/761159fa-2843-4abe-8052-147e6c873a78/77658948a9e0f7bc31e978b6bc271ec8/dotnet-sdk-5.0.200-win-x64.zip


SET POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

IF NOT EXIST "%DOTNET_TARGET_DIR%" MD "%DOTNET_TARGET_DIR%"

IF NOT EXIST "%DOTNET_TARGET_DIR%.flag" GOTO DOWNLOAD_AND_EXTRACT_DOT_NET

SET /P CURRENT_FLAG=<"%DOTNET_TARGET_DIR%.flag"
IF "%CURRENT_FLAG%" == "%DOTNET_URL%" GOTO CONTINUE_WITH_DOT_NET

:DOWNLOAD_AND_EXTRACT_DOT_NET

CD /D "%TARGET_DIR%"
IF ERRORLEVEL 1 GOTO FAIL

ECHO Downloading %DOTNET_URL% to %TARGET_DIR%%DOTNET_TEMP_FILE%
IF EXIST "%DOTNET_TEMP_FILE%" DEL /F "%DOTNET_TEMP_FILE%"
"%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; (New-Object Net.WebClient).DownloadFile('%DOTNET_URL%', '%DOTNET_TEMP_FILE%')"
IF ERRORLEVEL 1 GOTO FAIL

RMDIR /S /Q "%DOTNET_TARGET_DIR%"
IF ERRORLEVEL 1 GOTO FAIL

MKDIR "%DOTNET_TARGET_DIR%"
IF ERRORLEVEL 1 GOTO FAIL

CD /D %DOTNET_TARGET_DIR%"
IF ERRORLEVEL 1 GOTO FAIL

ECHO Extracting %TARGET_DIR%%DOTNET_TEMP_FILE% to %DOTNET_TARGET_DIR%
"%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%DOTNET_TEMP_FILE%', '.');"
IF ERRORLEVEL 1 GOTO FAIL

DEL /F "..\%DOTNET_TEMP_FILE%"
IF ERRORLEVEL 1 GOTO FAIL

ECHO %DOTNET_URL%>"%DOTNET_TARGET_DIR%.flag"
IF ERRORLEVEL 1 GOTO FAIL

:CONTINUE_WITH_DOT_NET

IF NOT EXIST "%DOTNET_TARGET_DIR%\dotnet.exe" (
  ECHO Unable to find dotnet.exe under %DOTNET_TARGET_DIR%
  GOTO FAIL
)

CALL "%DOTNET_TARGET_DIR%\dotnet.exe" %*
EXIT /B %ERRORLEVEL%
ENDLOCAL

:FAIL
ECHO "FAIL"
EXIT /B 1