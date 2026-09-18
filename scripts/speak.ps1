param([Parameter(Mandatory=$true)][string]$TextPath,[Parameter(Mandatory=$true)][string]$OutputPath,[string]$Voice="")
$ErrorActionPreference="Stop"
Add-Type -AssemblyName System.Speech
$synth=New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
 if($Voice) { $synth.SelectVoice($Voice) }
 $synth.SetOutputToWaveFile($OutputPath)
 $synth.Speak([IO.File]::ReadAllText($TextPath))
} finally { $synth.Dispose() }
