# Entwürfe für Issue-Antworten (nicht gepostet)

## #17 Y2-Fork mit KI-geschriebenem Code
Hi Jarrod, thanks for asking first, I appreciate it!

Yes, a Y2 fork is fine with me. A few things I'd ask:
- Keep the license. InniClassic is under the same custom non-commercial license as upstream (JJ Launcher), see LICENSE. Your fork has to stay free for personal/educational use.
- Credit upstream (ismileblue / Jaejoong Lee's JJ Launcher) and InniClassic in your README.
- Large parts of this project were written with AI assistance (Claude). I review and test on the real device, but please do the same on yours, and don't assume the code is bug-free.

Good luck with the Y2 port!

## #6, #8, #16 schließen
Thanks for the report and for your patience. This is fixed/merged in the latest release, so I'm closing it. If it still happens on your device, please reopen with your Y1 firmware version and what you did right before.

(Vor dem Posten: je Issue prüfen, ob es wirklich in einem Release gelöst ist.)

## #9 / #19 Einstellungen und Theme werden zurückgesetzt
Thanks for reporting this. I think settings may not reach flash when the device is shut down right after a change. I added a filesystem sync before "Power Off" in the menu; it will be in the next release.

To narrow it down, could you tell me:
1. Do you shut down via Settings → Power Off, or by holding the power button / letting the battery run out?
2. Does the setting survive a plain app restart, or only break after a full power cycle?
3. Which firmware are you on?

@pihh: thanks for looking into it. I don't merge attached source files from issues. If you have a fix, please open a pull request so I can review the diff.

## #20 / #21 Feature-Wünsche
Thanks for the ideas. Shutdown in the main menu / by key combo and stronger weak/normal haptics are noted. I can't promise a date, I'll look at them after the current round of UI work.

## #10 / #13 (nur Hinweis an dich)
Der PR verlinkt #10 nur. Im PR-Text "Fixes #10" ergänzen, sonst schließt sich das Issue beim Merge nicht.
