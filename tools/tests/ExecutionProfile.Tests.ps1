BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '../ExecutionProfile.psm1') -Force
    function New-CapableHost {
        @{ logicalProcessors=8; totalMemoryGiB=48; availableMemoryGiB=22; freeDiskGiB=50;
            javaReady=$true; sdkReady=$true; emulatorReady=$true; accelerationReady=$true; cpuLoadPercent=20 }
    }
}
Describe 'Resource-aware work placement' {
    It 'uses local resources with a bounded worker and emulator budget on a capable host' {
        $decision = Get-ExecutionDecision (New-CapableHost)
        $decision.mode | Should -Be Local
        $decision.maxWorkers | Should -Be 4
        $decision.maxGradleProcesses | Should -Be 1
        $decision.maxEmulators | Should -Be 1
    }
    It 'routes only device work to cloud when acceleration or the image is unavailable' {
        foreach ($missing in @('accelerationReady','emulatorReady')) {
            $facts = New-CapableHost; $facts[$missing]=$false
            $decision = Get-ExecutionDecision $facts
            $decision.mode | Should -Be Hybrid
            $decision.buildLocation | Should -Be Local
            $decision.deviceLocation | Should -Be Cloud
        }
    }
    It 'routes heavy work to cloud for inadequate or unknown resources and missing toolchains' {
        foreach ($field in @('logicalProcessors','totalMemoryGiB','availableMemoryGiB','freeDiskGiB','javaReady','sdkReady')) {
            $facts=New-CapableHost; $facts[$field]=0
            (Get-ExecutionDecision $facts).mode | Should -Be Cloud
        }
        foreach ($unknown in @($null,[double]::NaN,[double]::PositiveInfinity)) {
            $facts=New-CapableHost; $facts.availableMemoryGiB=$unknown
            (Get-ExecutionDecision $facts).mode | Should -Be Cloud
        }
    }
    It 'supports light local builds on a constrained machine without starting an emulator' {
        $facts=New-CapableHost; $facts.logicalProcessors=4; $facts.totalMemoryGiB=8; $facts.availableMemoryGiB=4
        $decision=Get-ExecutionDecision $facts
        $decision.mode | Should -Be Hybrid
        $decision.maxWorkers | Should -Be 2
        $decision.maxEmulators | Should -Be 0
    }
    It 'does not label an otherwise capable machine permanently weak from one busy CPU sample' {
        $facts=New-CapableHost; $facts.cpuLoadPercent=98
        $decision=Get-ExecutionDecision $facts
        $decision.mode | Should -Be Local
        $decision.reasons -join ' ' | Should -Match 'busy; wait/recheck'
    }
    It 'caps available resources to container memory rather than host RAM' {
        $memory=Limit-ExecutionResources 64 40 4 3
        $memory.totalMemoryGiB | Should -Be 4
        $memory.availableMemoryGiB | Should -Be 1
        (Limit-ExecutionResources 64 40 4 8).availableMemoryGiB | Should -Be 0
        (Limit-ExecutionResources 64 40 4 $null).availableMemoryGiB | Should -Be 0
    }
    It 'retains native limits when cgroup is unlimited or larger than the host' {
        (Limit-ExecutionResources 32 10 $null $null).availableMemoryGiB | Should -Be 10
        (Limit-ExecutionResources 32 10 100 5).totalMemoryGiB | Should -Be 32
    }
    It 'uses the actual minor compile SDK and rejects an unknown declaration' {
        Get-ProjectAndroidPlatform 'compileSdk { version = release(36) { minorApiLevel = 1 } }' | Should -Be 'android-36.1'
        Get-ProjectAndroidPlatform 'compileSdk = 36' | Should -Be 'android-36'
        { Get-ProjectAndroidPlatform 'compileSdk = libs.versions.androidSdk.get()' } | Should -Throw
    }
}
