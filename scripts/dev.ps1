<#
  StackStudy: sobe o ambiente de desenvolvimento com um comando.

  Uso (na raiz do projeto):
    .\dev            sobe banco (Docker), API e front, espera ficar pronto e abre o navegador
    .\dev -Stop      para API, front e o banco (os dados do banco sao mantidos)
    .\dev -NoBrowser nao abre o navegador
    .\dev -AllowRemoteDb  permite rodar mesmo com .env apontando para o Neon (NAO recomendado)
#>
param(
  [switch]$Stop,
  [switch]$NoBrowser,
  [switch]$AllowRemoteDb
)

# ---- Ajuste aqui se mudar as portas ---------------------------------------
$DbPort       = 5432
$BackendPort  = 8080
$FrontendUrl  = 'http://localhost:5173'
# ---------------------------------------------------------------------------

$Root        = Split-Path -Parent $PSScriptRoot
$BackendDir  = Join-Path $Root 'backend'
$FrontendDir = Join-Path $Root 'frontend'
$EnvFile     = Join-Path $Root '.env'
$PidFile     = Join-Path $env:TEMP 'stackstudy-dev-pids.txt'
$HealthUrl   = "http://localhost:$BackendPort/actuator/health/liveness"

function Step($msg)  { Write-Host "==> $msg" -ForegroundColor Cyan }
function Ok($msg)    { Write-Host "    $msg" -ForegroundColor Green }
function Warn($msg)  { Write-Host "    $msg" -ForegroundColor Yellow }
function Fail($msg)  { Write-Host "ERRO: $msg" -ForegroundColor Red; exit 1 }

function Test-Port([int]$Port) {
  try {
    $c = New-Object Net.Sockets.TcpClient
    $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
    $done = $iar.AsyncWaitHandle.WaitOne(500)
    if ($done) { $c.EndConnect($iar) }
    $c.Close()
    return $done
  } catch { return $false }
}

function Wait-Until([scriptblock]$Condition, [int]$TimeoutSec) {
  $limit = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $limit) {
    if (& $Condition) { return $true }
    Start-Sleep -Seconds 2
  }
  return $false
}

# ---------------------------------------------------------------- PARAR ----
if ($Stop) {
  Step 'Parando API e front'
  if (Test-Path $PidFile) {
    Get-Content $PidFile | ForEach-Object {
      if ($_ -match '^\d+$') { & taskkill /PID $_ /T /F *> $null }
    }
    Remove-Item $PidFile -Force
    Ok 'Processos encerrados.'
  } else {
    Warn 'Nenhum processo registrado (talvez ja esteja parado).'
  }
  Step 'Parando o banco (os dados sao mantidos)'
  Push-Location $Root
  docker compose stop
  Pop-Location
  exit 0
}

# --------------------------------------------------------- PRE-REQUISITOS ----
Step 'Verificando ferramentas'
foreach ($tool in 'docker', 'node', 'npm', 'java') {
  if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
    Fail "'$tool' nao encontrado. Instale (Docker Desktop, Node.js LTS, JDK 21) e abra um novo terminal."
  }
}
if (-not (Test-Path (Join-Path $BackendDir 'mvnw.cmd'))) { Fail "backend\mvnw.cmd nao encontrado. Voce esta na pasta certa do projeto?" }
if (-not (Test-Path (Join-Path $FrontendDir 'package.json'))) { Fail 'frontend\package.json nao encontrado.' }
Ok 'Ferramentas ok.'

