@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo        LAUNCHCONTROL - INICIAR
echo ==========================================
echo.

where docker >nul 2>&1
if errorlevel 1 (
  echo ERRO: Docker nao foi encontrado.
  echo Instale o Docker Desktop e abra-o antes de continuar.
  echo https://www.docker.com/products/docker-desktop/
  pause
  exit /b 1
)

docker info >nul 2>&1
if errorlevel 1 (
  echo ERRO: o Docker Desktop nao esta iniciado.
  echo Abra o Docker Desktop, aguarde ficar pronto e execute este arquivo novamente.
  pause
  exit /b 1
)

echo Iniciando banco MySQL, criando as tabelas e compilando a aplicacao...
echo Na primeira execucao pode demorar alguns minutos.
echo.
docker compose up --build
if errorlevel 1 (
  echo.
  echo O LaunchControl nao iniciou. Copie a mensagem de erro acima para diagnostico.
  pause
  exit /b 1
)
endlocal
