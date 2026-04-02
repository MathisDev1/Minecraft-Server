@echo off
REM Build and Deploy Script (Automated)
SET MAVEN_CMD="%~dp0.maven\apache-maven-3.9.6\bin\mvn.cmd"

echo [1/3] Baue Plugins (CityBuild Core ^& Economy)...

echo -- Baue Core... (mvn install)
call %MAVEN_CMD% clean install -DskipTests
if %ERRORLEVEL% NEQ 0 goto :BUILD_FAILED

echo -- Baue Economy... (mvn package)
call %MAVEN_CMD% -f CityBuildEconomy\pom.xml clean package -DskipTests
if %ERRORLEVEL% NEQ 0 goto :BUILD_FAILED

echo [2/3] Kopiere JARs in den LocalServer/plugins Ordner...
copy target\citybuild-core-1.0-SNAPSHOT.jar ..\LocalServer\plugins\
copy CityBuildEconomy\target\citybuild-economy-1.0-SNAPSHOT.jar ..\LocalServer\plugins\

echo [3/3] Fertig! Du kannst nun den Server mit LocalServer\start.bat starten.
echo.
pause
exit /b 0

:BUILD_FAILED
    echo [FEHLER] Maven Build ist fehlgeschlagen.
    pause
    exit /b %ERRORLEVEL%