# ------------------------------------------------------------------- .env ----
Step 'Carregando .env'
if (-not (Test-Path $EnvFile)) {
  Fail ".env nao existe. Rode:  Copy-Item .env.example .env   e preencha com valores de DESENVOLVIMENTO."
}
$raw = Get-Content $EnvFile -Raw
if (-not $AllowRemoteDb -and $raw -match 'neon\.tech') {
  Fail ".env aponta para o Neon (producao). Use o banco local do docker-compose. Para ignorar este aviso: .\dev -AllowRemoteDb"
}
if ($raw -match '(?m)^\s*SPRING_PROFILES_ACTIVE\s*=\s*prod\b') {
  Warn 'SPRING_PROFILES_ACTIVE=prod no .env. Em desenvolvimento use vazio ou dev.'
}
Get-Content $EnvFile | ForEach-Object {
  $line = $_.Trim()
  if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
    $i = $line.IndexOf('=')
    $k = $line.Substring(0, $i).Trim()
    $v = $line.Substring($i + 1).Trim().Trim('"').Trim("'")
    [Environment]::SetEnvironmentVariable($k, $v, 'Process')
  }
}
Ok 'Variaveis carregadas (as janelas da API e do front herdam estes valores).'

# ----------------------------------------------------------------- DOCKER ----
Step 'Verificando o Docker'
docker info *> $null
if ($LASTEXITCODE -ne 0) {
  $dd = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
  if (Test-Path $dd) {
    Warn 'Docker nao esta rodando. Abrindo o Docker Desktop (pode levar ate 2 minutos)...'
    Start-Process $dd
  } else {
    Fail 'Docker nao esta rodando e o Docker Desktop nao foi encontrado. Abra-o manualmente.'
  }
  $ready = Wait-Until { docker info *> $null; $LASTEXITCODE -eq 0 } 150
  if (-not $ready) { Fail 'O Docker nao ficou pronto a tempo. Abra o Docker Desktop e rode de novo.' }
}
Ok 'Docker pronto.'

Step 'Subindo o banco (docker compose)'
Push-Location $Root
docker compose up -d postgres
$composeCode = $LASTEXITCODE
Pop-Location
if ($composeCode -ne 0) { Fail 'docker compose up falhou.' }
if (-not (Wait-Until { Test-Port $DbPort } 90)) { Fail "Banco nao respondeu na porta $DbPort. Confira o docker-compose.yml e o .env." }
Ok "Banco no ar (porta $DbPort)."

# --------------------------------------------------------------- DEPENDENCIAS --
if (-not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
  Step 'Instalando dependencias do front (npm ci), so na primeira vez'
  Push-Location $FrontendDir
  npm ci
  $npmCode = $LASTEXITCODE
  Pop-Location
  if ($npmCode -ne 0) { Fail 'npm ci falhou.' }
}

# --------------------------------------------------------------- INICIAR ------
if ((Test-Port $BackendPort)) { Warn "A porta $BackendPort ja esta em uso (a API talvez ja esteja rodando)." }

Step 'Iniciando a API (janela separada)'
$beCmd = "`$Host.UI.RawUI.WindowTitle='StackStudy API'; .\mvnw.cmd spring-boot:run"
$be = Start-Process powershell -WorkingDirectory $BackendDir -PassThru -ArgumentList '-NoExit', '-Command', ('"' + $beCmd + '"')

Step 'Iniciando o front (janela separada)'
$feCmd = "`$Host.UI.RawUI.WindowTitle='StackStudy Web'; npm run dev"
$fe = Start-Process powershell -WorkingDirectory $FrontendDir -PassThru -ArgumentList '-NoExit', '-Command', ('"' + $feCmd + '"')

@($be.Id, $fe.Id) | Set-Content $PidFile

# ------------------------------------------------------------------ ESPERAR ---
Step 'Aguardando a API ficar pronta (a primeira vez demora mais)'
$apiUp = Wait-Until {
  try { (Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch { $false }
} 300
if ($apiUp) { Ok 'API respondendo.' } else { Warn "A API nao respondeu em 5 min. Veja a janela 'StackStudy API'." }

if (-not $NoBrowser) { Start-Process $FrontendUrl }

Write-Host ''
Write-Host 'Pronto.' -ForegroundColor Green
Write-Host "  Front: $FrontendUrl"
Write-Host "  API:   http://localhost:$BackendPort  (saude: $HealthUrl)"
Write-Host '  Para parar tudo:  .\dev -Stop'

