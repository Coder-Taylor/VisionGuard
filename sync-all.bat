@echo off
chcp 65001 >nul
title VisionGuard 三端同步脚本

echo ════════════════════════════════════════════
echo   VisionGuard — Gitee + GitHub 双端同步
echo ════════════════════════════════════════════
echo.
echo 当前时间: %date% %time%
echo.

:: 项目根目录
set REPO_DIR=D:\Document\学习\大学\竞赛信息\计算机设计大赛\vision-hub
cd /d "%REPO_DIR%" || exit /b 1

:: 检查是否有未提交的改动
git status --porcelain >nul 2>&1
if errorlevel 1 (
    echo [错误] Git 仓库异常，请检查
    pause
    exit /b 1
)

git status --porcelain | findstr . >nul 2>&1
if not errorlevel 1 (
    echo [提示] 有未提交的改动：
    git status --short
    echo.
    set /p COMMIT_MSG=请输入 commit 信息（留空则跳过）:
    if not "!COMMIT_MSG!"=="" (
        git add -A -- ':!submission/android/app/build' ':!submission/android/.gradle'
        git commit -m "!COMMIT_MSG!"
        if errorlevel 1 (
            echo [错误] Commit 失败
            pause
            exit /b 1
        )
    )
)

echo.
echo [1/2] 推送到 Gitee...
git push gitee master
if %errorlevel% neq 0 (
    echo [警告] Gitee 推送失败！可能是网络问题或仓库超过 1GB 限制
    echo 尝试强制推送（请确认没有其他人改过远程仓库）...
    choice /c YN /m "强制推送？(Y/N)"
    if !errorlevel!==1 (
        git push gitee master --force
    )
)

echo.
echo [2/2] 推送到 GitHub...
git push github master
if %errorlevel% neq 0 (
    echo [警告] GitHub 推送失败！
    choice /c YN /m "强制推送？(Y/N)"
    if !errorlevel!==1 (
        git push github master --force
    )
)

echo.
echo ════════════════════════════════════════════
echo   同步完成！
echo.
echo   Gitee:  https://gitee.com/taylorchengitee/vision-guard
echo   GitHub: https://github.com/Coder-Taylor/VisionGuard
echo ════════════════════════════════════════════
pause
