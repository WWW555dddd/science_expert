[CmdletBinding()]
param(
    [ValidateSet("help", "services", "env", "build", "run", "status", "stop-services")]
    [string]$Action = "help"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$backendRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$expertEnvFile = Join-Path $backendRoot ".env.expert.local"
$composeFile = Join-Path $backendRoot "docker-compose.yml"
$jarFile = Join-Path $backendRoot "ruoyi-admin\target\ruoyi-admin.jar"

function Import-DotEnv {
    param(
        [string]$Path,
        [switch]$Required
    )

    if (!(Test-Path -LiteralPath $Path))
    {
        if ($Required)
        {
            throw "缺少配置文件：$Path，请先根据同名 .example 文件创建本地配置。"
        }
        return
    }
    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (!$line -or $line.StartsWith("#") -or !$line.Contains("="))
        {
            return
        }
        $parts = $line.Split("=", 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Initialize-DockerEnvironment {
    if (!(Get-Command docker -ErrorAction SilentlyContinue))
    {
        throw "未找到 Docker，请先安装并启动 Docker Desktop。"
    }
    if ([string]::IsNullOrEmpty($env:DOCKER_CONFIG))
    {
        $dockerConfigDir = Join-Path $env:TEMP "science-expert-docker-cli"
        New-Item -ItemType Directory -Path $dockerConfigDir -Force | Out-Null
        $env:DOCKER_CONFIG = $dockerConfigDir
    }
    & docker info --format "{{.ServerVersion}}" *> $null
    if ($LASTEXITCODE -ne 0)
    {
        throw "Docker Desktop 尚未就绪。"
    }
}

function Start-Services {
    & docker compose --env-file $expertEnvFile -f $composeFile up -d --remove-orphans
    if ($LASTEXITCODE -ne 0)
    {
        throw "基础服务启动失败。"
    }
    for ($index = 0; $index -lt 60; $index++)
    {
        $health = & docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" science-expert-neo4j 2>$null
        if ($health -eq "healthy")
        {
            break
        }
        Start-Sleep -Seconds 2
    }
    if ($health -ne "healthy")
    {
        throw "Neo4j 健康检查未通过。"
    }
    $user = [Environment]::GetEnvironmentVariable("NEO4J_USER", "Process")
    $password = [Environment]::GetEnvironmentVariable("NEO4J_PASSWORD", "Process")
    $queries = @(
        "CREATE CONSTRAINT kg_node_entity_id IF NOT EXISTS FOR (n:KGNode) REQUIRE n.entityId IS UNIQUE",
        "CREATE INDEX kg_node_external_key IF NOT EXISTS FOR (n:KGNode) ON (n.externalKey)",
        "CREATE INDEX kg_node_type IF NOT EXISTS FOR (n:KGNode) ON (n.entityType)"
    )
    foreach ($query in $queries)
    {
        & docker exec science-expert-neo4j cypher-shell -u $user -p $password $query *> $null
        if ($LASTEXITCODE -ne 0)
        {
            throw "Neo4j 初始化语句执行失败。"
        }
    }
    $httpPort = [Environment]::GetEnvironmentVariable("NEO4J_HTTP_PORT", "Process")
    if (!$httpPort) { $httpPort = "7474" }
    Write-Host "Neo4j: http://127.0.0.1:$httpPort" -ForegroundColor Green
    Write-Host "MinIO: http://127.0.0.1:9000" -ForegroundColor Green
}

function Import-ExpertEnvironment {
    param(
        [switch]$Required,
        [switch]$Quiet
    )

    Import-DotEnv -Path $expertEnvFile -Required:$Required
    if (!$Quiet)
    {
        Write-Host "本地环境变量已加载。" -ForegroundColor Green
    }
}

function Get-MavenCommand {
    if ($env:MAVEN_CMD -and (Test-Path -LiteralPath $env:MAVEN_CMD))
    {
        return $env:MAVEN_CMD
    }
    foreach ($name in @("mvn.cmd", "mvn"))
    {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command)
        {
            return $command.Source
        }
    }
    foreach ($mavenHome in @($env:MAVEN_HOME, $env:M2_HOME))
    {
        if (!$mavenHome)
        {
            continue
        }
        $candidate = Join-Path $mavenHome "bin\mvn.cmd"
        if (Test-Path -LiteralPath $candidate)
        {
            return $candidate
        }
    }
    $wrapperRoot = Join-Path $env:USERPROFILE ".m2\wrapper\dists"
    if (Test-Path -LiteralPath $wrapperRoot)
    {
        $candidate = Get-ChildItem -LiteralPath $wrapperRoot -Recurse -Filter mvn.cmd -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($candidate)
        {
            return $candidate.FullName
        }
    }
    throw "未找到 Maven。可设置环境变量 MAVEN_CMD 指向 mvn.cmd。"
}

function Build-Backend {
    $maven = Get-MavenCommand
    & $maven -pl ruoyi-admin -am package -DskipTests
    if ($LASTEXITCODE -ne 0)
    {
        throw "后端构建失败。"
    }
}

function Run-Backend {
    Import-ExpertEnvironment -Required
    $backendPort = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { "8080" }
    $listener = netstat -ano | Select-String -Pattern "LISTENING" |
        Where-Object { $_.Line -match ":$backendPort\s" }
    if ($listener)
    {
        throw "$backendPort 已被占用，请先停止现有后端进程。"
    }
    if (!(Test-Path -LiteralPath $jarFile))
    {
        Build-Backend
    }
    Push-Location $backendRoot
    try
    {
        & java.exe -jar $jarFile
    }
    finally
    {
        Pop-Location
    }
}

function Show-Status {
    Import-ExpertEnvironment -Quiet
    $ports = @(
        $env:SERVER_PORT,
        $env:DB_PORT,
        $env:REDIS_PORT,
        $env:NEO4J_HTTP_PORT,
        "9000",
        "9001"
    ) | Where-Object { $_ }
    $portPattern = ($ports | Sort-Object -Unique | ForEach-Object { ":{0}\s" -f [regex]::Escape($_) }) -join "|"
    $listeners = netstat -ano | Select-String -Pattern "LISTENING" |
        Where-Object { $_.Line -match $portPattern }
    if ($listeners)
    {
        $listeners | ForEach-Object { $_.Line }
    }
    else
    {
        Write-Host "未发现后端依赖端口正在监听。"
    }
    if (Get-Command docker -ErrorAction SilentlyContinue)
    {
        & docker compose --env-file $expertEnvFile -f $composeFile ps
    }
}

switch ($Action)
{
    "services"
    {
        Initialize-DockerEnvironment
        Import-ExpertEnvironment -Required
        Start-Services
    }
    "env" { Import-ExpertEnvironment }
    "build" { Build-Backend }
    "run" { Run-Backend }
    "status" { Show-Status }
    "stop-services"
    {
        Initialize-DockerEnvironment
        Import-ExpertEnvironment -Required
        & docker compose --env-file $expertEnvFile -f $composeFile stop
    }
    default
    {
        Write-Host "用法："
        Write-Host "  .\bin\dev.ps1 services       启动 Redis、Neo4j、MinIO"
        Write-Host "  .\bin\dev.ps1 status         查看本机服务状态"
        Write-Host "  .\bin\dev.ps1 env            加载本地环境变量"
        Write-Host "  .\bin\dev.ps1 build          构建后端"
        Write-Host "  .\bin\dev.ps1 run            前台启动后端"
        Write-Host "  .\bin\dev.ps1 stop-services  停止 Docker 服务"
    }
}
