param(
    [string]$NetworkRoot = 'C:\Users\wmb\Desktop\mc-network-test',
    [string]$ProfileRoot = 'C:\Users\wmb\AppData\Roaming\ModrinthApp\profiles\Silvermons Modpack (1)',
    [switch]$ProfileOnly
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$NetworkRoot = [IO.Path]::GetFullPath($NetworkRoot).TrimEnd('\')
$ProfileRoot = [IO.Path]::GetFullPath($ProfileRoot).TrimEnd('\')
$backendRoot = Join-Path $NetworkRoot 'cobblemon'
$backupBase = if ($ProfileOnly) { Join-Path $ProfileRoot 'test-pack-backups' } else { Join-Path $NetworkRoot 'backups' }
$backupRoot = Join-Path $backupBase ('prisma-scenarios-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))

function Assert-Target([string]$target) {
    $full = [IO.Path]::GetFullPath($target)
    if (-not ($full.StartsWith($NetworkRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or
              $full.StartsWith($ProfileRoot + '\', [StringComparison]::OrdinalIgnoreCase))) {
        throw "Destination escaped the named test directories: $full"
    }
    if ($ProfileOnly -and -not $full.StartsWith($ProfileRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Profile-only installation escaped the profile: $full"
    }
    $ancestor = $full
    while ($ancestor) {
        if ((Test-Path -LiteralPath $ancestor) -and ((Get-Item -Force -LiteralPath $ancestor).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Linked destination: $ancestor"
        }
        $ancestor = Split-Path -Parent $ancestor
    }
    return $full
}

function Backup-File([string]$target) {
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { return }
    if ($target.StartsWith($NetworkRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        $relative = 'network\' + $target.Substring($NetworkRoot.Length + 1)
    } else {
        $relative = 'profile\' + $target.Substring($ProfileRoot.Length + 1)
    }
    $backupFile = Assert-Target (Join-Path $backupRoot $relative)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backupFile) | Out-Null
    Copy-Item -LiteralPath $target -Destination $backupFile
}

function Install-File([string]$source, [string]$target) {
    $target = Assert-Target $target
    if ((Test-Path -LiteralPath $target -PathType Leaf) -and
        ((Get-FileHash -LiteralPath $source).Hash -eq (Get-FileHash -LiteralPath $target).Hash)) { return }
    Backup-File $target
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -Force
    if ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $target).Hash) { throw "Copy verification failed: $target" }
}

function Write-Text([string]$target, [string]$value) {
    $target = Assert-Target $target
    if ((Test-Path -LiteralPath $target -PathType Leaf) -and [IO.File]::ReadAllText($target) -eq $value) { return }
    Backup-File $target
    [IO.File]::WriteAllText($target, $value, [Text.UTF8Encoding]::new($false))
}

$artifacts = Join-Path $repository 'build\prisma-tests'
$guide = Join-Path $repository 'examples\prisma-tests\README.md'
$buildProperties = Get-Content -LiteralPath (Join-Path $repository 'gradle.properties') -Raw | ConvertFrom-StringData
$modName = '{0}_{1}.jar' -f $buildProperties.archives_base_name, $buildProperties.mod_version.Replace('.', '-')
$mod = Join-Path $repository ('build\libs\' + $modName)
$copies = @(
    @((Join-Path $artifacts 'prisma-battle-music-test-commands.zip'), (Join-Path $ProfileRoot 'datapacks\prisma-battle-music-test-commands.zip')),
    @((Join-Path $artifacts 'prisma-battle-music-tests.zip'), (Join-Path $ProfileRoot 'resourcepacks\prisma-battle-music-tests.zip')),
    @($mod, (Join-Path $ProfileRoot ('mods\' + $modName)))
)
if (-not $ProfileOnly) { $copies += @(
    @((Join-Path $artifacts 'prisma-battle-music-test-commands.zip'), (Join-Path $backendRoot 'world\datapacks\prisma-battle-music-test-commands.zip')),
    @((Join-Path $artifacts 'cobblemon-prisma-server.zip'), (Join-Path $backendRoot 'resource-pack\cobblemon-prisma-server.zip')),
    @((Join-Path $artifacts 'cobblemon-prisma-server.zip'), (Join-Path $NetworkRoot 'client-test\resourcepacks\cobblemon-prisma-server.zip')),
    @((Join-Path $artifacts 'prisma-battle-music-tests.zip'), (Join-Path $NetworkRoot 'client-test\resourcepacks\prisma-battle-music-tests.zip')),
    @($guide, (Join-Path $backendRoot 'TEST_SCENARIOS.md')),
    @($guide, (Join-Path $NetworkRoot 'client-test\README.md')),
    @($mod, (Join-Path $backendRoot ('mods\' + $modName))),
    @($mod, (Join-Path $NetworkRoot ('client-test\mods\' + $modName)))
) }
foreach ($copy in $copies) {
    if (-not (Test-Path -LiteralPath $copy[0] -PathType Leaf)) { throw "Missing source: $($copy[0])" }
    Assert-Target $copy[1] | Out-Null
}
$requiredDirectories = @((Join-Path $ProfileRoot 'mods'))
if (-not $ProfileOnly) { $requiredDirectories += Join-Path $backendRoot 'world' }
foreach ($directory in $requiredDirectories) {
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) { throw "Expected existing test directory: $directory" }
}
if (-not (Test-Path -LiteralPath (Join-Path $ProfileRoot 'resourcepacks\Prisma Battle Tracks.zip') -PathType Leaf)) {
    throw 'The profile needs its original Prisma audio pack for the audio-free companion pack.'
}
if (-not $ProfileOnly) {
$properties = Join-Path $backendRoot 'server.properties'
$propertyText = [IO.File]::ReadAllText($properties)
if ($propertyText -notmatch '(?m)^resource-pack=.*cobblemon-prisma-server\.zip\s*$') {
    throw 'The backend does not offer the expected Prisma pack; refusing to update an unrelated pack hash.'
}
}
foreach ($copy in $copies) { Install-File $copy[0] $copy[1] }

$sourceGuide = ($guide -replace '\\', '/')
$profileGuide = @"
# Prisma music tests in this Modrinth profile

The updated client mod JAR is in mods/. The companion resource pack is resourcepacks/prisma-battle-music-tests.zip, and the command datapack is datapacks/prisma-battle-music-test-commands.zip.

## Resource packs

In Minecraft's Resource Packs menu, enable Prisma Battle Tracks.zip and prisma-battle-music-tests.zip, with the companion above Prisma. Keep your other texture packs as desired. The companion supplies 33 example rules; the original Prisma pack supplies the audio. Pack selections were left for the in-game menu so a running client cannot overwrite a file-based settings change.

If you connect to the existing cobblemon test backend, accept its updated self-contained server pack and use the server's installed command datapack. Disable the local companion when testing isolated server-pack variants.

## Single-player command datapack

Minecraft does not automatically load this profile's top-level datapacks/ folder. When creating a test world, open Data Packs > Open Pack Folder, copy datapacks/prisma-battle-music-test-commands.zip into that folder, enable it, and create the world with commands enabled. For an existing world, copy it to saves/WORLD/datapacks/ and reopen the world to register the sample NPC classes. For later command-only updates use /reload.

Run /function prisma_test:help in game for the 45-scenario menu. Commands spawn at your position; finish the current battle before spawning another. PvP checks require another player (for example, Open to LAN), and teammate checks need a compatible multi-battle setup.

See the [complete scenario guide](<$sourceGuide>) for expected tracks, low-HP transitions, shiny/appearance persistence, trainer classes, missing sounds, and isolated variants. Player identity examples need the actual opponent's username/UUID supplied to the builder.
"@
Write-Text (Join-Path $ProfileRoot 'PRISMA_TEST_SCENARIOS.md') ($profileGuide + "`r`n")
if ($ProfileOnly) {
    Write-Host 'Installed and hash-verified the profile command datapack, companion resource pack, and matching mod JAR.'
    Write-Host "Setup instructions: $(Join-Path $ProfileRoot 'PRISMA_TEST_SCENARIOS.md')"
    Write-Host "Backup location (created only for replaced files): $backupRoot"
    return
}
$sha1 = (Get-FileHash -LiteralPath (Join-Path $artifacts 'cobblemon-prisma-server.zip') -Algorithm SHA1).Hash.ToLowerInvariant()
if ($propertyText -match '(?m)^resource-pack-sha1=.*$') {
    $propertyText = [regex]::Replace($propertyText, '(?m)^resource-pack-sha1=.*$', 'resource-pack-sha1=' + $sha1)
} else {
    $propertyText += "`r`nresource-pack-sha1=$sha1`r`n"
}
Write-Text $properties $propertyText

$readmePath = Join-Path $backendRoot 'README.md'
$readme = [IO.File]::ReadAllText($readmePath)
$replacement = @'
## Music configuration

Music rules come exclusively from accepted/enabled resource packs. The latest music mod is installed in the Silvermons Modpack (1) client and copied to client-test. Its client-only replacement is also in the backend mods folder, where Fabric skips it; the older server-companion implementation has been replaced. Local server/client music JSON and /battle-music reload are obsolete.

The self-contained resource-pack/cobblemon-prisma-server.zip contains the original 28 Prisma OGG tracks, 18 sound aliases, and 33 example rules. The existing HTTP URL and required-pack setting remain in server.properties; its SHA-1 has been updated. The original supplied Prisma ZIP is unchanged.

## Test scenarios

Restart the cobblemon backend to register the new sample NPC classes, and restart the client to load the latest mod. Reconnect and accept the updated offered pack. Disable older local companion packs for these checks.

Run /function prisma_test:help for the 45-scenario menu: wild/species/form/category/shiny, trainer and player identities/scopes, intros, ally/enemy HP, appearances, persistence, fallbacks, and reloads. See [TEST_SCENARIOS.md](TEST_SCENARIOS.md) for prerequisites and expected tracks. PvP and teammate cases need additional players/a compatible battle setup. Later command-only changes use /reload; client resource reloads use F3+T.

Copies for another client are in ../client-test. Existing worlds, operator settings, and forwarding settings are preserved.

'@
$pattern = '(?s)## Music configuration\r?\n.*?(?=\r?\n## Velocity compatibility|\z)'
if ($readme -notmatch $pattern) { throw 'Could not locate the old music setup instructions' }
$readme = [regex]::Replace($readme, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement + "`r`n" })
Write-Text $readmePath $readme
Write-Host 'Installed updated datapack, resource packs, guides, and matching mod JAR; verified copied file hashes.'
Write-Host "Backup: $backupRoot"
Write-Host 'Restart the cobblemon backend and Minecraft client, reconnect/accept the updated server pack, then run /function prisma_test:help.'
