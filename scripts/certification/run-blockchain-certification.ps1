param(
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$ComposeFile = "docker-compose.blockchain-certification.yml"

function Write-Status {
    param([string]$Name, [string]$State)
    Write-Host "$Name`: $State"
}

function Invoke-Native {
    param(
        [string]$Command,
        [string[]]$Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE"
    }
}

function Get-CertificationValue {
    param(
        [string]$Name,
        [string]$Default
    )

    $fromEnvironment = [Environment]::GetEnvironmentVariable($Name)
    if ($fromEnvironment) {
        return $fromEnvironment
    }

    $envFile = Join-Path $Root ".env"
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            $trimmed = $line.Trim()
            if (-not $trimmed -or $trimmed.StartsWith("#")) {
                continue
            }

            $separator = $trimmed.IndexOf("=")
            if ($separator -le 0) {
                continue
            }

            $key = $trimmed.Substring(0, $separator).Trim()
            if ($key -ne $Name) {
                continue
            }

            $value = $trimmed.Substring($separator + 1).Trim()
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            return $value
        }
    }

    return $Default
}

try {
    Set-Location $Root

    Write-Status "COMPOSE CONFIG" "RUNNING"
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "config", "--quiet")
    Write-Status "COMPOSE CONFIG" "PASS"

    Write-Status "STACK START" "RUNNING"
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "up", "-d", "--build")
    Write-Status "STACK START" "PASS"

    Write-Status "STACK PS" "RUNNING"
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "ps")

    $btcUser = Get-CertificationValue "CERT_BTC_RPC_USER" "helium"
    $btcPassword = Get-CertificationValue "CERT_BTC_RPC_PASSWORD" "helium"

    Write-Status "BTC RPC" "RUNNING"
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "exec", "-T", "bitcoin", "bitcoin-cli", "-regtest", "-rpcuser=$btcUser", "-rpcpassword=$btcPassword", "getblockchaininfo")
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "exec", "-T", "bitcoin", "bitcoin-cli", "-regtest", "-rpcuser=$btcUser", "-rpcpassword=$btcPassword", "getblockcount")
    Write-Status "BTC RPC" "PASS"

    Write-Status "ETH RPC" "RUNNING"
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "exec", "-T", "anvil", "cast", "chain-id", "--rpc-url", "http://127.0.0.1:8545")
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "exec", "-T", "anvil", "cast", "block-number", "--rpc-url", "http://127.0.0.1:8545")
    Write-Status "ETH RPC" "PASS"

    Write-Status "SOL RPC" "RUNNING"
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "exec", "-T", "solana", "solana", "-u", "http://127.0.0.1:8899", "cluster-version")
    Invoke-Native "docker" @("compose", "-f", $ComposeFile, "exec", "-T", "solana", "solana", "-u", "http://127.0.0.1:8899", "slot")
    Write-Status "SOL RPC" "PASS"

    foreach ($category in @(
        "BTC DEPOSIT",
        "BTC WITHDRAWAL",
        "ETH DEPOSIT",
        "ETH WITHDRAWAL",
        "SOL DEPOSIT",
        "SOL WITHDRAWAL",
        "RESTART RECOVERY",
        "PROVIDER DISAGREEMENT",
        "REORG HANDLING"
    )) {
        Write-Status $category "RUNNING"
    }

    Invoke-Native ".\backend\helium-core\gradlew.bat" @(
        "-p", "backend\helium-core",
        ":app:test",
        "--tests", "*BitcoinRegtestLifecycleIntegrationTest",
        "--tests", "*EthereumAnvilLifecycleIntegrationTest",
        "--tests", "*SolanaValidatorLifecycleIntegrationTest",
        "--tests", "*BlockchainRestartRecoveryIntegrationTest",
        "--tests", "*BlockchainProviderDisagreementIntegrationTest",
        "--tests", "*CanonicalChainReorgIntegrationTest"
    )

    foreach ($category in @(
        "BTC DEPOSIT",
        "BTC WITHDRAWAL",
        "ETH DEPOSIT",
        "ETH WITHDRAWAL",
        "SOL DEPOSIT",
        "SOL WITHDRAWAL",
        "RESTART RECOVERY",
        "PROVIDER DISAGREEMENT",
        "REORG HANDLING"
    )) {
        Write-Status $category "PASS"
    }

    Write-Status "BLOCKCHAIN CERTIFICATION" "PASSED"
} finally {
    if (-not $KeepRunning) {
        docker compose -f $ComposeFile down --remove-orphans | Out-Null
    }
}
