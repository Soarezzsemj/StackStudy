@echo off
rem Atalho: executa o script sem precisar mexer na politica de execucao do PowerShell.
rem Uso:  dev            (sobe tudo)
rem       dev -Stop      (para tudo)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dev.ps1" %*
